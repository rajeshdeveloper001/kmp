package com.vplayed.shared.movies

import com.vplayed.shared.core.error.DataResult
import com.vplayed.shared.core.error.flatMap
import com.vplayed.shared.core.error.map
import com.vplayed.shared.core.network.ApiCaller
import com.vplayed.shared.core.network.requireResponse
import com.vplayed.shared.home.ContentItem
import com.vplayed.shared.home.HomeMapper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One page of the movie catalogue. */
data class MoviesPage(
    val items: List<ContentItem>,
    /** Titles in the whole catalogue, e.g. 1,997; null when the server does not say. */
    val total: Int?,
    /** The next page number, or null on the last page. */
    val nextPage: Int?,
)

/**
 * The movie catalogue (`GET mediaview/api/v1/sho-details/media/1`), used by VPlayed's Movies tab
 * (AndroidJP has no Movies screen yet). Items use the Home item rules (`HomeMapper.item`, portrait
 * cards); paging rules stay with the screen.
 */
interface MoviesRepository {
    suspend fun getPage(page: Int): DataResult<MoviesPage>

    companion object {
        const val PAGE_SIZE = 10
    }
}

internal class DefaultMoviesRepository(
    private val api: ApiCaller,
    private val mapper: HomeMapper,
    private val mappingDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : MoviesRepository {

    // Same query as the UIKit app's movie list (`is_promo=true`); D6 read rule.
    override suspend fun getPage(page: Int): DataResult<MoviesPage> =
        api.get(
            PATH,
            query = listOf("page" to page.toString(), "limit" to MoviesRepository.PAGE_SIZE.toString(), "is_promo" to "true"),
        ).flatMap { it.requireResponse() }.let { result ->
            withContext(mappingDispatcher) { result.map { mapper.moviesPage(it) } }
        }

    private companion object {
        /** `1` is the movie show type. */
        const val PATH = "mediaview/api/v1/sho-details/media/1"
    }
}
