package com.vplayed.shared.home

/*
 * Synthetic Home answers (no real content or user data). The first six are AndroidJP's
 * `app/src/test/resources/home` fixtures; [APPLE_PAGE] holds the shapes only VPlayed handled before
 * Phase 4 (row types 8/9, `publish_status`, monetization, `webp` copies, portrait micro-series art).
 */
internal object HomeFixtures {
    // AndroidJP app/src/test/resources/home/banners.json
    const val BANNERS = """
{
  "statusCode": 200,
  "status": "success",
  "message": "Home banners fetched successfully.",
  "response": {
    "data": [
      {
        "_id": "banner-1",
        "master_details": {
          "_id": "content-1",
          "title": "Sample Feature",
          "sho_type": 2,
          "description": "A synthetic banner used by unit tests.",
          "genre": ["6a1432f418d4d53c2c6003c4"],
          "default_genre": { "_id": "g-adventure", "title": { "en": "Adventure" } },
          "video_banner_images": { "default": { "mobile": "banners/feature-mobile.jpg", "web": "banners/feature-web.jpg" } },
          "title_images": { "default": { "mobile": "titles/feature-mobile.png", "web": "titles/feature-web.png" } },
          "title_image": "titles/feature.avif"
        }
      },
      {
        "_id": "banner-2",
        "master_details": {
          "_id": "content-2",
          "title": "Second Feature",
          "sho_type": 6,
          "genre": [{ "_id": "g-drama", "title": "Drama" }],
          "new_thumbnail_images": { "default": { "tl": "thumbs/second-tl.jpg" } }
        }
      },
      { "_id": "banner-without-content" }
    ]
  }
}
"""

    // AndroidJP app/src/test/resources/home/failed_envelope.json
    const val FAILED_ENVELOPE = """
{ "statusCode": 422, "status": "failed", "message": "Invalid page", "response": null }
"""

    // AndroidJP app/src/test/resources/home/home_page1.json
    const val HOME_PAGE_1 = """
{
  "statusCode": 200,
  "status": "success",
  "response": {
    "total": 7, "recordsPerPage": 3, "currentPage": 1, "totalPages": 3, "previous": null, "next": 2,
    "data": [
      {
        "_id": "sec-live", "title": "Live streaming", "type": 6, "is_top_ten": false, "row_count": 1,
        "total": 2, "next": null,
        "data": [
          { "_id": "live-1", "title": "Morning Show", "type": 6, "streaming_status": 1,
            "stream_start_time": "2026-10-12T14:40:00.000Z", "schedule_at": null, "studio_name": "Sample Studio",
            "genre": [{ "title": "News" }],
            "new_thumbnail_images": { "default": { "tv": "thumbs/live-1-tv.jpg", "tl": "thumbs/live-1-tl.jpg" } } },
          { "_id": "live-2", "title": "Evening Show", "type": 6, "streaming_status": 0,
            "schedule_at": "2026-10-13T15:30:00Z", "stream_start_time": null,
            "new_thumbnail_images": null }
        ]
      },
      {
        "_id": "sec-videos", "title": "Latest Movies", "type": 1, "is_top_ten": false, "row_count": 1,
        "total": 25, "next": 2,
        "data": [
          { "_id": "vid-1", "title": "First Film", "type": 1, "sho_type": 1, "video_duration": "5432.5",
            "default_genre": "thriller",
            "genre": [{ "title": "Action", "slug": "action" }, { "title": "Thriller", "slug": "thriller" }],
            "prices": [{ "amount": 3, "currency": "USD", "currency_type": 2, "currency_sign": "$", "is_active": true },
                       { "amount": 49, "currency": "INR", "currency_type": 1, "currency_sign": "₹", "is_active": true }],
            "new_thumbnail_images": { "default": { "tm": "thumbs/vid-1-tm.jpg", "tc": "thumbs/vid-1-tc.jpg" } } },
          { "_id": "vid-2", "title": "Second Film", "type": 1, "video_duration": 125,
            "thumbnail_image": "https://cdn.example.test/absolute/vid-2.jpg" }
        ]
      },
      { "_id": "sec-empty", "title": "Nothing here", "type": 1, "is_top_ten": false, "row_count": 1, "total": 0, "next": null, "data": [] }
    ]
  }
}
"""

