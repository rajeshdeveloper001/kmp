package com.vplayed.shared.home

import com.vplayed.shared.core.error.AppError
import com.vplayed.shared.core.error.DataResult
import com.vplayed.shared.core.error.HTTP_UNAUTHORIZED
import com.vplayed.shared.core.error.flatMap
import com.vplayed.shared.core.error.map
import com.vplayed.shared.core.network.ApiCaller
import com.vplayed.shared.core.network.requireResponse
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The Home endpoints. Every call returns a result and none throws (except cancellation). */
interface HomeRepository {
    /** Hero carousel content. */
    suspend fun getBanners(): DataResult<List<HomeBanner>>

    /** One page of rows: [SECTIONS_PER_PAGE] rows of up to [ITEMS_PER_SECTION] items. */
    suspend fun getSections(page: Int): DataResult<HomeSectionsPage>

    /** Further items of one row (decision D8: row paging through `home/all/{id}`). */
    suspend fun getSectionItems(sectionId: String, page: Int): DataResult<SectionItemsPage>

    /** Follows or unfollows a channel, identified by its slug. */
    suspend fun setFollowing(channelSlug: String, follow: Boolean): DataResult<Unit>

    companion object {
        const val SECTIONS_PER_PAGE = 3
        const val ITEMS_PER_SECTION = 10
    }
}

/**
 * [HomeRepository] against the VPlayed backend (AndroidJP `docs/api/home-api.md`). Decision D6:
 * a page is read when the HTTP status is 2xx and the envelope's `statusCode` is 200 (`status` is
 * advisory). Mapping runs on [mappingDispatcher], off the caller's (main) thread.
 */
internal class DefaultHomeRepository(
    private val api: ApiCaller,
    private val mapper: HomeMapper,
    private val mappingDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : HomeRepository {

    override suspend fun getBanners(): DataResult<List<HomeBanner>> =
        api.get(BANNERS_PATH).flatMap { it.requireResponse() }.mapOff { mapper.banners(it) }

    override suspend fun getSections(page: Int): DataResult<HomeSectionsPage> =
        api.get(
            HOME_PATH,
            query = listOf(
                "page" to page.toString(),
                "limit" to HomeRepository.SECTIONS_PER_PAGE.toString(),
                "inside_limit" to HomeRepository.ITEMS_PER_SECTION.toString(),
            ),
        ).flatMap { it.requireResponse() }.mapOff { mapper.sectionsPage(it) }

    override suspend fun getSectionItems(sectionId: String, page: Int): DataResult<SectionItemsPage> =
        api.get(
            SECTION_ITEMS_PATH + sectionId.encodeURLPathPart(),
            query = listOf("page" to page.toString(), "inside_limit" to HomeRepository.ITEMS_PER_SECTION.toString()),
        ).flatMap { it.requireResponse() }.mapOff { mapper.sectionItemsPage(it) }

    /** The UIKit app sends the new state as a string (`StudiosViewModel.followStudioAPI`) and accepts 200 and 201. */
    override suspend fun setFollowing(channelSlug: String, follow: Boolean): DataResult<Unit> {
        val body = buildJsonObject {
            put("studio", channelSlug)
            put("follow", if (follow) "1" else "0")
        }
        return api.postJson(FOLLOW_PATH, body).flatMap { response ->
            val status = if (response.isHttpSuccess) response.effectiveStatusCode else response.httpStatus
            when {
                status == HTTP_UNAUTHORIZED -> DataResult.Failure(AppError.Unauthorized)
                status !in FOLLOW_ACCEPTED -> DataResult.Failure(AppError.Server(status, response.message))
                response.envelope == null -> DataResult.Failure(AppError.Parsing)
                else -> DataResult.Success(Unit)
            }
        }
    }

    private suspend fun <T, R> DataResult<T>.mapOff(transform: (T) -> R): DataResult<R> =
        withContext(mappingDispatcher) { map(transform) }

    private companion object {
        const val BANNERS_PATH = "mediaview/api/v1/homebanners"
        const val HOME_PATH = "mediaview/api/v1/home"
        const val SECTION_ITEMS_PATH = "mediaview/api/v1/home/all/"
        const val FOLLOW_PATH = "studios/api/v1/follow_studio"
        val FOLLOW_ACCEPTED = setOf(200, 201)
    }
}
