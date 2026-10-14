package com.vplayed.shared.home

import com.vplayed.shared.core.error.AppError
import com.vplayed.shared.core.error.DataResult
import com.vplayed.shared.home.FakeHomeRepository.Companion.channel
import com.vplayed.shared.home.FakeHomeRepository.Companion.channelSection
import com.vplayed.shared.home.FakeHomeRepository.Companion.item
import com.vplayed.shared.home.FakeHomeRepository.Companion.page
import com.vplayed.shared.home.FakeHomeRepository.Companion.section
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ported from AndroidJP's `HomeViewModelTest` and VPlayed's `HomeViewModelTests`. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeFeedControllerTest {

    private val repository = FakeHomeRepository().apply {
        sectionPages[1] = page(section("a"), section("b", nextItemsPage = 2), next = 2)
        sectionPages[2] = page(section("c"), next = 3)
        sectionPages[3] = page(section("d"), next = null)
    }

    /** A controller on the test scheduler, after [HomeFeedController.load] ran to completion. */
    private fun TestScope.loaded(): HomeFeedController = HomeFeedController(repository, backgroundScope).also {
        it.load()
        runCurrent()
    }

    private val HomeFeedController.now get() = state.value

    private fun TestScope.act(action: () -> Unit) {
        action()
        runCurrent()
    }

    // region First load

    @Test
    fun givenApiSucceeds_whenOpened_thenShowsBannersAndFirstPageRows() = runTest {
        val controller = loaded()

        assertFalse(controller.now.isLoading)
        assertEquals(listOf("b1"), controller.now.banners.map { it.id })
        assertEquals(listOf("a", "b"), controller.now.sections.map { it.id })
        assertEquals(PagingStatus.Idle, controller.now.paging)
    }

    @Test
    fun givenNotStarted_whenCreated_thenNothingIsRequested() = runTest {
        val controller = HomeFeedController(repository, backgroundScope)
        runCurrent()

        assertTrue(controller.now.isLoading)
        assertTrue(repository.sectionCalls.isEmpty())
        controller.refresh()
        runCurrent()
        assertTrue(repository.sectionCalls.isEmpty(), "Refresh before load does nothing")
    }

    @Test
    fun givenLoadCalledTwice_whenOpened_thenLoadsOnce() = runTest {
        val controller = loaded()
        act { controller.load() }
        assertEquals(listOf(1), repository.sectionCalls)
    }

    @Test
    fun givenFirstPageInFlight_whenOpened_thenShowsLoadingState() = runTest {
        val hold = repository.holdSections(1)
        val controller = loaded()

        assertTrue(controller.now.isLoading)

        hold.complete(Unit)
        runCurrent()
        assertFalse(controller.now.isLoading)
    }

    @Test
    fun givenEverythingFails_whenOpened_thenShowsFullScreenErrorAndRetryRecovers() = runTest {
        repository.banners = DataResult.Failure(AppError.Network)
        repository.sectionPages[1] = DataResult.Failure(AppError.Network)
        val controller = loaded()

        assertEquals(AppError.Network, controller.now.error)
        assertFalse(controller.now.isEmpty)

        repository.banners = DataResult.Success(emptyList())
        repository.sectionPages[1] = page(section("a"), next = null)
        act { controller.retry() }

        assertNull(controller.now.error)
        assertEquals(listOf("a"), controller.now.sections.map { it.id })
    }

    @Test
    fun givenOnlyBannersFail_whenOpened_thenRowsStillShow() = runTest {
        repository.banners = DataResult.Failure(AppError.Server(500))
        val controller = loaded()

        assertNull(controller.now.error)
        assertTrue(controller.now.banners.isEmpty())
        assertEquals(2, controller.now.sections.size)
    }

    @Test
    fun givenRowsFailButBannersLoad_whenOpened_thenShowsBannersWithRetryForRows() = runTest {
        repository.sectionPages[1] = DataResult.Failure(AppError.Server(503))
        val controller = loaded()

        assertNull(controller.now.error)
        assertEquals(1, controller.now.banners.size)
        assertEquals(PagingStatus.Failed(AppError.Server(503)), controller.now.paging)

        repository.sectionPages[1] = page(section("a"), next = null)
        act { controller.loadNextPage() }
        assertEquals(listOf("a"), controller.now.sections.map { it.id })
    }

    @Test
    fun givenNoContentAtAll_whenOpened_thenShowsEmptyState() = runTest {
        repository.banners = DataResult.Success(emptyList())
        repository.sectionPages[1] = page(next = null)
        val controller = loaded()

        assertTrue(controller.now.isEmpty)
    }

    // endregion

    // region Paging

    @Test
    fun givenUserScrollsToEnd_whenLoadingNextPages_thenRowsAppendUntilEndReached() = runTest {
        val controller = loaded()

        act { controller.loadNextPage() }
        act { controller.loadNextPage() }

        assertEquals(listOf("a", "b", "c", "d"), controller.now.sections.map { it.id })
        assertEquals(PagingStatus.EndReached, controller.now.paging)
        assertNull(controller.now.nextPage)

        act { controller.loadNextPage() }
        assertEquals(listOf(1, 2, 3), repository.sectionCalls, "No request after the last page")
    }

    @Test
    fun givenPageAlreadyLoading_whenScrollTriggersAgain_thenOnlyOneRequestIsMade() = runTest {
        val controller = loaded()
        val hold = repository.holdSections(2)

        act { controller.loadNextPage() }
        act { controller.loadNextPage() }
        assertEquals(PagingStatus.Loading, controller.now.paging)

        hold.complete(Unit)
        runCurrent()
        assertEquals(listOf(1, 2), repository.sectionCalls)
    }

    @Test
    fun givenDuplicateRowFromServer_whenAppending_thenItIsShownOnce() = runTest {
        repository.sectionPages[2] = page(section("b"), section("c"), next = null)
        val controller = loaded()

        act { controller.loadNextPage() }

        assertEquals(listOf("a", "b", "c"), controller.now.sections.map { it.id })
    }

    @Test
    fun givenNextPageFails_whenUserTapsRetry_thenPageLoads() = runTest {
        repository.sectionPages[2] = DataResult.Failure(AppError.Network)
        val controller = loaded()

        act { controller.loadNextPage() }
        assertEquals(PagingStatus.Failed(AppError.Network), controller.now.paging)
        assertEquals(listOf("a", "b"), controller.now.sections.map { it.id })

        repository.sectionPages[2] = page(section("c"), next = null)
        act { controller.loadNextPage() }
        assertEquals(listOf("a", "b", "c"), controller.now.sections.map { it.id })
    }

    @Test
    fun givenPagesWithNothingNew_whenPaging_thenTheyAreSkippedUpToTheLimit() = runTest {
        // Decision D9: pages 2–7 add no row; the controller fetches on without the user scrolling,
        // but stops after five such pages in a row.
        (2..7).forEach { repository.sectionPages[it] = page(section("a"), next = it + 1) }
        repository.sectionPages[8] = page(section("z"), next = null)
        val controller = loaded()

        act { controller.loadNextPage() }

        assertEquals(listOf(1, 2, 3, 4, 5, 6), repository.sectionCalls)
        assertEquals(PagingStatus.Idle, controller.now.paging)
        assertEquals(7, controller.now.nextPage)

        act { controller.loadNextPage() }
        act { controller.loadNextPage() }
        assertEquals(listOf("a", "b", "z"), controller.now.sections.map { it.id })
    }

    @Test
    fun givenFirstPageWithNothingDisplayable_whenOpened_thenFollowingPagesLoadAtOnce() = runTest {
        repository.sectionPages[1] = page(next = 2)
        repository.sectionPages[2] = page(section("c"), next = null)
        val controller = loaded()

        assertEquals(listOf("c"), controller.now.sections.map { it.id })
        assertEquals(PagingStatus.EndReached, controller.now.paging)
    }

    @Test
    fun givenServerRepeatsAPageNumber_whenPaging_thenTheFeedEnds() = runTest {
        repository.sectionPages[2] = page(section("c"), next = 2)
        val controller = loaded()

        act { controller.loadNextPage() }
        act { controller.loadNextPage() }

        assertEquals(listOf(1, 2), repository.sectionCalls)
        assertEquals(PagingStatus.EndReached, controller.now.paging)
    }

    // endregion

    // region Row paging

    @Test
    fun givenRowWithMoreItems_whenUserScrollsRow_thenItemsAppendAndNextPageAdvances() = runTest {
        repository.itemPages["b" to 2] = DataResult.Success(SectionItemsPage(listOf(item("b-new"), item("b-0")), nextPage = null))
        val controller = loaded()

        act { controller.loadMoreItems("b") }

        val row = controller.now.sections.first { it.id == "b" }
        assertEquals(listOf("b-0", "b-1", "b-2", "b-new"), row.items.map { it.id })
        assertNull(row.nextItemsPage)
        assertTrue(controller.now.loadingSectionIds.isEmpty())

        act { controller.loadMoreItems("b") }
        assertEquals(listOf("b" to 2), repository.itemCalls, "Complete rows do not request again")
    }

    @Test
    fun givenRowWithoutMoreItems_whenScrolled_thenNoRequest() = runTest {
        val controller = loaded()
        act { controller.loadMoreItems("a") }
        assertTrue(repository.itemCalls.isEmpty())
    }

    @Test
    fun givenRowPageFails_whenScrolledAgain_thenItRetries() = runTest {
        val controller = loaded()
        act { controller.loadMoreItems("b") }
        assertEquals(3, controller.now.sections.first { it.id == "b" }.items.size)
        assertTrue(controller.now.loadingSectionIds.isEmpty())

        act { controller.loadMoreItems("b") }
        assertEquals(listOf("b" to 2, "b" to 2), repository.itemCalls)
    }

    // endregion

    // region Refresh

    @Test
    fun givenContentShown_whenRefreshFails_thenKeepsExistingContentAndAnnouncesIt() = runTest {
        val controller = loaded()
        repository.sectionPages[1] = DataResult.Failure(AppError.Network)

        act { controller.refresh() }

        assertFalse(controller.now.isRefreshing)
        assertEquals(listOf("a", "b"), controller.now.sections.map { it.id })
        assertEquals(HomeFeedEffect.RefreshFailed(AppError.Network), controller.effects.first())
    }

    @Test
    fun givenContentShown_whenRefreshSucceeds_thenReplacesRows() = runTest {
        val controller = loaded()
        act { controller.loadNextPage() }
        repository.sectionPages[1] = page(section("z"), next = null)

        act { controller.refresh() }

        assertEquals(listOf("z"), controller.now.sections.map { it.id })
        assertEquals(PagingStatus.EndReached, controller.now.paging)
    }

    @Test
    fun givenRefreshInFlight_whenBannersFail_thenPreviousBannersStay() = runTest {
        val controller = loaded()
        repository.banners = DataResult.Failure(AppError.Timeout)

        act { controller.refresh() }

        assertEquals(listOf("b1"), controller.now.banners.map { it.id })
    }

    @Test
    fun givenPageLoading_whenRefreshed_thenTheOldPageIsDiscarded() = runTest {
        val controller = loaded()
        val hold = repository.holdSections(2)
        act { controller.loadNextPage() }

        repository.sectionPages[1] = page(section("z"), next = 5)
        act { controller.refresh() }
        hold.complete(Unit)
        runCurrent()

        assertEquals(listOf("z"), controller.now.sections.map { it.id })
        assertEquals(PagingStatus.Idle, controller.now.paging)
        assertEquals(5, controller.now.nextPage)
    }

    @Test
    fun givenFirstLoadInFlight_whenRefreshed_thenTheLoadStartsOver() = runTest {
        // A sign-in or language change during the first load: its answer would be out of date.
        val controller = HomeFeedController(repository, backgroundScope)
        val stale = repository.holdSections(1)
        act { controller.load() }

        repository.sectionPages[1] = page(section("fresh"), next = null)
        act { controller.refresh() }
        stale.complete(Unit)
        runCurrent()

        assertEquals(listOf("fresh"), controller.now.sections.map { it.id })
        assertFalse(controller.now.isLoading)
    }

    // endregion

    // region Follow

    private fun TestScope.withChannels(): HomeFeedController {
        repository.sectionPages[1] = page(
            channelSection("studios", channel("ch-1", followers = 10), channel("ch-2", followers = null)),
            // The same channel in a second row.
            channelSection("more", channel("ch-1", followers = 10)),
            next = null,
        )
        return loaded()
    }

    private fun HomeFeedController.channel(row: String, id: String): ItemDetails.Channel =
        now.sections.first { it.id == row }.items.first { it.id == id }.details as ItemDetails.Channel

    @Test
    fun givenChannel_whenFollowed_thenEveryCardShowsTheNewStateAndCount() = runTest {
        val controller = withChannels()

        act { controller.setFollowing("ch-1", follow = true) }

        assertEquals(listOf("slug-ch-1" to true), repository.followCalls)
        assertEquals(ItemDetails.Channel(null, null, followers = 11, isFollowed = true), controller.channel("studios", "ch-1"))
        assertEquals(ItemDetails.Channel(null, null, followers = 11, isFollowed = true), controller.channel("more", "ch-1"))
        assertEquals(HomeFeedEffect.FollowChanged("ch-1", isFollowed = true), controller.effects.first())
    }

    @Test
    fun givenChannelWithoutCount_whenUnfollowed_thenCountStaysAtZero() = runTest {
        val controller = withChannels()

        act { controller.setFollowing("ch-2", follow = false) }

        assertEquals(ItemDetails.Channel(null, null, followers = 0, isFollowed = false), controller.channel("studios", "ch-2"))
    }

    @Test
    fun givenFollowFails_whenFollowing_thenCardIsUnchangedAndFailureIsAnnounced() = runTest {
        repository.followResult = DataResult.Failure(AppError.Network)
        val controller = withChannels()

        act { controller.setFollowing("ch-1", follow = true) }

        assertEquals(ItemDetails.Channel(null, null, followers = 10, isFollowed = false), controller.channel("studios", "ch-1"))
        assertEquals(HomeFeedEffect.FollowFailed("ch-1", AppError.Network), controller.effects.first())
        assertTrue(controller.now.updatingChannelIds.isEmpty())
    }

    @Test
    fun givenFollowInFlight_whenTappedAgain_thenOneRequest() = runTest {
        val controller = withChannels()
        val hold = repository.holdFollow()

        act { controller.setFollowing("ch-1", follow = true) }
        assertEquals(setOf("ch-1"), controller.now.updatingChannelIds)
        act { controller.setFollowing("ch-1", follow = true) }
        hold.complete(Unit)
        runCurrent()

        assertEquals(1, repository.followCalls.size)
        assertTrue(controller.now.updatingChannelIds.isEmpty())
    }

    @Test
    fun givenUnknownChannelOrNotAChannel_whenFollowing_thenNothingIsSent() = runTest {
        val controller = loaded()
        act { controller.setFollowing("a-0", follow = true) }
        act { controller.setFollowing("missing", follow = true) }
        assertTrue(repository.followCalls.isEmpty())
    }

    // endregion
}
