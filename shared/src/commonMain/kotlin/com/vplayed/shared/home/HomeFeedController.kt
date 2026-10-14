package com.vplayed.shared.home

import com.vplayed.shared.core.error.AppError
import com.vplayed.shared.core.error.DataResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Loading further home rows (vertical paging). */
sealed interface PagingStatus {
    /** More pages may exist; the next one loads when the user nears the end. */
    data object Idle : PagingStatus

    data object Loading : PagingStatus

    data object EndReached : PagingStatus

    data class Failed(val error: AppError) : PagingStatus
}

data class HomeFeedState(
    /** First load in progress (or not started yet): the full-screen placeholder is shown. */
    val isLoading: Boolean = true,
    /** Pull-to-refresh in progress; the current content stays until it succeeds. */
    val isRefreshing: Boolean = false,
    val banners: List<HomeBanner> = emptyList(),
    val sections: List<HomeSection> = emptyList(),
    val paging: PagingStatus = PagingStatus.Idle,
    /** Next page of rows to request; null when every page is loaded. */
    val nextPage: Int? = 1,
    /** Rows currently fetching more items (row paging). */
    val loadingSectionIds: Set<String> = emptySet(),
    /** Channels whose follow request is in flight. */
    val updatingChannelIds: Set<String> = emptySet(),
    /** Set when nothing could be loaded at all: the full-screen error with Retry is shown. */
    val error: AppError? = null,
) {
    val isEmpty: Boolean
        get() = !isLoading && error == null && banners.isEmpty() && sections.isEmpty() && paging == PagingStatus.EndReached
}

/** One-off events for the screen to announce. */
sealed interface HomeFeedEffect {
    /** A refresh failed; the previous content is still shown. */
    data class RefreshFailed(val error: AppError) : HomeFeedEffect

    /** The user now follows ([isFollowed]) or no longer follows a channel. */
    data class FollowChanged(val channelId: String, val isFollowed: Boolean) : HomeFeedEffect

    /** A follow change failed; the channel card is unchanged. */
    data class FollowFailed(val channelId: String, val error: AppError) : HomeFeedEffect
}

/**
 * The Home feed, shared by AndroidJP (`HomeViewModel`) and VPlayed (`SharedHomeViewModel`):
 * - [load] fetches banners and the first page of rows together; a banner failure alone is not fatal,
 *   and when only the rows fail the banners show with a retry for the rows;
 * - further pages load as the user scrolls ([loadNextPage]); rows without items are dropped and a
 *   row already shown (same id) is ignored;
 * - decision D9: a page that adds no row would leave the paging trigger off screen, so up to
 *   [MAX_EMPTY_PAGES_IN_A_ROW] such pages are skipped automatically; a server that repeats or rewinds
 *   the page number ends the feed;
 * - decision D8: each row pages its own items ([loadMoreItems]);
 * - [refresh] keeps the content on failure; a reload discards answers of the requests it replaces;
 * - [setFollowing] follows or unfollows a channel once the server confirms it.
 *
 * State changes happen on [scope]'s dispatcher, which must be the main thread. AndroidJP passes
 * `viewModelScope`; VPlayed gets a controller from `SharedGraph.homeFeedController()`, which owns
 * its scope, and calls [close] when the model goes away.
 */
