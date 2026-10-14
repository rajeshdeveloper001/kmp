package com.vplayed.shared.home

import com.vplayed.shared.core.config.ClientPlatform
import com.vplayed.shared.core.serialization.lenientBool
import com.vplayed.shared.core.serialization.lenientDouble
import com.vplayed.shared.core.serialization.lenientInt
import com.vplayed.shared.core.serialization.lenientLong
import com.vplayed.shared.core.serialization.lenientString
import com.vplayed.shared.core.serialization.localizedText
import kotlin.math.roundToInt
import kotlin.time.Instant
import com.vplayed.shared.movies.MoviesPage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Which image variants each app shows (decision D12). The two apps were tuned separately — AndroidJP
 * favours the mid-size `tv` render and reads only `default` images; VPlayed favours `tc`, falls back
 * to `webp` copies and has its own banner and micro-series rules — so each keeps its table until
 * design picks one. Every other mapping rule is shared.
 */
enum class HomeImagePolicy {
    ANDROID,
    APPLE,
    ;

    internal val formats: List<String>
        get() = when (this) {
            ANDROID -> listOf("default")
            // JPEG/PNG originals first: at the same size the WebP copies are compressed much harder.
            APPLE -> listOf("default", "webp")
        }

    companion object {
        fun forPlatform(platform: ClientPlatform): HomeImagePolicy = when (platform) {
            ClientPlatform.ANDROID -> ANDROID
            ClientPlatform.IOS -> APPLE
        }
    }
}

/**
 * Converts home API JSON to the shared models (a port of AndroidJP's `HomeMapper` and VPlayed's
 * `Home*` decoders). Reading is lenient: an odd field is treated as absent and an item without an
 * `_id` is dropped, so one bad entry never fails a page. [imageBaseUrl] turns relative image paths
 * into absolute URLs; [language] picks translations of localized texts.
 */
