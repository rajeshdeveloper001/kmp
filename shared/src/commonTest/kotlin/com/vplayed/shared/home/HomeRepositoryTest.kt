package com.vplayed.shared.home

import com.vplayed.shared.PlatformDependencies
import com.vplayed.shared.SharedGraph
import com.vplayed.shared.core.crypto.platformCryptoPrimitives
import com.vplayed.shared.core.error.AppError
import com.vplayed.shared.core.error.DataResult
import com.vplayed.shared.core.network.FakeDeviceInfo
import com.vplayed.shared.core.network.InMemoryRequestCredentials
import com.vplayed.shared.core.network.RecordingEngine
import com.vplayed.shared.core.network.testConfig
import com.vplayed.shared.core.platform.NoOpLogger
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Ported from AndroidJP's `DefaultHomeRepositoryTest`: the real client stack against synthetic answers. */
class HomeRepositoryTest {

    private val answers = ArrayDeque<Pair<Int, String>>()
    private val recorder = RecordingEngine {
        val (status, body) = answers.removeFirstOrNull() ?: throw IOException("offline")
        respond(body, HttpStatusCode.fromValue(status), headersOf(HttpHeaders.ContentType, "application/json"))
    }

    private val repository = SharedGraph(
        testConfig(),
        PlatformDependencies(
            deviceInfo = FakeDeviceInfo(),
            logger = NoOpLogger,
            engineFactory = { recorder.engine },
            cryptoPrimitives = platformCryptoPrimitives(),
            requestCredentials = InMemoryRequestCredentials(),
        ),
    ).homeRepository

    private fun answer(body: String, status: Int = 200) = answers.addLast(status to body)

    @Test
    fun givenFirstPage_whenLoadingSections_thenRequestsPagedEndpointAndMapsRows() = runTest {
        answer(HomeFixtures.HOME_PAGE_1)

        val result = repository.getSections(page = 1)

        val url = recorder.last.url
        assertEquals("/mediaview/api/v1/home", url.encodedPath)
        assertEquals("1", url.parameters["page"])
        assertEquals("3", url.parameters["limit"])
        assertEquals("10", url.parameters["inside_limit"])
        val page = assertIs<DataResult.Success<HomeSectionsPage>>(result).data
        assertEquals(listOf("sec-live", "sec-videos"), page.sections.map { it.id })
        assertEquals(2, page.nextPage)
        // The test config is Android's: AndroidJP's image table and its image host.
        assertEquals("https://images.test.invalid/thumbs/vid-1-tc.jpg", page.sections[1].items[0].imageUrl)
    }

    @Test
    fun givenLastPage_whenLoadingSections_thenNextPageIsNull() = runTest {
        answer(HomeFixtures.HOME_PAGE_3)
        assertEquals(null, assertIs<DataResult.Success<HomeSectionsPage>>(repository.getSections(page = 3)).data.nextPage)
    }

    @Test
    fun givenBannersWithGenreIds_whenLoading_thenParsesInsteadOfFailing() = runTest {
        answer(HomeFixtures.BANNERS)

        val result = repository.getBanners()

        assertEquals("/mediaview/api/v1/homebanners", recorder.last.url.encodedPath)
        assertEquals(2, assertIs<DataResult.Success<List<HomeBanner>>>(result).data.size)
    }

    @Test
    fun givenRowPaging_whenLoadingMoreItems_thenRequestsRowEndpoint() = runTest {
        answer(HomeFixtures.SECTION_ITEMS_PAGE_2)

        val result = repository.getSectionItems("sec-videos", page = 2)

        val url = recorder.last.url
        assertEquals("/mediaview/api/v1/home/all/sec-videos", url.encodedPath)
        assertEquals("2", url.parameters["page"])
        assertEquals("10", url.parameters["inside_limit"])
        assertEquals(3, assertIs<DataResult.Success<SectionItemsPage>>(result).data.nextPage)
    }

    @Test
    fun givenRowIdWithReservedCharacters_whenPaging_thenItIsOnePathSegment() = runTest {
        answer(HomeFixtures.SECTION_ITEMS_PAGE_2)
        repository.getSectionItems("a/b?c", page = 2)
        assertEquals("/mediaview/api/v1/home/all/a%2Fb%3Fc", recorder.last.url.encodedPath)
    }

    @Test
    fun givenServerError_whenLoading_thenReturnsServerError() = runTest {
        answer("{}", status = 500)
        val error = assertIs<DataResult.Failure>(repository.getSections(1)).error
        assertEquals(500, assertIs<AppError.Server>(error).code)
    }

    @Test
    fun givenUnauthorized_whenLoading_thenReturnsUnauthorized() = runTest {
        answer("{}", status = 401)
        assertEquals(DataResult.Failure(AppError.Unauthorized), repository.getSections(1))
    }

    @Test
    fun givenFailedEnvelope_whenLoading_thenReturnsServerErrorWithApiMessage() = runTest {
        answer(HomeFixtures.FAILED_ENVELOPE)
        assertEquals(DataResult.Failure(AppError.Server(422, "Invalid page")), repository.getSections(1))
    }

    @Test
    fun givenStatusCode200WithoutSuccessStatus_whenLoading_thenTheRowsAreRead() = runTest {
        // Decision D6: `statusCode` decides; `status` is advisory.
        answer("""{"statusCode":200,"status":"ok","response":{"data":[],"next":null}}""")
        assertIs<DataResult.Success<HomeSectionsPage>>(repository.getSections(1))
    }

    @Test
    fun givenMalformedJsonOrMissingResponse_whenLoading_thenReturnsParsingError() = runTest {
        answer("{ not json")
        assertEquals(DataResult.Failure(AppError.Parsing), repository.getSections(1))
        answer("""{"statusCode":200,"response":null}""")
        assertEquals(DataResult.Failure(AppError.Parsing), repository.getBanners())
    }

    @Test
    fun givenNoConnection_whenLoading_thenReturnsNetworkError() = runTest {
        assertEquals(DataResult.Failure(AppError.Network), repository.getSections(1))
    }

    @Test
    fun givenFollow_whenSent_thenPostsSlugAndStateAsStrings() = runTest {
        answer("""{"statusCode":201,"message":"Followed"}""")

        assertEquals(DataResult.Success(Unit), repository.setFollowing("studio-nine", follow = true))

        val request = recorder.last
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/studios/api/v1/follow_studio", request.url.encodedPath)
        val body = Json.parseToJsonElement((request.body as TextContent).text)
        assertEquals(Json.parseToJsonElement("""{"studio":"studio-nine","follow":"1"}"""), body)
    }

    @Test
    fun givenUnfollowRefused_whenSent_thenServerErrorWithMessage() = runTest {
        answer("""{"statusCode":422,"message":"Not followed"}""")
        assertEquals(DataResult.Failure(AppError.Server(422, "Not followed")), repository.setFollowing("studio-nine", follow = false))
        answer("", status = 200)
        assertEquals(DataResult.Failure(AppError.Parsing), repository.setFollowing("studio-nine", follow = false))
    }
}
