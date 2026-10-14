package com.vplayed.shared.home

/**
 * Content type codes used by the home API (`type` on rows and items). Decision D7: the union of
 * both apps — VPlayed also receives 8 (promos) and 9 (channels), read as [PROMO] and [CHANNEL].
 */
enum class ContentType(val code: Int) {
    VIDEO(1),
    PROMO(2),
    AUDIO(3),
    CHANNEL(4),
    BUNDLE(5),
    LIVE(6),
    MICRO_SERIES(7),
    UNKNOWN(-1),
    ;

    companion object {
        private const val PROMO_ALIAS = 8
        private const val CHANNEL_ALIAS = 9

        fun fromCode(code: Int?): ContentType = when (code) {
            PROMO_ALIAS -> PROMO
            CHANNEL_ALIAS -> CHANNEL
            else -> entries.firstOrNull { it.code == code } ?: UNKNOWN
        }
    }
}

/** What a title is (`sho_type`): 1 = video, 2 = series, 6 = micro series. The hero chip shows this, never a genre. */
enum class ShowType(val code: Int) {
    VIDEO(1),
    SERIES(2),
    MICRO_SERIES(6),
    ;

    companion object {
        fun fromCode(code: Int?): ShowType? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Which card a home row renders with on AndroidJP (VPlayed derives its own layout from
 * [HomeSection.type], [HomeSection.isTopTen] and the items, as its UI is native).
 */
enum class SectionStyle {
    /** 16:9 card with title and "Genre • duration": videos and series. */
    LANDSCAPE,

    /** 3:4 poster with title and meta: micro series. */
    PORTRAIT,

    /** 3:4 poster behind a large rank numeral: `is_top_ten` rows. */
    TOP_TEN,

    /** Wide live card with badge, schedule, channel and action. */
    LIVE,

    /** 16:9 card with a duration pill and views · likes: promos. */
    PROMO,

    /** Audio card. */
    MUSIC,

    /** Brand-colored channel tile with logo, description, followers and Follow. */
    CHANNEL,

    /** Bulk-purchase promo card, rendered without a row title. */
    OFFER,
}

/** A hero slide from `homebanners`. Image URLs are absolute. */
data class HomeBanner(
    val id: String,
    val title: String,
    val description: String?,
    /** Video / Series / Micro series, shown in the hero chip; null when the API sends no known type. */
    val showType: ShowType?,
    val imageUrl: String?,
    /** Logo-style title artwork; the hero shows it instead of the title text when it loads. */
    val titleImageUrl: String?,
    val slug: String? = null,
    /** Genre names (ids are skipped). */
    val genres: List<String> = emptyList(),
)

/**
 * An amount to show. [currencySign] is the sign to display — the API's `currency_sign`, else the
 * currency code; [hasSign] tells which, so a platform can substitute its own symbol for a code.
 */
data class Price(
    val amount: Double,
    val currencySign: String,
    val currency: String,
    val currencyType: Int? = null,
    val hasSign: Boolean = true,
)

/**
 * The corner tag on a content card (decision D14: VPlayed's rule, a port of the UIKit
 * `SeriesCollectionViewCell.configureMonetizationStatus` followed by `configureCrownStatus`).
 */
sealed interface AccessTag {
    data object ComingSoon : AccessTag

    /** Pay-per-view: the price to show. */
    data class Paid(val price: Price) : AccessTag

    /** Included in a subscription the user does not have. */
    data object Crown : AccessTag
}

data class ContentItem(
    val id: String,
    val title: String,
    /** The card artwork for this item's row style (AndroidJP). */
    val imageUrl: String?,
    val type: ContentType,
    /** First genre name (genre ids are dropped). */
    val genre: String? = null,
    /** Formatted running time, e.g. "2h 08m" or "4m 27s". */
    val duration: String? = null,
    /** Episode count for series and micro series (shown when there is no duration). */
    val episodeCount: Int? = null,
    val showType: ShowType? = null,
    val price: Price? = null,
    val details: ItemDetails = ItemDetails.None,
    val slug: String? = null,
    /** Running time in seconds, for platforms that format it themselves. */
    val durationSeconds: Double? = null,
    /** 16:9 artwork. */
    val landscapeImageUrl: String? = null,
    /** Portrait artwork. */
    val portraitImageUrl: String? = null,
    val accessTag: AccessTag? = null,
) {
    val isLive: Boolean get() = (details as? ItemDetails.Live)?.status == LiveStatus.LIVE
}

/** Data only some card types show. */
sealed interface ItemDetails {
    data object None : ItemDetails

    /** Live stream (`type` 6). [startsAtMillis] is the start time (UTC epoch millis). */
    data class Live(val status: LiveStatus, val startsAtMillis: Long?, val channelName: String?) : ItemDetails

    /** Promo clip (`type` 2). Null counts were not sent (VPlayed then hides the stats line). */
    data class Promo(val views: Long?, val likes: Long?) : ItemDetails

    /** Audio track (`type` 3). */
    data class Audio(val artist: String?) : ItemDetails

    /**
     * Channel / studio (`type` 4). [brandColor] is ARGB from `bg_card_color`; [followers] (null when
     * not sent) and [isFollowed] include the user's own follow changes made on Home.
     */
    data class Channel(
        val brandColor: Long?,
        val description: String?,
        val followers: Int?,
        val isFollowed: Boolean = false,
    ) : ItemDetails

    /** Bulk-purchase bundle (`type` 5). */
    data class Bundle(
        val label: String?,
        val description: String?,
        val days: Int?,
        /** Price to pay (after discount) and the original price when discounted. */
        val price: Price?,
        val originalPrice: Price?,
        val discountPercent: Int?,
        /** Prototype color variant from the banner artwork's file name. */
        val accent: BundleAccent,
        /** ARGB from `background_color` (decision D11: preferred when a platform can show any color). */
        val backgroundColor: Long? = null,
        /**
         * The price's `discounted_percentage` as listed, even when no discount applies (divergence
         * D20: VPlayed shows it as "Save up to N%"; AndroidJP shows [discountPercent] only).
         */
        val listedDiscountPercent: Double? = null,
    ) : ItemDetails
}

/**
 * Live state (decision D10: both apps' keys are read). `streaming_status`: 1 = live now,
 * 0 = scheduled, else finished; without it, `publish_status`: 3 = live, 0–2 = upcoming, else finished.
 */
enum class LiveStatus { LIVE, UPCOMING, ENDED }

/** Prototype bundle color variants, chosen from the bundle's banner artwork file name. */
enum class BundleAccent { BLUE, GOLD, VIOLET, GREEN }

data class HomeSection(
    val id: String,
    val title: String,
    val type: ContentType,
    val style: SectionStyle,
    /** 1 = single horizontal row, 2 = two stacked rows scrolling together. */
    val rowCount: Int,
    val items: List<ContentItem>,
    val totalItems: Int,
    /** Next page of this row's items (`home/all/{id}`), or null when every item is loaded. */
    val nextItemsPage: Int?,
    val isTopTen: Boolean = false,
    /** The API's row `type` as sent (8 and 9 included). */
    val typeCode: Int = type.code,
) {
    val canLoadMoreItems: Boolean get() = nextItemsPage != null

    /** The previous Android app offered "view all" for rows with more than two items (not top 10 / bundles). */
    val hasViewAll: Boolean
        get() = totalItems > VIEW_ALL_MIN_ITEMS && style != SectionStyle.TOP_TEN && style != SectionStyle.OFFER

    private companion object {
        const val VIEW_ALL_MIN_ITEMS = 2
    }
}

/** One page of home rows. */
data class HomeSectionsPage(
    val sections: List<HomeSection>,
    val nextPage: Int?,
    val totalSections: Int,
)

/** One page of a single row's items. */
data class SectionItemsPage(
    val items: List<ContentItem>,
    val nextPage: Int?,
)
