package com.vplayed.shared.movies

import com.vplayed.shared.PlatformDependencies
import com.vplayed.shared.SharedGraph
import com.vplayed.shared.core.config.ClientPlatform
import com.vplayed.shared.core.config.SharedConfig
import com.vplayed.shared.core.crypto.platformCryptoPrimitives
import com.vplayed.shared.core.error.AppError
import com.vplayed.shared.core.error.DataResult
import com.vplayed.shared.core.network.FakeDeviceInfo
import com.vplayed.shared.core.network.RecordingEngine
import com.vplayed.shared.core.network.respondJson
import com.vplayed.shared.core.network.testConfig
import com.vplayed.shared.core.platform.NoOpLogger
import com.vplayed.shared.home.ContentType
import com.vplayed.shared.home.ShowType
import com.vplayed.shared.session.testSessions
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class MoviesRepositoryTest {

    private val answers = ArrayDeque<Pair<HttpStatusCode, String>>()
    private val recorder = RecordingEngine { answers.removeFirst().let { (status, body) -> respondJson(body, status) } }

    /** VPlayed's graph (Apple image table): Movies is an iOS screen. */
    private val repository = SharedGraph(
        testConfig().let { SharedConfig(it.environment, it.serverUrl, it.imageBaseUrl, false, it.appVersion, ClientPlatform.IOS, it.transportKey) },
        PlatformDependencies(FakeDeviceInfo(), NoOpLogger, { recorder.engine }, platformCryptoPrimitives()),
        testSessions(),
    ).moviesRepository

    @Test
    fun givenPage_whenLoading_thenRequestsTheMovieCatalogueAndMapsItems() = runTest {
        answers += HttpStatusCode.OK to PAGE_1

        val page = assertIs<DataResult.Success<MoviesPage>>(repository.getPage(1)).data

        val sent = recorder.last
        assertEquals("/mediaview/api/v1/sho-details/media/1", sent.url.encodedPath)
        assertEquals("1", sent.url.parameters["page"])
        assertEquals("10", sent.url.parameters["limit"])
        assertEquals("true", sent.url.parameters["is_promo"])
        assertEquals(1997, page.total)
        assertEquals(2, page.nextPage)
        assertEquals(listOf("m-1", "m-2"), page.items.map { it.id }, "the repeated m-1 is dropped")
        val first = page.items.first()
        assertEquals("Movie One", first.title)
        assertEquals(ContentType.VIDEO, first.type)
        assertEquals(ShowType.fromCode(1), first.showType)
        assertEquals("Drama", first.genre)
        assertEquals("1h 30m", first.duration)
        assertEquals("https://images.test.invalid/posters/one-tp.jpg", first.portraitImageUrl)
    }

    @Test
    fun givenLastPageOrMissingTotal_whenLoading_thenNoNextPageAndNullTotal() = runTest {
        answers += HttpStatusCode.OK to """{"statusCode":200,"response":{"data":[],"next":0}}"""

        val page = assertIs<DataResult.Success<MoviesPage>>(repository.getPage(9)).data

        assertNull(page.nextPage)
        assertNull(page.total)
        assertEquals(emptyList(), page.items)
    }

    @Test
    fun givenFailures_whenLoading_thenTypedErrors() = runTest {
        answers += HttpStatusCode.OK to """{"statusCode":500,"message":"Down"}"""
        answers += HttpStatusCode.Unauthorized to ""
        answers += HttpStatusCode.OK to "{not json"

        assertEquals(DataResult.Failure(AppError.Server(500, "Down")), repository.getPage(1))
        assertEquals(DataResult.Failure(AppError.Unauthorized), repository.getPage(1))
        assertEquals(DataResult.Failure(AppError.Parsing), repository.getPage(1))
    }

    private companion object {
        /** Synthetic, in the shape of the dev API's `sho-details/media/1`. */
        const val PAGE_1 = """{"statusCode":200,"status":"success","response":{"total":1997,"currentPage":1,"next":2,"title":"Movies","data":[
            {"_id":"m-1","title":"Movie One","sho_type":1,"video_duration":5400,"default_genre":{"title":{"en":"Drama"}},
             "new_thumbnail_images":{"default":{"tp":"posters/one-tp.jpg","tl":"posters/one-tl.jpg"}}},
            {"_id":"m-2","title":"Movie Two","sho_type":1,"video_duration":600,
             "new_thumbnail_images":{"default":{"tp":"posters/two-tp.jpg"}}},
            {"_id":"m-1","title":"Movie One","sho_type":1}
        ]}}"""
    }
}
