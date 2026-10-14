package com.vplayed.shared.home

import com.vplayed.shared.core.serialization.SharedJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun response(fixture: String): JsonObject = SharedJson.parseToJsonElement(fixture).jsonObject.getValue("response").jsonObject

/** Ported from AndroidJP's `HomeMapperTest`: AndroidJP's image table, every other rule shared. */
class HomeMapperTest {

    private val mapper = HomeMapper(imageBaseUrl = "https://images.test/", imagePolicy = HomeImagePolicy.ANDROID)

    private fun page(fixture: String) = mapper.sectionsPage(response(fixture))

    private val allSections by lazy {
        (page(HomeFixtures.HOME_PAGE_1).sections + page(HomeFixtures.HOME_PAGE_2).sections + page(HomeFixtures.HOME_PAGE_3).sections)
            .associateBy { it.id }
    }

    private val banners = mapper.banners(response(HomeFixtures.BANNERS))

    @Test
    fun givenBannersWithAndWithoutContent_whenMapping_thenOnlyBannersWithTitlesRemain() {
        assertEquals(listOf("Sample Feature", "Second Feature"), banners.map { it.title })
    }

    @Test
    fun givenShowType_whenMappingBanner_thenChipUsesContentTypeNotGenre() {
        assertEquals(ShowType.SERIES, banners[0].showType)
        assertEquals(ShowType.MICRO_SERIES, banners[1].showType)
    }

    @Test
    fun givenBannerImages_whenMapping_thenPrefersMobileBannerAndPngTitleArtwork() {
        assertEquals("https://images.test/banners/feature-mobile.jpg", banners[0].imageUrl)
        assertEquals("https://images.test/titles/feature-mobile.png", banners[0].titleImageUrl)
        assertEquals("https://images.test/thumbs/second-tl.jpg", banners[1].imageUrl)
        assertNull(banners[1].titleImageUrl, "No title artwork: the hero shows the title text")
    }

    @Test
    fun givenSectionWithoutItems_whenMapping_thenItIsDropped() {
        val result = page(HomeFixtures.HOME_PAGE_1)
        assertEquals(listOf("sec-live", "sec-videos"), result.sections.map { it.id })
        assertEquals(2, result.nextPage)
        assertEquals(7, result.totalSections)
    }

    @Test
    fun givenSectionTypes_whenMapping_thenEachGetsItsPrototypeComponent() {
        val styles = allSections.mapValues { it.value.style }
        assertEquals(SectionStyle.LIVE, styles["sec-live"])
        assertEquals(SectionStyle.LANDSCAPE, styles["sec-videos"])
        assertEquals(SectionStyle.TOP_TEN, styles["sec-top"])
        assertEquals(SectionStyle.CHANNEL, styles["sec-channels"])
        assertEquals(SectionStyle.OFFER, styles["sec-bundles"])
        assertEquals(SectionStyle.PORTRAIT, styles["sec-micro"])
        assertEquals(SectionStyle.PROMO, styles["sec-promos"])
        assertEquals(SectionStyle.MUSIC, styles["sec-audio"])
    }

    @Test
    fun givenVideo_whenMapping_thenUsesMidSizeThumbnailGenreTitleAndDuration() {
        val video = allSections.getValue("sec-videos").items[0]
        assertEquals("https://images.test/thumbs/vid-1-tc.jpg", video.imageUrl)
        assertEquals("Thriller", video.genre, "Slug default_genre resolves to its display title")
        assertEquals("1h 30m", video.duration)
        assertEquals(5432.5, video.durationSeconds)
        assertEquals(ShowType.VIDEO, video.showType)
        assertEquals(ContentType.VIDEO, video.type)
    }

    @Test
    fun givenNumericDurationAndAbsoluteImage_whenMapping_thenBothAreHandled() {
        val video = allSections.getValue("sec-videos").items[1]
        assertEquals("2m 05s", video.duration)
        assertNull(video.genre)
        assertEquals("https://cdn.example.test/absolute/vid-2.jpg", video.imageUrl)
    }

    @Test
    fun givenSeveralPrices_whenMapping_thenPrefersRupees() {
        val price = allSections.getValue("sec-videos").items[0].price!!
        assertEquals("₹", price.currencySign)
        assertEquals(49.0, price.amount)
    }

    @Test
    fun givenStreamingStatus_whenMappingLiveItems_thenLiveAndUpcomingCarryStartTimeAndChannel() {
        val live = allSections.getValue("sec-live").items
        val now = live[0].details as ItemDetails.Live
        val later = live[1].details as ItemDetails.Live
        assertTrue(live[0].isLive)
        assertEquals(LiveStatus.LIVE, now.status)
        assertEquals(1_791_816_000_000L, now.startsAtMillis) // 2026-10-12T14:40:00Z
        assertEquals("Sample Studio", now.channelName)
        assertFalse(live[1].isLive)
        assertEquals(LiveStatus.UPCOMING, later.status)
        assertEquals(1_791_905_400_000L, later.startsAtMillis) // 2026-10-13T15:30:00Z, no millis
        assertNull(live[1].imageUrl)
    }