    // AndroidJP app/src/test/resources/home/home_page2.json
    const val HOME_PAGE_2 = """
{
  "statusCode": 200,
  "status": "success",
  "response": {
    "total": 7, "currentPage": 2, "totalPages": 3, "next": 3,
    "data": [
      {
        "_id": "sec-top", "title": "Top Views", "type": 1, "is_top_ten": true, "row_count": 1, "total": 1, "next": null,
        "data": [ { "_id": "top-1", "title": "Ranked Film", "type": 1, "thumbnail_image": "posters/top-1.jpg",
                    "new_thumbnail_images": { "default": { "tv": "thumbs/top-1-tv.jpg" } } } ]
      },
      {
        "_id": "sec-channels", "title": "Popular Channels", "type": 4, "is_top_ten": false, "row_count": 1, "total": 1, "next": null,
        "data": [ { "_id": "ch-1", "name": "Sample Channel", "type": 4, "profile_picture": "images/channel.png",
                    "bg_card_color": "#A0E3FF", "description": "Films and originals", "follow_count": 1200 } ]
      },
      {
        "_id": "sec-bundles", "title": "Bulk purchase", "type": 5, "is_top_ten": false, "row_count": 1, "total": 2, "next": null,
        "data": [ { "_id": "bp-1", "name": "Weekend Pack", "label": { "en": "Special for you" },
                    "description": "Three films for one price.", "days": 7,
                    "banner_img": "https://cdn.example.test/static/blue.png",
                    "prices": [{ "amount": 200, "currency": "INR", "currency_type": 1, "currency_sign": "₹",
                                 "discounted_price": 80, "discounted_percentage": 40 }] },
                  { "_id": "bp-2", "name": "Summer Pack", "days": "30",
                    "banner_img": "https://cdn.example.test/static/yellow.png",
                    "prices": [{ "amount": 99, "currency": "INR", "currency_type": 1, "currency_sign": "₹",
                                 "discounted_price": 0, "discounted_percentage": 12 }] } ]
      }
    ]
  }
}
"""

    // AndroidJP app/src/test/resources/home/home_page3.json
    const val HOME_PAGE_3 = """
{
  "statusCode": 200,
  "status": "success",
  "response": {
    "total": 7, "currentPage": 3, "totalPages": 3, "next": null,
    "data": [
      {
        "_id": "sec-micro", "title": "Micro Series", "type": 7, "is_top_ten": false, "row_count": 2, "total": 1, "next": null,
        "data": [ { "_id": "micro-1", "title": "Short Story", "type": 7, "sho_type": 6, "episodes_count": 12,
                    "default_genre": { "title": "drama" }, "genre": [{ "title": "Drama", "slug": "drama" }], "thumbnail_image": "posters/micro-1.jpg",
                    "video_banner_images": { "default": { "mobile": "banners/micro-1-mobile.jpg" } } } ]
      },
      {
        "_id": "sec-promos", "title": "Promos", "type": 2, "is_top_ten": false, "row_count": 1, "total": 1, "next": null,
        "data": [ { "_id": "promo-1", "title": "Official Trailer", "type": 2, "video_duration": "148.2",
                    "view_count": 4200000, "guds_count": "312000",
                    "new_thumbnail_images": { "default": { "tv": "thumbs/promo-1-tv.jpg" } } } ]
      },
      {
        "_id": "sec-audio", "title": "Audio", "type": 3, "is_top_ten": false, "row_count": 1, "total": 1, "next": null,
        "data": [ { "_id": "audio-1", "title": "Title Song", "type": 3, "artist_name": "Sample Artist",
                    "audio_duration": "9488.3", "thumbnail_image": "covers/audio-1.jpg" } ]
      }
    ]
  }
}
"""