internal class HomeMapper(
    private val imageBaseUrl: String,
    private val imagePolicy: HomeImagePolicy,
    private val language: () -> String? = { null },
) {

    fun banners(response: JsonObject): List<HomeBanner> =
        response["data"].objects().mapNotNull(::banner).distinctBy { it.id }

    fun banner(dto: JsonObject): HomeBanner? {
        val content = dto["master_details"] as? JsonObject ?: return null
        val title = content["title"].lenientString() ?: return null
        val imagePath: String?
        val titleImagePath: String?
        when (imagePolicy) {
            HomeImagePolicy.ANDROID -> {
                imagePath = content["video_banner_images"].variant("mobile", "web")
                    ?: content["new_thumbnail_images"].variant("tl", "tc", "tv")
                // PNG variants first: `title_image` alone may be AVIF, which Android decodes only from API 31.
                titleImagePath = content["title_images"].variant("mobile", "web") ?: content["title_image"].lenientString()
            }
            HomeImagePolicy.APPLE -> {
                // The hero is portrait-tall, so the large `web` render crops best (UIKit `BannerTableViewCell` order).
                imagePath = content["video_banner_images"].variant("web", "key", "mobile")
                    ?: content["home_banner_images"].variant("web", "key", "mobile")
                    ?: content["home_banner_image"].lenientString()
                    ?: content["mobile_cover_image"].lenientString()
                    ?: content["poster_image"].lenientString()
                titleImagePath = content["title_images"].variant("web", "key", "mobile") ?: content["title_image"].lenientString()
            }
        }
        return HomeBanner(
            // The slide's own id (unique per slide), else its content's (VPlayed rule; AndroidJP never reads it).
            id = dto["_id"].lenientString() ?: content["_id"].lenientString() ?: title,
            title = title,
            description = content["description"].lenientString(),
            showType = ShowType.fromCode(content["sho_type"].lenientInt()),
            imageUrl = imageUrl(imagePath),
            titleImageUrl = imageUrl(titleImagePath),
            slug = content["slug"].lenientString(),
            genres = genreNames(content["genre"]),
        )
    }

    /** One page of rows: rows without items and rows of unknown types are dropped (both apps hid them). */
    fun sectionsPage(response: JsonObject): HomeSectionsPage = HomeSectionsPage(
        sections = response["data"].objects().mapNotNull(::section),
        nextPage = response["next"].positiveInt(),
        totalSections = response["total"].lenientInt() ?: 0,
    )

    fun section(dto: JsonObject): HomeSection? {
        val typeCode = dto["type"].lenientInt() ?: 0
        val type = ContentType.fromCode(typeCode)
        if (type == ContentType.UNKNOWN) return null
        val isTopTen = dto["is_top_ten"].lenientBool() ?: false
        val style = sectionStyle(type, isTopTen)
        val items = items(dto["data"], sectionType = type, style = style)
        if (items.isEmpty()) return null
        val title = dto["title"].localizedText(language()).orEmpty()
        return HomeSection(
            id = dto["_id"].lenientString() ?: "$typeCode-$title",
            title = title,
            type = type,
            style = style,
            rowCount = (dto["row_count"].lenientInt() ?: 1).coerceIn(1, MAX_ROWS),
            items = items,
            totalItems = dto["total"].lenientInt() ?: 0,
            nextItemsPage = dto["next"].positiveInt(),
            isTopTen = isTopTen,
            typeCode = typeCode,
        )
    }

    /** One page of `home/all/{id}`: the row's next items. */
    fun sectionItemsPage(response: JsonObject): SectionItemsPage {
        val category = response["category"] as? JsonObject
        val type = ContentType.fromCode(category?.get("type").lenientInt())
        val style = sectionStyle(type, isTopTen = category?.get("is_top_ten").lenientBool() ?: false)
        return SectionItemsPage(
            items = items(category?.get("data"), sectionType = type, style = style),
            nextPage = response["next"].positiveInt(),
        )
    }

    /**
     * One page of the movie catalogue (`sho-details/media/1`): `data` items as portrait video
     * cards, `total`, and `next` (null on the last page).
     */
    fun moviesPage(response: JsonObject): MoviesPage = MoviesPage(
        items = items(response["data"], sectionType = ContentType.VIDEO, style = SectionStyle.PORTRAIT),
        total = response["total"].lenientInt()?.takeIf { it >= 0 },
        nextPage = response["next"].positiveInt(),
    )

    private fun items(data: JsonElement?, sectionType: ContentType, style: SectionStyle): List<ContentItem> =
        data.objects().mapNotNull { item(it, sectionType, style) }.distinctBy { it.id }

    /** The AndroidJP card for each row type (AndroidJP `docs/api/home-api.md` › Section types). */
    private fun sectionStyle(type: ContentType, isTopTen: Boolean): SectionStyle = when {
        isTopTen -> SectionStyle.TOP_TEN
        else -> when (type) {
            ContentType.MICRO_SERIES -> SectionStyle.PORTRAIT
            ContentType.LIVE -> SectionStyle.LIVE
            ContentType.PROMO -> SectionStyle.PROMO
            ContentType.AUDIO -> SectionStyle.MUSIC
            ContentType.CHANNEL -> SectionStyle.CHANNEL
            ContentType.BUNDLE -> SectionStyle.OFFER
            ContentType.VIDEO, ContentType.UNKNOWN -> SectionStyle.LANDSCAPE
        }
    }

    fun item(
        dto: JsonObject,
        sectionType: ContentType = ContentType.UNKNOWN,
        style: SectionStyle = SectionStyle.LANDSCAPE,
    ): ContentItem? {
        val id = dto["_id"].lenientString() ?: return null
        val type = ContentType.fromCode(dto["type"].lenientInt()).takeUnless { it == ContentType.UNKNOWN } ?: sectionType
        val seconds = (if (type == ContentType.AUDIO) dto["audio_duration"] else dto["video_duration"]).lenientDouble()
        val price = preferredPrice(dto["prices"], dto["default_currency_type"].lenientInt())
        val images = images(dto, type, sectionType, portrait = style == SectionStyle.PORTRAIT || style == SectionStyle.TOP_TEN)
        return ContentItem(
            id = id,
            title = (dto["title"].lenientString() ?: dto["name"].lenientString() ?: dto["channel_name"].lenientString()).orEmpty(),
            imageUrl = images.card,
            type = type,
            genre = primaryGenre(dto["default_genre"], dto["genre"]),
            duration = seconds?.let(::formatDuration),
            episodeCount = dto["episodes_count"].lenientInt()?.takeIf { it > 0 },
            showType = ShowType.fromCode(dto["sho_type"].lenientInt()),
            price = price?.toPrice(price.amount),
            details = details(dto, type),
            slug = dto["slug"].lenientString(),
            durationSeconds = seconds,
            landscapeImageUrl = images.landscape,
            portraitImageUrl = images.portrait,
            accessTag = accessTag(
                monetization = dto["monetization"].lenientInt(),
                price = price?.toPrice(price.amount),
                isSubscription = dto["is_subscription"].lenientBool(),
                isUserSubscribed = dto["is_user_subscribed"].lenientBool(),
                isScheduled = dto["scheduled"].lenientBool(),
            ),
        )
    }

    private fun details(dto: JsonObject, type: ContentType): ItemDetails = when (type) {
        ContentType.LIVE -> live(dto)
        ContentType.PROMO -> ItemDetails.Promo(views = dto["view_count"].lenientLong(), likes = dto["guds_count"].lenientLong())
        ContentType.AUDIO -> ItemDetails.Audio(artist = dto["artist_name"].lenientString())
        ContentType.CHANNEL -> ItemDetails.Channel(
            brandColor = parseHexColor(dto["bg_card_color"].lenientString()),
            description = dto["description"].lenientString(),
            followers = dto["follow_count"].lenientInt(),
            isFollowed = dto["is_followed"].lenientBool() ?: false,
        )
        ContentType.BUNDLE -> bundle(dto)
        else -> ItemDetails.None
    }

    /** Decision D10: AndroidJP's keys (`streaming_status`, `schedule_at`) first, then VPlayed's (`publish_status`, `scheduled_at`). */
    private fun live(dto: JsonObject): ItemDetails.Live {
        val streaming = dto["streaming_status"].lenientInt()
        val status = if (streaming != null) {
            when (streaming) {
                STREAMING_LIVE -> LiveStatus.LIVE
                STREAMING_SCHEDULED -> LiveStatus.UPCOMING
                else -> LiveStatus.ENDED
            }
        } else {
            when (dto["publish_status"].lenientInt()) {
                PUBLISH_LIVE -> LiveStatus.LIVE
                in PUBLISH_UPCOMING -> LiveStatus.UPCOMING
                else -> LiveStatus.ENDED
            }
        }
        val scheduled = dto["schedule_at"].lenientString() ?: dto["scheduled_at"].lenientString()
        val started = dto["stream_start_time"].lenientString()
        val start = if (status == LiveStatus.UPCOMING) scheduled ?: started else started ?: scheduled
        return ItemDetails.Live(status = status, startsAtMillis = parseIsoMillis(start), channelName = dto["studio_name"].lenientString())
    }

    /** `discounted_price` is the amount taken off `amount`; the card shows the result and strikes the original. */
    private fun bundle(dto: JsonObject): ItemDetails.Bundle {
        val price = preferredPrice(dto["prices"], dto["default_currency_type"].lenientInt())
        val amount = price?.amount ?: 0.0
        val discount = price?.discountedPrice?.takeIf { it > 0 && it < amount }
        val bannerImage = dto["banner_img"].lenientString()
        return ItemDetails.Bundle(
            label = dto["label"].localizedText(language()),
            description = dto["description"].lenientString(),
            days = dto["days"].lenientInt()?.takeIf { it > 0 },
            price = price?.toPrice(if (discount != null) amount - discount else amount),
            originalPrice = if (discount != null) price.toPrice(amount) else null,
            discountPercent = if (discount != null) {
                price.discountedPercentage?.roundToInt()?.takeIf { it > 0 } ?: (discount / amount * PERCENT).roundToInt()
            } else {
                null
            },
            accent = bundleAccent(bannerImage),
            backgroundColor = parseHexColor(dto["background_color"].lenientString()),
            listedDiscountPercent = price?.discountedPercentage,
        )
    }

    /** The API's stock banners are `blue.png`, `yellow.png`, `dark-red.png`; custom artwork has no color hint. */
    private fun bundleAccent(bannerImage: String?): BundleAccent {
        val name = bannerImage.orEmpty().substringAfterLast('/').lowercase()
        return when {
            "blue" in name -> BundleAccent.BLUE
            "yellow" in name || "gold" in name -> BundleAccent.GOLD
            "green" in name -> BundleAccent.GREEN
            else -> BundleAccent.VIOLET
        }
    }

    // region Images

    private class ItemImages(val card: String?, val landscape: String?, val portrait: String?)

    private fun images(dto: JsonObject, type: ContentType, sectionType: ContentType, portrait: Boolean): ItemImages {
        val landscapePath: String?
        val portraitPath: String?
        when (imagePolicy) {
            HomeImagePolicy.ANDROID -> {
                landscapePath = androidImagePath(dto, type)
                portraitPath = dto["thumbnail_image"].lenientString() ?: landscapePath
            }
            HomeImagePolicy.APPLE -> {
                // VPlayed decodes items by the row type.
                val (landscape, poster) = appleImagePaths(dto, sectionType.takeUnless { it == ContentType.UNKNOWN } ?: type)
                landscapePath = landscape
                portraitPath = poster
            }
        }
        val landscape = imageUrl(landscapePath)
        val poster = imageUrl(portraitPath)
        return ItemImages(card = if (portrait) poster else landscape, landscape = landscape, portrait = poster)
    }

    private fun androidImagePath(dto: JsonObject, type: ContentType): String? = when (type) {
        ContentType.CHANNEL -> channelLogo(dto)
        ContentType.BUNDLE -> bundleImage(dto)
        ContentType.MICRO_SERIES -> dto["video_banner_images"].variant("mobile", "web") ?: androidCardVariant(dto)
        else -> androidCardVariant(dto) ?: dto["thumbnail_image"].lenientString() ?: dto["video_banner_images"].variant("mobile", "web")
    }

    /** A mid-size 16:9 variant first (sharp on phones, light on data). */
    private fun androidCardVariant(dto: JsonObject): String? = dto["new_thumbnail_images"].variant("tv", "tc", "tl", "tp", "tm")

    /** VPlayed's (landscape, portrait) artwork per row type (`ContentItem`, `AudioItem`, `ChannelItem`, `LiveItem`). */
    private fun appleImagePaths(dto: JsonObject, family: ContentType): Pair<String?, String?> {
        val newThumbnails = dto["new_thumbnail_images"]
        val thumbnails = dto["thumbnail_images"]
        val thumbnail = dto["thumbnail_image"].lenientString()
        val single: String? = when (family) {
            ContentType.AUDIO -> thumbnails.variant("web", "key", "mobile") ?: thumbnail ?: newThumbnails.variant("web", "mobile", "tm", "tp")
            ContentType.CHANNEL -> channelLogo(dto)
            ContentType.LIVE -> newThumbnails.variant("tc", "tl", "to", "tv", "tp", "tm") ?: thumbnail
            ContentType.BUNDLE -> bundleImage(dto)
            else -> return if (ShowType.fromCode(dto["sho_type"].lenientInt()) == ShowType.MICRO_SERIES) {
                // Micro-series `new_thumbnail_images` are portrait (3:4; `tp` is the largest, their `tc` tiny).
                Pair(
                    dto["mobile_poster_image"].lenientString() ?: dto["poster_image"].lenientString() ?: newThumbnails.variant("tl", "tp"),
                    newThumbnails.variant("tp", "to", "tl", "tm", "tc", "tv") ?: thumbnails.variant("web", "key", "mobile") ?: thumbnail,
                )
            } else {
                Pair(
                    newThumbnails.variant("tc", "tl", "to", "tv", "tp", "tm", "mobile", "web") ?: dto["poster_image"].lenientString() ?: thumbnail,
                    thumbnails.variant("web", "key", "mobile") ?: thumbnail ?: newThumbnails.variant("tc", "tv", "tp"),
                )
            }
        }
        return Pair(single, single)
    }

    private fun channelLogo(dto: JsonObject): String? = dto["profile_picture"].lenientString() ?: dto["channel_logo"].lenientString()

    private fun bundleImage(dto: JsonObject): String? = dto["banner_img"].lenientString() ?: dto["bulk_thumbnail_images"].variant("tl", "tv", "tp")

    /**
     * The first available path, trying [keys] in order and, for each, the policy's formats in order.
     * An image set maps format (`default`, `webp`, …) → size code (`tl`, `tc`, `tv`, `mobile`, …) → relative path.
     */
    private fun JsonElement?.variant(vararg keys: String): String? {
        val formats = this as? JsonObject ?: return null
        return keys.firstNotNullOfOrNull { key ->
            imagePolicy.formats.firstNotNullOfOrNull { format -> (formats[format] as? JsonObject)?.get(key).lenientString() }
        }
    }

    /** Absolute URL for an API image path; absolute URLs pass through unchanged. */
    fun imageUrl(path: String?): String? {
        val value = path?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (value.startsWith("http://") || value.startsWith("https://")) value else imageBaseUrl + value.trimStart('/')
    }

    // endregion

    // region Prices

    private class RawPrice(
        val amount: Double,
        val currency: String?,
        val currencyType: Int?,
        val currencySign: String?,
        val discountedPrice: Double?,
        val discountedPercentage: Double?,
    ) {
        fun toPrice(value: Double) = Price(
            amount = value,
            currencySign = currencySign ?: currency.orEmpty(),
            currency = currency.orEmpty(),
            currencyType = currencyType,
            hasSign = currencySign != null,
        )
    }

    /**
     * Decision D13 (VPlayed's `checkCurrencyIndex` rule): the active price whose `currency_type` is the
     * item's `default_currency_type` (1 = INR when absent), else the first active price.
     */
    private fun preferredPrice(prices: JsonElement?, defaultCurrencyType: Int?): RawPrice? {
        val active = prices.objects().mapNotNull { price ->
            val amount = price["amount"].lenientDouble() ?: return@mapNotNull null
            if (price["is_active"].lenientBool() == false) return@mapNotNull null
            RawPrice(
                amount = amount,
                currency = price["currency"].lenientString(),
                currencyType = price["currency_type"].lenientInt(),
                currencySign = price["currency_sign"].lenientString(),
                discountedPrice = price["discounted_price"].lenientDouble(),
                discountedPercentage = price["discounted_percentage"].lenientDouble(),
            )
        }
        val preferredType = defaultCurrencyType ?: INR_CURRENCY_TYPE
        return active.firstOrNull { it.currencyType == preferredType } ?: active.firstOrNull()
    }

    // endregion

    internal companion object {
        const val MAX_ROWS = 2
        const val STREAMING_SCHEDULED = 0
        const val STREAMING_LIVE = 1
        const val PUBLISH_LIVE = 3
        val PUBLISH_UPCOMING = 0..2
        const val INR_CURRENCY_TYPE = 1
        const val SECONDS_PER_MINUTE = 60
        const val MINUTES_PER_HOUR = 60
        const val PERCENT = 100
        const val OPAQUE = 0xFF000000L
        const val RGB_HEX_LENGTH = 6
        const val ARGB_HEX_LENGTH = 8
        const val HEX_RADIX = 16

        fun JsonElement?.objects(): List<JsonObject> = (this as? JsonArray).orEmpty().filterIsInstance<JsonObject>()

        fun JsonElement?.positiveInt(): Int? = lenientInt()?.takeIf { it > 0 }

        /**
         * Decision D14: VPlayed's card tag. Monetization status first (4/6/7 upcoming, 5 free,
         * otherwise the price unless subscribed), then the crown pass, which can override it.
         */
        fun accessTag(
            monetization: Int?,
            price: Price?,
            isSubscription: Boolean?,
            isUserSubscribed: Boolean?,
            isScheduled: Boolean?,
        ): AccessTag? {
            val subscribed = isUserSubscribed ?: false
            val priceTag = price?.let(AccessTag::Paid)
            var tag: AccessTag? = when (monetization) {
                4, 6, 7 -> AccessTag.ComingSoon
                5 -> null
                else -> if (subscribed) null else priceTag
            }
            when (monetization) {
                6, 7, 8 -> if (isSubscription != null && isUserSubscribed != null) {
                    if (isSubscription && !isUserSubscribed) {
                        tag = AccessTag.Crown
                    } else if (isUserSubscribed) {
                        tag = null
                    }
                }
                1 -> tag = when {
                    isSubscription == true && !subscribed -> AccessTag.Crown
                    isScheduled == true -> null
                    else -> priceTag
                }
                2, 5 -> tag = null
                else -> if (isSubscription == true && !subscribed) tag = AccessTag.Crown
            }
            return tag
        }

        /** "2h 08m", "4m 27s" or "45s" (AndroidJP card meta and promo duration pill). */
        fun formatDuration(seconds: Double): String? {
            if (seconds < 1) return null
            val total = seconds.toLong()
            val hours = total / (SECONDS_PER_MINUTE * MINUTES_PER_HOUR)
            val minutes = total / SECONDS_PER_MINUTE % MINUTES_PER_HOUR
            val secs = total % SECONDS_PER_MINUTE
            return when {
                hours > 0 -> "${hours}h ${twoDigits(minutes)}m"
                minutes > 0 -> "${minutes}m ${twoDigits(secs)}s"
                else -> "${secs}s"
            }
        }

        private fun twoDigits(value: Long): String = value.toString().padStart(2, '0')

        /** "#RRGGBB" or "#AARRGGBB" to an ARGB value; null when missing or malformed. */
        fun parseHexColor(value: String?): Long? {
            val hex = value?.trim()?.removePrefix("#") ?: return null
            val parsed = hex.toLongOrNull(HEX_RADIX)?.takeIf { it >= 0 } ?: return null
            return when (hex.length) {
                RGB_HEX_LENGTH -> OPAQUE or parsed
                ARGB_HEX_LENGTH -> parsed
                else -> null
            }
        }

        /** ISO-8601 timestamps as sent by the API ("2026-10-12T20:45:00.000Z"), with or without millis. */
        fun parseIsoMillis(value: String?): Long? {
            val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return try {
                Instant.parse(text).toEpochMilliseconds()
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        /** Database ids (24 hex chars) sometimes sent in place of genre names. */
        private val ObjectId = Regex("^[0-9a-fA-F]{24}$")

        /** Genre names from `[{"title": "Action"}]`, `[{"title": {"en": "Action"}}]` or `["Action"]`; ids are skipped. */
        fun genreNames(genres: JsonElement?): List<String> = (genres as? JsonArray).orEmpty().mapNotNull { it.genreName() }

        /**
         * The item's main genre for the card meta line. `default_genre` is an object (its title is
         * sometimes lowercase, e.g. "action"), a lowercase slug or an id; it is resolved to the
         * display title of the matching entry in `genre` when there is one.
         */
        fun primaryGenre(defaultGenre: JsonElement?, genres: JsonElement?): String? {
            val default = defaultGenre.genreName() ?: return genreNames(genres).firstOrNull()
            val match = (genres as? JsonArray).orEmpty().filterIsInstance<JsonObject>().firstOrNull { genre ->
                listOf(genre["slug"], genre["title"]).any { (it as? JsonPrimitive)?.content.equals(default, ignoreCase = true) }
            }
            return match.genreName()
                ?: if (defaultGenre is JsonPrimitive) default.replaceFirstChar { it.titlecase() } else default
        }

        private fun JsonElement?.genreName(): String? = when (this) {
            is JsonPrimitive -> lenientString()?.takeIf { isString && !ObjectId.matches(it) }
            is JsonObject -> this["title"].localizedText()
            else -> null
        }
    }
}