    @Test
    fun givenTopTenAndMicroSeries_whenMapping_thenUsePortraitThumbnailAndEpisodes() {
        assertEquals("https://images.test/posters/top-1.jpg", allSections.getValue("sec-top").items[0].imageUrl)
        val micro = allSections.getValue("sec-micro")
        assertEquals("https://images.test/posters/micro-1.jpg", micro.items[0].imageUrl)
        assertEquals(12, micro.items[0].episodeCount)
        assertEquals("Drama", micro.items[0].genre, "Lowercase default_genre title resolves to the genre list title")
        assertEquals(2, micro.rowCount)
    }

    @Test
    fun givenChannel_whenMapping_thenBrandColorDescriptionAndFollowers() {
        val channel = allSections.getValue("sec-channels").items[0]
        assertEquals("Sample Channel", channel.title)
        assertEquals("https://images.test/images/channel.png", channel.imageUrl)
        assertEquals(ItemDetails.Channel(brandColor = 0xFFA0E3FF, description = "Films and originals", followers = 1200), channel.details)
    }

    @Test
    fun givenDiscountedBundle_whenMapping_thenPriceIsReducedAndOriginalIsKept() {
        val bundle = allSections.getValue("sec-bundles").items[0]
        val details = bundle.details as ItemDetails.Bundle
        assertEquals("Weekend Pack", bundle.title)
        assertEquals("Special for you", details.label)
        assertEquals("Three films for one price.", details.description)
        assertEquals(120.0, details.price!!.amount)
        assertEquals(200.0, details.originalPrice!!.amount)
        assertEquals(40, details.discountPercent)
        assertEquals(BundleAccent.BLUE, details.accent)
    }

    @Test
    fun givenBundleWithoutDiscount_whenMapping_thenFullPriceValidityAndGoldAccent() {
        val details = allSections.getValue("sec-bundles").items[1].details as ItemDetails.Bundle
        assertEquals(99.0, details.price!!.amount)
        assertNull(details.originalPrice, "discounted_price 0 means no discount, whatever the percentage says")
        assertNull(details.discountPercent)
        assertEquals(12.0, details.listedDiscountPercent, "The listed percentage is kept for VPlayed's card (D20)")
        assertEquals(30, details.days)
        assertEquals(BundleAccent.GOLD, details.accent)
    }

    @Test
    fun givenPromoAndAudio_whenMapping_thenStatsDurationAndArtist() {
        val promo = allSections.getValue("sec-promos").items[0]
        assertEquals(ItemDetails.Promo(views = 4_200_000, likes = 312_000), promo.details)
        assertEquals("2m 28s", promo.duration)
        val audio = allSections.getValue("sec-audio").items[0]
        assertEquals(ItemDetails.Audio(artist = "Sample Artist"), audio.details)
        assertEquals("2h 38m", audio.duration)
    }

    @Test
    fun givenRowEndpointWithLocalizedTitle_whenMappingItems_thenReturnsItemsAndNextPage() {
        val result = mapper.sectionItemsPage(response(HomeFixtures.SECTION_ITEMS_PAGE_2))
        assertEquals(listOf("vid-11"), result.items.map { it.id })
        assertEquals(3, result.nextPage)
        assertEquals("Drama", result.items[0].genre)
    }

    @Test
    fun givenVPlayedOnlyShapes_whenMappingForAndroid_thenAndroidImageTableApplies() {
        val sections = mapper.sectionsPage(response(HomeFixtures.APPLE_PAGE)).sections.associateBy { it.id }
        // `tv` first, and `webp` copies are not read.
        assertEquals("https://images.test/thumbs/l1-tv.jpg", sections.getValue("sec-live6").items[0].imageUrl)
        assertNull(sections.getValue("sec-promo8").items[0].imageUrl)
        assertEquals("https://images.test/covers/a1.jpg", sections.getValue("3-No id row").items[0].imageUrl)
    }
}

/** VPlayed's rules (its `HomeDecodingTests` and `ImageAndAccessTagTests`) with VPlayed's image table. */
class HomeMapperAppleTest {

    private val mapper = HomeMapper(imageBaseUrl = "https://images.test/", imagePolicy = HomeImagePolicy.APPLE, language = { "ta" })

    private val page = mapper.sectionsPage(response(HomeFixtures.APPLE_PAGE))
    private val sections = page.sections.associateBy { it.id }

    private fun item(sectionId: String, index: Int = 0) = sections.getValue(sectionId).items[index]

