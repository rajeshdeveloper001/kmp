package com.vplayed.shared.home

import com.vplayed.shared.core.error.AppError
import com.vplayed.shared.core.error.DataResult
import kotlinx.coroutines.CompletableDeferred

/**
 * Scriptable [HomeRepository] (ported from AndroidJP's unit-test fake). Each call records its
 * arguments; results come from the maps, and a call can be held to observe in-flight states.
 */
internal class FakeHomeRepository : HomeRepository {
    var banners: DataResult<List<HomeBanner>> = DataResult.Success(listOf(banner("b1")))
    val sectionPages = mutableMapOf<Int, DataResult<HomeSectionsPage>>()
    val itemPages = mutableMapOf<Pair<String, Int>, DataResult<SectionItemsPage>>()
    var followResult: DataResult<Unit> = DataResult.Success(Unit)
    val sectionCalls = mutableListOf<Int>()
    val itemCalls = mutableListOf<Pair<String, Int>>()
    val followCalls = mutableListOf<Pair<String, Boolean>>()
    private val sectionHolds = mutableMapOf<Int, CompletableDeferred<Unit>>()
    private var bannerHold: CompletableDeferred<Unit>? = null
    private var followHold: CompletableDeferred<Unit>? = null

    fun holdSections(page: Int): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { sectionHolds[page] = it }

    fun holdBanners(): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { bannerHold = it }

    fun holdFollow(): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { followHold = it }

    override suspend fun getBanners(): DataResult<List<HomeBanner>> {
        bannerHold?.also { bannerHold = null }?.await()
        return banners
    }

    override suspend fun getSections(page: Int): DataResult<HomeSectionsPage> {
        sectionCalls += page
        sectionHolds.remove(page)?.await()
        return sectionPages[page] ?: DataResult.Failure(AppError.Server(404))
    }

    override suspend fun getSectionItems(sectionId: String, page: Int): DataResult<SectionItemsPage> {
        itemCalls += sectionId to page
        return itemPages[sectionId to page] ?: DataResult.Failure(AppError.Server(404))
    }

    override suspend fun setFollowing(channelSlug: String, follow: Boolean): DataResult<Unit> {
        followCalls += channelSlug to follow
        followHold?.also { followHold = null }?.await()
        return followResult
    }

    companion object {
        fun banner(id: String) = HomeBanner(id, "Banner $id", null, ShowType.VIDEO, null, null)

        fun item(id: String) = ContentItem(id, "Title $id", null, ContentType.VIDEO, genre = "Drama")

        fun channel(id: String, followers: Int? = 10, isFollowed: Boolean = false) = ContentItem(
            id = id,
            title = "Channel $id",
            imageUrl = null,
            type = ContentType.CHANNEL,
            details = ItemDetails.Channel(brandColor = null, description = null, followers = followers, isFollowed = isFollowed),
            slug = "slug-$id",
        )

        fun section(id: String, items: Int = 3, nextItemsPage: Int? = null) = HomeSection(
            id = id,
            title = "Row $id",
            type = ContentType.VIDEO,
            style = SectionStyle.LANDSCAPE,
            rowCount = 1,
            items = List(items) { item("$id-$it") },
            totalItems = items,
            nextItemsPage = nextItemsPage,
        )

        fun channelSection(id: String, vararg channels: ContentItem) = HomeSection(
            id = id,
            title = "Row $id",
            type = ContentType.CHANNEL,
            style = SectionStyle.CHANNEL,
            rowCount = 1,
            items = channels.toList(),
            totalItems = channels.size,
            nextItemsPage = null,
        )

        fun page(vararg sections: HomeSection, next: Int?) = DataResult.Success(
            HomeSectionsPage(sections.toList(), nextPage = next, totalSections = sections.size),
        )
    }
}