class HomeFeedController(
    private val repository: HomeRepository,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(HomeFeedState())
    val state: StateFlow<HomeFeedState> = mutableState.asStateFlow()

    // Effects are optional for a screen (AndroidJP shows none yet): when nobody collects, the oldest are dropped.
    private val effectChannel = Channel<HomeFeedEffect>(EFFECT_BUFFER, BufferOverflow.DROP_OLDEST)
    val effects: Flow<HomeFeedEffect> = effectChannel.receiveAsFlow()

    private var hasStarted = false
    private var firstLoadJob: Job? = null
    private var pagingJob: Job? = null
    private val rowJobs = mutableMapOf<String, Job>()

    private var current: HomeFeedState
        get() = mutableState.value
        set(value) {
            mutableState.value = value
        }

    /** Starts the first load. Later calls do nothing; use [retry] or [refresh] to load again. */
    fun load() {
        if (hasStarted) return
        hasStarted = true
        reload(isRefresh = false)
    }

    /** Retry after a full-screen error. */
    fun retry() {
        hasStarted = true
        current = HomeFeedState()
        reload(isRefresh = false)
    }

    /**
     * Pull-to-refresh, or new content after a sign-in or a language change: reloads banners and
     * page 1, keeping the current content until it succeeds. Before anything is shown it restarts
     * the first load, so the content matches the current account and language. Ignored before [load].
     */
    fun refresh() {
        if (!hasStarted) return
        val state = current
        if (state.isLoading || state.error != null) {
            current = HomeFeedState()
            reload(isRefresh = false)
        } else {
            current = state.copy(isRefreshing = true)
            reload(isRefresh = true)
        }
    }

    /** Loads the next page of rows. Safe to call repeatedly: ignored while busy or at the end. */
    fun loadNextPage() {
        val state = current
        val page = state.nextPage ?: return
        if (state.isLoading || state.isRefreshing || state.paging == PagingStatus.Loading) return
        loadPages(from = page)
    }

    /** Loads the next items of one row. Ignored when the row is complete or already loading. */
    fun loadMoreItems(sectionId: String) {
        val state = current
        val section = state.sections.firstOrNull { it.id == sectionId } ?: return
        val page = section.nextItemsPage ?: return
        if (sectionId in state.loadingSectionIds) return
        current = state.copy(loadingSectionIds = state.loadingSectionIds + sectionId)
        // Started after it is recorded: on an immediate dispatcher it may finish inside `start()`.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val result = repository.getSectionItems(sectionId, page)
            val latest = current
            val sections = if (result is DataResult.Success) {
                latest.sections.map { row ->
                    if (row.id != sectionId) {
                        row
                    } else {
                        row.copy(items = row.items.appendDistinct(result.data.items) { it.id }, nextItemsPage = result.data.nextPage)
                    }
                }
            } else {
                // The row stays as it is; scrolling to its end again retries.
                latest.sections
            }
            current = latest.copy(sections = sections, loadingSectionIds = latest.loadingSectionIds - sectionId)
            rowJobs.remove(sectionId)
        }
        rowJobs[sectionId] = job
        job.start()
    }

    /**
     * Follows ([follow] true) or unfollows the channel [channelId] shown on Home. Like the UIKit app
     * it waits for the server: on success every card of that channel shows the new state and
     * follower count; on failure nothing changes. Ignored while a request for it is in flight.
     */
    fun setFollowing(channelId: String, follow: Boolean) {
        val state = current
        if (channelId in state.updatingChannelIds) return
        val slug = state.sections.firstNotNullOfOrNull { row ->
            row.items.firstOrNull { it.id == channelId && it.details is ItemDetails.Channel }?.slug
        } ?: return
        current = state.copy(updatingChannelIds = state.updatingChannelIds + channelId)
        scope.launch {
            val result = repository.setFollowing(slug, follow)
            val latest = current
            current = when (result) {
                is DataResult.Success -> latest.copy(
                    sections = latest.sections.map { row -> row.withFollowState(channelId, follow) },
                    updatingChannelIds = latest.updatingChannelIds - channelId,
                )
                is DataResult.Failure -> latest.copy(updatingChannelIds = latest.updatingChannelIds - channelId)
            }
            effectChannel.trySend(
                when (result) {
                    is DataResult.Success -> HomeFeedEffect.FollowChanged(channelId, follow)
                    is DataResult.Failure -> HomeFeedEffect.FollowFailed(channelId, result.error)
                },
            )
        }
    }

    /** Cancels the controller's work. Only for a controller that owns its scope (see the class comment). */
    fun close() = scope.cancel()

    private fun reload(isRefresh: Boolean) {
        // Answers of the requests this load replaces are not wanted any more.
        firstLoadJob?.cancel()
        pagingJob?.cancel()
        rowJobs.values.forEach(Job::cancel)
        rowJobs.clear()
        current.let { state ->
            val paging = if (state.paging == PagingStatus.Loading) PagingStatus.Idle else state.paging
            current = state.copy(paging = paging, loadingSectionIds = emptySet())
        }
        firstLoadJob = scope.launch {
            val banners = async { repository.getBanners() }
            val firstPage = repository.getSections(page = 1)
            val bannerResult = banners.await()
            val loadedBanners = (bannerResult as? DataResult.Success)?.data
            val state = current
            when (firstPage) {
                is DataResult.Success -> {
                    val sections = emptyList<HomeSection>().appendDistinct(firstPage.data.sections) { it.id }
                    val nextPage = firstPage.data.nextPage?.takeIf { it > 1 }
                    current = HomeFeedState(
                        isLoading = false,
                        banners = loadedBanners ?: state.banners.takeIf { isRefresh }.orEmpty(),
                        sections = sections,
                        nextPage = nextPage,
                        paging = if (nextPage == null) PagingStatus.EndReached else PagingStatus.Idle,
                    )
                    // Nothing displayable on page 1: fetch on until there is (decision D9).
                    if (sections.isEmpty() && nextPage != null) loadPages(from = nextPage)
                }
                is DataResult.Failure -> {
                    current = when {
                        isRefresh -> state.copy(isRefreshing = false)
                        !loadedBanners.isNullOrEmpty() -> state.copy(
                            isLoading = false,
                            banners = loadedBanners,
                            paging = PagingStatus.Failed(firstPage.error),
                        )
                        else -> state.copy(isLoading = false, error = firstPage.error)
                    }
                    if (isRefresh) effectChannel.trySend(HomeFeedEffect.RefreshFailed(firstPage.error))
                }
            }
        }
    }

    private fun loadPages(from: Int) {
        current = current.copy(paging = PagingStatus.Loading)
        pagingJob = scope.launch {
            var page: Int? = from
            var emptyPagesInARow = 0
            while (true) {
                val requested = page ?: break
                when (val result = repository.getSections(requested)) {
                    is DataResult.Success -> {
                        val state = current
                        val sections = state.sections.appendDistinct(result.data.sections) { it.id }
                        val added = sections.size - state.sections.size
                        // A server that repeats or rewinds the page number would loop forever.
                        val nextPage = result.data.nextPage?.takeIf { it > requested }
                        current = state.copy(sections = sections, nextPage = nextPage)
                        emptyPagesInARow = if (added == 0) emptyPagesInARow + 1 else 0
                        page = nextPage.takeIf { added == 0 && emptyPagesInARow < MAX_EMPTY_PAGES_IN_A_ROW }
                    }
                    is DataResult.Failure -> {
                        current = current.copy(paging = PagingStatus.Failed(result.error))
                        return@launch
                    }
                }
            }
            current = current.copy(paging = if (current.nextPage == null) PagingStatus.EndReached else PagingStatus.Idle)
        }
    }

    companion object {
        /** Consecutive pages without a new row that are fetched without the user scrolling (decision D9). */
        const val MAX_EMPTY_PAGES_IN_A_ROW = 5

        private const val EFFECT_BUFFER = 8

        private fun <T> List<T>.appendDistinct(more: List<T>, id: (T) -> String): List<T> {
            val known = mapTo(HashSet(), id)
            return this + more.filter { known.add(id(it)) }
        }

        private fun HomeSection.withFollowState(channelId: String, follow: Boolean): HomeSection {
            if (items.none { it.id == channelId }) return this
            return copy(
                items = items.map { item ->
                    val channel = item.details as? ItemDetails.Channel
                    if (item.id != channelId || channel == null) {
                        item
                    } else {
                        val followers = ((channel.followers ?: 0) + if (follow) 1 else -1).coerceAtLeast(0)
                        item.copy(details = channel.copy(isFollowed = follow, followers = followers))
                    }
                },
            )
        }
    }
}