    @Test
    fun givenRowTypes8And9_whenMapping_thenTheyArePromosAndChannels() {
        assertEquals(ContentType.PROMO, sections.getValue("sec-promo8").type)
        assertEquals(8, sections.getValue("sec-promo8").typeCode)
        assertEquals(SectionStyle.PROMO, sections.getValue("sec-promo8").style)
        assertEquals(ContentType.CHANNEL, sections.getValue("sec-ch9").type)
        assertEquals(9, sections.getValue("sec-ch9").typeCode)
    }

    @Test
    fun givenUnknownTypeRowAndRowWithoutId_whenMapping_thenUnknownIsDroppedAndIdIsDerived() {
        assertEquals(listOf("sec-promo8", "sec-ch9", "sec-live6", "sec-micro", "sec-bulk", "3-No id row"), page.sections.map { it.id })
        assertNull(page.nextPage, "next 0 ends the feed")
    }

    @Test
    fun givenTranslatedTitle_whenMapping_thenTheAppLanguageIsUsed() {
        assertEquals("Munnottangal", sections.getValue("sec-promo8").title)
    }

    @Test
    fun givenItemWithoutId_whenMapping_thenOnlyThatItemIsDropped() {
        assertEquals(listOf("m-1", "v-1"), sections.getValue("sec-micro").items.map { it.id })
    }

    @Test
    fun givenWebpOnlyArtworkAndStringCounts_whenMappingPromo_thenWebpAndCountsAreRead() {
        val promo = item("sec-promo8")
        assertEquals("https://images.test/thumbs/p8-1-tc.webp", promo.landscapeImageUrl)
        assertEquals(ItemDetails.Promo(views = 12, likes = null), promo.details)
        assertEquals(75.0, promo.durationSeconds)
        assertEquals("teaser", promo.slug)
    }

    @Test
    fun givenVPlayedChannelKeys_whenMapping_thenNameLogoFollowStateAndUnknownFollowers() {
        val channel = item("sec-ch9")
        assertEquals("Studio Nine", channel.title)
        assertEquals("https://images.test/logos/nine.png", channel.imageUrl)
        assertEquals(ItemDetails.Channel(brandColor = 0xFF112233, description = null, followers = null, isFollowed = true), channel.details)
    }

    @Test
    fun givenPublishStatus_whenMappingLive_thenLiveAndUpcomingWithStartTimes() {
        val live = item("sec-live6", 0)
        val upcoming = item("sec-live6", 1)
        assertEquals(ItemDetails.Live(LiveStatus.LIVE, 1_791_816_000_000L, null), live.details)
        assertEquals(ItemDetails.Live(LiveStatus.UPCOMING, 1_791_905_400_000L, null), upcoming.details)
        assertEquals("https://images.test/thumbs/l1-tc.jpg", live.imageUrl, "VPlayed prefers tc")
    }

    @Test
    fun givenMicroSeries_whenMapping_thenPosterIsLandscapeAndTpIsPortrait() {
        val micro = item("sec-micro", 0)
        assertEquals("https://images.test/posters/m1-mobile.jpg", micro.landscapeImageUrl)
        assertEquals("https://images.test/thumbs/m1-tp.jpg", micro.portraitImageUrl)
        assertEquals(micro.portraitImageUrl, micro.imageUrl, "Top-10 rows use the portrait art")
    }

    @Test
    fun givenVideo_whenMapping_thenTcLandscapeAndPortraitThumbnail() {
        val video = item("sec-micro", 1)
        assertEquals("https://images.test/thumbs/v1-tc.jpg", video.landscapeImageUrl)
        assertEquals("https://images.test/posters/v1-web.jpg", video.portraitImageUrl)
    }

    @Test
    fun givenDefaultCurrencyType_whenMapping_thenThatPriceIsPreferredAndShownAsPaidTag() {
        // Decision D13: `default_currency_type` 2 picks USD; the price has no sign, only a code.
        val video = item("sec-micro", 1)
        val expected = Price(amount = 3.5, currencySign = "USD", currency = "USD", currencyType = 2, hasSign = false)
        assertEquals(expected, video.price)
        assertEquals(AccessTag.Paid(expected), video.accessTag)
    }

    @Test
    fun givenSubscriptionTitleNotSubscribed_whenMapping_thenCrown() {
        assertEquals(AccessTag.Crown, item("sec-micro", 0).accessTag)
    }

    @Test
    fun givenBackgroundColor_whenMappingBundle_thenColorAccentAndDiscount() {
        val bundle = item("sec-bulk").details as ItemDetails.Bundle
        assertEquals(0xFFA0E3FF, bundle.backgroundColor)
        assertEquals(BundleAccent.BLUE, bundle.accent)
        assertEquals(150.0, bundle.price!!.amount)
        assertEquals(200.0, bundle.originalPrice!!.amount)
        assertEquals(25, bundle.discountPercent)
    }