    // AndroidJP app/src/test/resources/home/section_items_page2.json
    const val SECTION_ITEMS_PAGE_2 = """
{
  "statusCode": 200,
  "status": "success",
  "response": {
    "total": 25, "currentPage": 2, "totalPages": 3, "previous": 1, "next": 3,
    "category": {
      "_id": "sec-videos", "title": { "en": "Latest Movies" }, "type": 1, "is_top_ten": false, "row_count": 1,
      "data": [
        { "_id": "vid-11", "title": "Eleventh Film", "type": 1, "genre": [{ "title": "Drama" }],
          "new_thumbnail_images": { "default": { "tv": "thumbs/vid-11-tv.jpg" } } }
      ]
    }
  }
}
"""
    /** VPlayed-only shapes, with values chosen so each rule has one clear answer. */
    const val APPLE_PAGE = """
{
  "statusCode": 200,
  "status": "success",
  "response": {
    "total": 7, "next": 0,
    "data": [
      { "_id": "sec-promo8", "title": { "en": "Trailers", "ta": "Munnottangal" }, "type": 8,
        "data": [ { "_id": "p8-1", "title": "Teaser", "slug": "teaser", "view_count": "12", "video_duration": "75",
                    "new_thumbnail_images": { "webp": { "tc": "thumbs/p8-1-tc.webp" } } } ] },
      { "_id": "sec-ch9", "title": "Studios", "type": 9,
        "data": [ { "_id": "ch-9", "channel_name": "Studio Nine", "slug": "studio-nine", "channel_logo": "logos/nine.png",
                    "is_followed": 1, "bg_card_color": "#112233" } ] },
      { "_id": "sec-live6", "title": "On air", "type": 6,
        "data": [ { "_id": "l-1", "title": "Concert", "publish_status": 3, "stream_start_time": "2026-10-12T14:40:00Z",
                    "new_thumbnail_images": { "default": { "tv": "thumbs/l1-tv.jpg", "tc": "thumbs/l1-tc.jpg" } } },
                  { "_id": "l-2", "title": "Talk", "publish_status": 1, "scheduled_at": "2026-10-13T15:30:00.000Z" } ] },
      { "_id": "sec-micro", "title": "Shorts", "type": 1, "is_top_ten": true,
        "data": [ { "_id": "m-1", "title": "Tiny", "sho_type": 6, "mobile_poster_image": "posters/m1-mobile.jpg",
                    "new_thumbnail_images": { "default": { "tc": "thumbs/m1-tc.jpg", "tp": "thumbs/m1-tp.jpg" } },
                    "monetization": 1, "is_subscription": true, "is_user_subscribed": false },
                  { "_id": "v-1", "title": "Film", "sho_type": 1,
                    "new_thumbnail_images": { "default": { "tv": "thumbs/v1-tv.jpg", "tc": "thumbs/v1-tc.jpg" } },
                    "thumbnail_images": { "default": { "web": "posters/v1-web.jpg" } },
                    "monetization": 3, "default_currency_type": 2,
                    "prices": [ { "amount": 49, "currency": "INR", "currency_type": 1, "currency_sign": "₹" },
                                { "amount": "3.5", "currency": "USD", "currency_type": 2 } ] },
                  { "title": "No id" } ] },
      { "_id": "sec-bulk", "title": "Plans", "type": 5,
        "data": [ { "_id": "b-1", "name": "Pack", "background_color": "#A0E3FF", "banner_img": "static/blue.png",
                    "prices": [ { "amount": 200, "currency": "INR", "currency_type": 1, "currency_sign": "₹", "discounted_price": 50 } ] } ] },
      { "_id": "sec-unknown", "title": "Future", "type": 42, "data": [ { "_id": "x-1", "title": "X" } ] },
      { "title": "No id row", "type": 3,
        "data": [ { "_id": "a-1", "title": "Song", "thumbnail_image": "covers/a1.jpg",
                    "thumbnail_images": { "default": { "mobile": "covers/a1-mobile.jpg" } } } ] }
    ]
  }
}
"""

    /** VPlayed's banner keys: `home_banner_images` and `title_images` with `web` renders. */
    const val APPLE_BANNERS = """
{
  "statusCode": 200,
  "response": {
    "data": [
      { "_id": "banner-a", "master_details": { "_id": "content-a", "title": "Hero", "slug": "hero", "sho_type": 1,
          "genre": [ { "title": "Drama" }, "6a1432f418d4d53c2c6003c4" ],
          "home_banner_images": { "default": { "mobile": "banners/a-mobile.jpg", "web": "banners/a-web.jpg" } },
          "title_images": { "default": { "mobile": "titles/a-mobile.png", "web": "titles/a-web.png" } } } },
      { "_id": "banner-b", "master_details": { "title": "Poster only", "poster_image": "posters/b.jpg" } },
      { "_id": "banner-dup", "master_details": { "_id": "content-a", "title": "Same content again" } }
    ]
  }
}
"""
}