    @Test
    fun givenAudio_whenMapping_thenThumbnailSetBeforeSingleThumbnail() {
        val audio = sections.getValue("3-No id row")
        assertEquals(ContentType.AUDIO, audio.type)
        assertEquals("https://images.test/covers/a1-mobile.jpg", audio.items[0].imageUrl)
    }

    @Test
    fun givenVPlayedBannerKeys_whenMapping_thenWebRendersSlugAndGenres() {
        val banners = mapper.banners(response(HomeFixtures.APPLE_BANNERS))
        assertEquals(listOf("banner-a", "banner-b", "banner-dup"), banners.map { it.id }, "Slides are identified by their own id")
        val hero = banners[0]
        assertEquals("https://images.test/banners/a-web.jpg", hero.imageUrl)
        assertEquals("https://images.test/titles/a-web.png", hero.titleImageUrl)
        assertEquals("hero", hero.slug)
        assertEquals(listOf("Drama"), hero.genres, "Genre ids are skipped")
        assertEquals("https://images.test/posters/b.jpg", banners[1].imageUrl)
        assertNull(banners[1].showType)
    }
}

/** VPlayed's `AccessTag` cases (decision D14). */
class AccessTagTest {
    private val rupees = Price(amount = 33.0, currencySign = "₹", currency = "INR", currencyType = 1)

    private fun tag(monetization: Int?, price: Price? = rupees, isSubscription: Boolean? = null, isUserSubscribed: Boolean? = null, isScheduled: Boolean? = null) =
        HomeMapper.accessTag(monetization, price, isSubscription, isUserSubscribed, isScheduled)

    @Test
    fun upcomingTitlesAreComingSoon() {
        listOf(4, 6, 7).forEach { assertEquals(AccessTag.ComingSoon, tag(it), "monetization $it") }
    }

    @Test
    fun freeTitlesHaveNoTag() {
        assertNull(tag(5))
        assertNull(tag(2))
    }

    @Test
    fun payPerViewShowsThePrice() {
        assertEquals(AccessTag.Paid(rupees), tag(3))
        assertNull(tag(3, isUserSubscribed = true), "Subscribers see no price")
        assertNull(tag(3, price = null))
    }

    @Test
    fun subscriptionTitleShowsCrownUntilSubscribed() {
        assertEquals(AccessTag.Crown, tag(1, isSubscription = true, isUserSubscribed = false))
        assertEquals(AccessTag.Crown, tag(8, price = null, isSubscription = true, isUserSubscribed = false))
        assertNull(tag(8, price = null, isSubscription = true, isUserSubscribed = true))
    }

    @Test
    fun comingSoonSubscriptionTitleShowsCrown() {
        assertEquals(AccessTag.Crown, tag(6, isSubscription = true, isUserSubscribed = false))
    }

    @Test
    fun scheduledPayPerViewHidesThePrice() {
        assertNull(tag(1, isScheduled = true))
        assertIs<AccessTag.Paid>(tag(1))
    }
}

class HomeFormattingTest {
    @Test
    fun durationsUseHoursMinutesOrSeconds() {
        assertEquals("2h 08m", HomeMapper.formatDuration(7_680.0))
        assertEquals("4m 27s", HomeMapper.formatDuration(267.72))
        assertEquals("45s", HomeMapper.formatDuration(45.0))
        assertNull(HomeMapper.formatDuration(0.5))
    }

    @Test
    fun hexColorsAcceptRgbAndArgbOnly() {
        assertEquals(0xFFA0E3FF, HomeMapper.parseHexColor("#A0E3FF"))
        assertEquals(0x80A0E3FF, HomeMapper.parseHexColor("80A0E3FF"))
        assertNull(HomeMapper.parseHexColor("#FFF"))
        assertNull(HomeMapper.parseHexColor("#-12345"))
        assertNull(HomeMapper.parseHexColor("blue"))
    }

    @Test
    fun timestampsWithOrWithoutMillisAndBadValues() {
        assertEquals(1_791_816_000_000L, HomeMapper.parseIsoMillis("2026-10-12T14:40:00.000Z"))
        assertEquals(1_791_816_000_000L, HomeMapper.parseIsoMillis("2026-10-12T14:40:00Z"))
        assertNull(HomeMapper.parseIsoMillis("12/10/2026"))
        assertNull(HomeMapper.parseIsoMillis(" "))
    }

    @Test
    fun localizedRowTitlesFallBackToEnglish() {
        val mapper = HomeMapper("https://images.test/", HomeImagePolicy.ANDROID, language = { "ar" })
        val sections = mapper.sectionsPage(response(HomeFixtures.APPLE_PAGE)).sections
        assertEquals("Trailers", sections.first().title)
    }
}
