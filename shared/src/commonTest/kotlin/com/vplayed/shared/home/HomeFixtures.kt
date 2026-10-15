package com.vplayed.shared.home

/*
 * Synthetic and real-time production Home API fixtures.
 */
object HomeFixtures {
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

    const val HOME_BANNERS_SEND_REQUEST = """{
  "method": "GET",
  "endpoint": "mediaview/api/v1/homebanners",
  "headers": {
    "Accept": "application/json",
    "X-Request-Type": "mobile",
    "device_type": "android",
    "app_version": "3.0.0"
  }
}"""

    const val HOME_BANNERS_REALTIME_RESPONSE = """{
  "statusCode": 200,
  "message": "Home banners fetched successfully.",
  "status": "success",
  "response": {
    "data": [
      {
        "_id": "6ad079ea1a879fec419f2065",
        "is_active": true,
        "rank": "0|xzzzy8:",
        "created_at": "2026-10-15T06:59:54.713Z",
        "updated_at": "2026-10-15T06:59:54.713Z",
        "__v": 0,
        "studio_details": {
          "_id": "6a1432ca1121914bd8a167f2",
          "name": "VplayedDev",
          "slug": "vplayeddev",
          "bg_card_color": "#9C760A"
        },
        "master_details": {
          "_id": "6a2f84f52e1dcb33c68e6457",
          "title": "Vide Detail Page Test",
          "age_limit": 1,
          "poster_image": "gudsho-upload-poster-images/15-06-2026/1781499115019-web-BloodFather.jpg",
          "home_banner_image": "gudsho-upload-home-banner-images/15-06-2026/1781499123703-web-BloodFather.jpg",
          "slug": "blood-father",
          "sho_type": 1,
          "title_image": null,
          "poster_images": {
            "default": {
              "mobile": "gudsho-upload-poster-images/15-06-2026/1781499115052-mobile-BloodFather.jpg",
              "web": "gudsho-upload-poster-images/15-06-2026/1781499115019-web-BloodFather.jpg",
              "Key": "gudsho-upload-poster-images/15-06-2026/1781499115019-web-BloodFather.jpg"
            },
            "webp": {
              "mobile": "gudsho-upload-poster-images/15-06-2026/1781499115177-mobile-BloodFather.webp",
              "web": "gudsho-upload-poster-images/15-06-2026/1781499115135-web-BloodFather.webp",
              "Key": "gudsho-upload-poster-images/15-06-2026/1781499115135-web-BloodFather.webp"
            },
            "avif": {
              "mobile": "gudsho-upload-poster-images/15-06-2026/1781499115538-mobile-BloodFather.avif",
              "web": "gudsho-upload-poster-images/15-06-2026/1781499115615-web-BloodFather.avif",
              "Key": "gudsho-upload-poster-images/15-06-2026/1781499115615-web-BloodFather.avif"
            }
          },
          "home_banner_images": {
            "default": {
              "mobile": "gudsho-upload-home-banner-images/15-06-2026/1781499123694-mobile-BloodFather.jpg",
              "web": "gudsho-upload-home-banner-images/15-06-2026/1781499123703-web-BloodFather.jpg",
              "Key": "gudsho-upload-home-banner-images/15-06-2026/1781499123703-web-BloodFather.jpg"
            },
            "webp": {
              "mobile": "gudsho-upload-home-banner-images/15-06-2026/1781499123706-mobile-BloodFather.webp",
              "web": "gudsho-upload-home-banner-images/15-06-2026/1781499123732-web-BloodFather.webp",
              "Key": "gudsho-upload-home-banner-images/15-06-2026/1781499123732-web-BloodFather.webp"
            },
            "avif": {
              "mobile": "gudsho-upload-home-banner-images/15-06-2026/1781499123964-mobile-BloodFather.avif",
              "web": "gudsho-upload-home-banner-images/15-06-2026/1781499124035-web-BloodFather.avif",
              "Key": "gudsho-upload-home-banner-images/15-06-2026/1781499124035-web-BloodFather.avif"
            }
          },
          "title_images": null,
          "new_thumbnail_images": {
            "default": {
              "tm": "gudsho-upload-new_thumbnail_images/15-06-2026/1781499124255-tm-BloodFather.jpg",
              "ts": "gudsho-upload-new_thumbnail_images/15-06-2026/1781499124262-ts-BloodFather.jpg",
              "tv": "gudsho-upload-new_thumbnail_images/15-06-2026/1781499124316-tv-BloodFather.jpg",
              "ta": "gudsho-upload-new_thumbnail_images/15-06-2026/1781499124282-ta-BloodFather.jpg",
              "tp": "gudsho-upload-new_thumbnail_images/15-06-2026/1781499124275-tp-BloodFather.jpg",
              "to": "gudsho-upload-new_thumbnail_images/15-06-2026/1781499124286-to-BloodFather.jpg",
              "tc": "gudsho-upload-new_thumbnail_images/15-06-2026/1781499124288-tc-BloodFather.jpg",
              "tl": "gudsho-upload-new_thumbnail_images/15-06-2026/1781499124287-tl-BloodFather.jpg"
            },
            "webp": {
              "ta": "gudsho-upload-new_thumbnail_images/15-06-2026/1781499124347-ta-BloodFather.webp",
              "ts": "gudsho-upload-new_thumbnail_images/15-06-2026/1781499124334-ts-BloodFather.webp",
              "tm": "gudsho-upload-new_thumbnail_images/15-06-2026/1781499124325-tm-BloodFather.webp",
              "tc": "gudsho-upload-new_thumbnail_images/15-06-2026/1781499124342-tc-BloodFather.webp",
              "tp": "gudsho-upload-new_thumbnail_images/15-06-2026/1781499124348-tp-BloodFather.webp",
              "tv": "gudsho-upload-new_thumbnail_images/15-06-2026/1781499124388-tv-BloodFather.webp",
              "tl": "gudsho-upload-new_thumbnail_images/15-06-2026/1781499124343-tl-BloodFather.webp",
              "to": "gudsho-upload-new_thumbnail_images/15-06-2026/1781499124340-to-BloodFather.webp"
            },
            "avif": {
              "tp": "gudsho-upload-new_thumbnail_images/15-06-2026/1781499124751-tp-BloodFather.avif",
              "tm": "gudsho-upload-new_thumbnail_images/15-06-2026/1781499124495-tm-BloodFather.avif",
              "ts": "gudsho-upload-new_thumbnail_images/15-06-2026/1781499124584-ts-BloodFather.avif",
              "ta": "gudsho-upload-new_thumbnail_images/15-06-2026/1781499124811-ta-BloodFather.avif",
              "tl": "gudsho-upload-new_thumbnail_images/15-06-2026/1781499124758-tl-BloodFather.avif",
              "to": "gudsho-upload-new_thumbnail_images/15-06-2026/1781499124765-to-BloodFather.avif",
              "tc": "gudsho-upload-new_thumbnail_images/15-06-2026/1781499124792-tc-BloodFather.avif",
              "tv": "gudsho-upload-new_thumbnail_images/15-06-2026/1781499125127-tv-BloodFather.avif"
            }
          },
          "video_banner_images": {
            "default": {
              "mobile": "gudsho-upload-video-banner-images/15-06-2026/1781499123095-mobile-BloodFather.jpg",
              "web": "gudsho-upload-video-banner-images/15-06-2026/1781499123101-web-BloodFather.jpg",
              "Key": "gudsho-upload-video-banner-images/15-06-2026/1781499123101-web-BloodFather.jpg"
            },
            "webp": {
              "mobile": "gudsho-upload-video-banner-images/15-06-2026/1781499123146-mobile-BloodFather.webp",
              "web": "gudsho-upload-video-banner-images/15-06-2026/1781499123139-web-BloodFather.webp",
              "Key": "gudsho-upload-video-banner-images/15-06-2026/1781499123139-web-BloodFather.webp"
            },
            "avif": {
              "mobile": "gudsho-upload-video-banner-images/15-06-2026/1781499123426-mobile-BloodFather.avif",
              "web": "gudsho-upload-video-banner-images/15-06-2026/1781499123464-web-BloodFather.avif",
              "Key": "gudsho-upload-video-banner-images/15-06-2026/1781499123464-web-BloodFather.avif"
            }
          },
          "use_video_thumbnail_image_instead": false,
          "monetization": 1,
          "description": "An ex-con reconnects with his estranged teenage daughter while fighting to keep her alive after she becomes a target of violent drug dealers.",
          "thumbnail_image": "gudsho-upload-thumbnail-images/15-06-2026/1781499117862-web-BloodFather.jpg",
          "thumbnail_images": {
            "default": {
              "mobile": "gudsho-upload-thumbnail-images/15-06-2026/1781499117856-mobile-BloodFather.jpg",
              "web": "gudsho-upload-thumbnail-images/15-06-2026/1781499117862-web-BloodFather.jpg",
              "Key": "gudsho-upload-thumbnail-images/15-06-2026/1781499117862-web-BloodFather.jpg"
            },
            "webp": {
              "web": "gudsho-upload-thumbnail-images/15-06-2026/1781499117864-web-BloodFather.webp",
              "mobile": "gudsho-upload-thumbnail-images/15-06-2026/1781499117876-mobile-BloodFather.webp",
              "Key": "gudsho-upload-thumbnail-images/15-06-2026/1781499117864-web-BloodFather.webp"
            },
            "avif": {
              "mobile": "gudsho-upload-thumbnail-images/15-06-2026/1781499117932-mobile-BloodFather.avif",
              "web": "gudsho-upload-thumbnail-images/15-06-2026/1781499118015-web-BloodFather.avif",
              "Key": "gudsho-upload-thumbnail-images/15-06-2026/1781499118015-web-BloodFather.avif"
            }
          },
          "tags": [
            "drug dealer",
            "trailer park",
            "parent child relationship",
            "on the run",
            "teenage girl"
          ],
          "genre": [
            {
              "_id": "6a1432f418d4d53c2c6003c4",
              "title": "Action",
              "slug": "action"
            },
            {
              "_id": "6a1432f418d4d54c5d6003d2",
              "title": "Thriller",
              "slug": "thriller"
            },
            {
              "_id": "6a1432f418d4d563cb6003c8",
              "title": "Drama",
              "slug": "drama"
            }
          ],
          "default_genre": {
            "_id": "6a1432f418d4d533716003d0",
            "is_active": true,
            "is_archived": false,
            "title": {
              "en": "Science fiction",
              "es": "Ciencia ficción",
              "fr": "Science-fiction",
              "hi": "विज्ञान कथा",
              "ja": "SF",
              "ko": "공상과학",
              "ml": "സയൻസ് ഫിക്ഷൻ",
              "ta": "அறிவியல் புனைவு",
              "te": "సైన్స్ ఫిక్షన్",
              "tr": "Bilim kurgu"
            },
            "slug": "science-fiction",
            "creator_id": "6a1432ca11219130eba167e0",
            "updator_id": "6a1432ca11219130eba167e0",
            "created_at": "2026-05-25T11:31:00.508Z",
            "updated_at": "2026-05-25T11:31:00.508Z",
            "__v": 0
          },
          "access_period": "6a670a9b55c13db9d1970d3c",
          "studio_slug": "vplayeddev",
          "scheduled_at": null,
          "is_published": 1,
          "geo_area": [],
          "publish_status": 0,
          "content_label": {
            "is_full_time": true,
            "title": "Hot Movie"
          },
          "is_watch_later": 0
        }
      },
      {
        "_id": "6ac49c05dcd757c38880f285",
        "is_active": true,
        "rank": "0|xzzzyg:",
        "created_at": "2026-10-06T06:58:13.173Z",
        "updated_at": "2026-10-06T06:58:13.173Z",
        "__v": 0,
        "studio_details": {
          "_id": "6a1432ca1121914bd8a167f2",
          "name": "VplayedDev",
          "slug": "vplayeddev",
          "bg_card_color": "#9C760A"
        },
        "master_details": {
          "_id": "6a2cec2e0db67c100e8db016",
          "title": "Captain America: Brave New World",
          "age_limit": 1,
          "slug": "captain-america:-brave-new-world",
          "sho_type": 2,
          "title_image": "gudsho-upload-title-images/13-06-2026/1781329364273-web-blob.avif",
          "title_images": {
            "default": {
              "web": "gudsho-upload-title-images/13-06-2026/1781329362075-web-blob.png",
              "mobile": "gudsho-upload-title-images/13-06-2026/1781329362075-web-blob.png"
            },
            "webp": {
              "web": "gudsho-upload-title-images/13-06-2026/1781329363979-web-blob.webp",
              "mobile": "gudsho-upload-title-images/13-06-2026/1781329363987-mobile-blob.webp",
              "Key": "gudsho-upload-title-images/13-06-2026/1781329363979-web-blob.webp"
            },
            "avif": {
              "web": "gudsho-upload-title-images/13-06-2026/1781329364273-web-blob.avif",
              "mobile": "gudsho-upload-title-images/13-06-2026/1781329364277-mobile-blob.avif",
              "Key": "gudsho-upload-title-images/13-06-2026/1781329364273-web-blob.avif"
            }
          },
          "new_thumbnail_images": {
            "default": {
              "to": "gudsho-upload-new_thumbnail_images/13-06-2026/1781328992040-blob.jpg",
              "tl": "gudsho-upload-new_thumbnail_images/13-06-2026/1781328996140-blob.jpg",
              "tc": "gudsho-upload-new_thumbnail_images/13-06-2026/1781328997297-blob.jpg",
              "tm": "gudsho-upload-new_thumbnail_images/13-06-2026/1781328996515-blob.jpg",
              "ts": "gudsho-upload-new_thumbnail_images/13-06-2026/1781328996570-blob.jpg",
              "tp": "gudsho-upload-new_thumbnail_images/13-06-2026/1781328996768-blob.jpg",
              "ta": "gudsho-upload-new_thumbnail_images/13-06-2026/1781328997156-blob.jpg",
              "tv": "gudsho-upload-new_thumbnail_images/13-06-2026/1781328997213-blob.jpg"
            },
            "webp": {
              "to": "gudsho-upload-new_thumbnail_images/13-06-2026/1781328991464-blob.webp",
              "tl": "gudsho-upload-new_thumbnail_images/13-06-2026/1781328996182-blob.webp",
              "tc": "gudsho-upload-new_thumbnail_images/13-06-2026/1781328996159-blob.webp",
              "tm": "gudsho-upload-new_thumbnail_images/13-06-2026/1781328993328-blob.webp",
              "ts": "gudsho-upload-new_thumbnail_images/13-06-2026/1781328993415-blob.webp",
              "tp": "gudsho-upload-new_thumbnail_images/13-06-2026/1781328993890-blob.webp",
              "ta": "gudsho-upload-new_thumbnail_images/13-06-2026/1781328993808-blob.webp",
              "tv": "gudsho-upload-new_thumbnail_images/13-06-2026/1781328994761-blob.webp"
            }
          },
          "video_banner_images": {
            "default": {
              "web": "gudsho-upload-video-banner-images/13-06-2026/1781330074915-web-blob.jpg",
              "mobile": "gudsho-upload-video-banner-images/13-06-2026/1781330074924-mobile-blob.jpg",
              "Key": "gudsho-upload-video-banner-images/13-06-2026/1781330074915-web-blob.jpg"
            },
            "webp": {
              "web": "gudsho-upload-video-banner-images/13-06-2026/1781330080238-web-blob.webp",
              "mobile": "gudsho-upload-video-banner-images/13-06-2026/1781330080248-mobile-blob.webp",
              "Key": "gudsho-upload-video-banner-images/13-06-2026/1781330080238-web-blob.webp"
            },
            "avif": {
              "web": "gudsho-upload-video-banner-images/13-06-2026/1781330084404-web-blob.avif",
              "mobile": "gudsho-upload-video-banner-images/13-06-2026/1781330084410-mobile-blob.avif",
              "Key": "gudsho-upload-video-banner-images/13-06-2026/1781330084404-web-blob.avif"
            }
          },
          "use_video_thumbnail_image_instead": false,
          "monetization": 5,
          "description": "Following the events of Avengers: Endgame, Sam Wilson (The Falcon) grapples with the weight of Captain America's legacy while Bucky Barnes (the Winter Soldier) seeks redemption for his violent past. ",
          "tags": [
            "#Captain",
            "#america"
          ],
          "genre": [
            "6a1432f418d4d53c2c6003c4"
          ],
          "default_genre": {
            "_id": "6a1432f418d4d59ab56003c5",
            "is_active": true,
            "is_archived": false,
            "title": {
              "en": "Adventure"
            },
            "slug": "adventure"
          },
          "studio_slug": "vplayeddev",
          "is_published": 1,
          "geo_area": [],
          "publish_status": 0,
          "is_watch_later": 0
        }
      },
      {
        "_id": "6ac33d7da8dd9b09e6a08ae4",
        "is_active": true,
        "rank": "0|xzzzyo:",
        "created_at": "2026-10-05T06:02:37.594Z",
        "updated_at": "2026-10-05T06:02:37.594Z",
        "__v": 0,
        "studio_details": {
          "_id": "6a1432ca1121914bd8a167f2",
          "name": "VplayedDev",
          "slug": "vplayeddev",
          "bg_card_color": "#9C760A"
        },
        "master_details": {
          "_id": "6a2d2b424ca542acd5098bf3",
          "title": "The Snow Girl",
          "age_limit": 3,
          "slug": "the-snow-girl",
          "sho_type": 6,
          "new_thumbnail_images": {
            "default": {
              "to": "gudsho-upload-micro_series_thumbnail_images/13-06-2026/1781345314777-blob.jpg",
              "tl": "gudsho-upload-micro_series_thumbnail_images/13-06-2026/1781345315385-blob.jpg",
              "tc": "gudsho-upload-micro_series_thumbnail_images/13-06-2026/1781345315352-blob.jpg",
              "tm": "gudsho-upload-micro_series_thumbnail_images/13-06-2026/1781345315616-blob.jpg",
              "ts": "gudsho-upload-micro_series_thumbnail_images/13-06-2026/1781345315562-blob.jpg",
              "tp": "gudsho-upload-micro_series_thumbnail_images/13-06-2026/1781345315750-blob.jpg",
              "tv": "gudsho-upload-micro_series_thumbnail_images/13-06-2026/1781345315711-blob.jpg"
            },
            "webp": {
              "to": "gudsho-upload-micro_series_thumbnail_images/13-06-2026/1781345314501-blob.webp",
              "tl": "gudsho-upload-micro_series_thumbnail_images/13-06-2026/1781345315257-blob.webp",
              "tc": "gudsho-upload-micro_series_thumbnail_images/13-06-2026/1781345314845-blob.webp",
              "tm": "gudsho-upload-micro_series_thumbnail_images/13-06-2026/1781345314979-blob.webp",
              "ts": "gudsho-upload-micro_series_thumbnail_images/13-06-2026/1781345314668-blob.webp",
              "tp": "gudsho-upload-micro_series_thumbnail_images/13-06-2026/1781345315504-blob.webp",
              "tv": "gudsho-upload-micro_series_thumbnail_images/13-06-2026/1781345315162-blob.webp"
            }
          },
          "video_banner_images": {
            "default": {
              "web": "gudsho-upload-video-banner-images/13-06-2026/1781345654852-web-blob.jpg",
              "mobile": "gudsho-upload-video-banner-images/13-06-2026/1781345654860-mobile-blob.jpg",
              "Key": "gudsho-upload-video-banner-images/13-06-2026/1781345654852-web-blob.jpg"
            },
            "webp": {
              "web": "gudsho-upload-video-banner-images/13-06-2026/1781345658611-web-blob.webp",
              "mobile": "gudsho-upload-video-banner-images/13-06-2026/1781345658616-mobile-blob.webp",
              "Key": "gudsho-upload-video-banner-images/13-06-2026/1781345658611-web-blob.webp"
            }
          },
          "use_video_thumbnail_image_instead": false,
          "monetization": 5,
          "description": "During the Cavalcade of the Magi parade, the Martín family's daughter Amaya disappears. Journalist Miren takes on the case, determined to find her.",
          "tags": [
            "#Snow",
            "#Girl"
          ],
          "genre": [
            {
              "_id": "6a1432f418d4d5994d6003c7",
              "title": "Crime",
              "slug": "crime"
            },
            {
              "_id": "6a1432f418d4d517c66003cd",
              "title": "Philosophical",
              "slug": "philosophical"
            }
          ],
          "default_genre": {
            "_id": "6a1432f418d4d563cb6003c8",
            "is_active": true,
            "is_archived": false,
            "title": {
              "en": "Drama"
            },
            "slug": "drama"
          },
          "studio_slug": "vplayeddev",
          "scheduled_at": null,
          "is_published": 1,
          "geo_area": [],
          "publish_status": 0,
          "is_watch_later": 0
        }
      }
    ]
  },
  "error": false
}"""

    const val HOME_SEND_REQUEST = """{
  "page": 1,
  "limit": 3,
  "inside_limit": 10
}"""

    const val HOME_REALTIME_RESPONSE = """{
  "statusCode": 200,
  "message": "Data fetched successfully.",
  "status": "success",
  "response": {
    "data": [
      {
        "_id": "6a33d9a8f4074a83f46a090d",
        "is_top_ten": false,
        "title": "Live streaming",
        "type": 6,
        "row_count": 1,
        "count": 6,
        "total": 6,
        "recordsPerPage": 10,
        "currentPage": 1,
        "totalPages": 1,
        "previous": null,
        "next": null,
        "data": [
          {
            "_id": "6accd9b1d49de81f8df1143b",
            "studio_id": "6a1432ca1121914bd8a167f2",
            "studio_slug": "vplayeddev",
            "slug": "test-live-3",
            "is_active": 1,
            "live_id": "GSVPLIVE-XOPvYG",
            "recording_url": null,
            "streaming_status": 0,
            "streaming_watch_url": {
              "hls": "https://1b48f0f4d2af.ap-south-1.playback.live-video.net/api/video/v1/ap-south-1.603425007851.channel.YihMqH6X3EnT.m3u8"
            },
            "is_stream_key_deleted": false,
            "is_cf_enable": false,
            "cf_webhook_update": null,
            "is_premium": 0,
            "page_view": 0,
            "is_published": 1,
            "is_public": 1,
            "is_subscription": false,
            "genre": [
              {
                "_id": "6a1432f418d4d59ab56003c5",
                "title": "Adventure",
                "slug": "adventure"
              }
            ],
            "geo_area": [
              {
                "name": "Afghanistan",
                "code": "AF",
                "flag": "🇦🇫"
              },
              {
                "name": "Aland Islands",
                "code": "AX",
                "flag": "🇦🇽"
              }
            ],
            "facebook_shares": 0,
            "twitter_shares": 0,
            "whatsapp_shares": 0,
            "other_shares": 0,
            "is_archived": 0,
            "is_converted": 0,
            "status": 1,
            "publish_status": 0,
            "prices": [],
            "stream_type": 2,
            "streaming_id": "arn:aws:ivs:ap-south-1:603425007851:channel/YihMqH6X3EnT",
            "streaming_key": "sk_ap-south-1_16Sb4n050lsK_mBJ3JAwTszS98E3IzoZ8sk1xNgIhZv",
            "streaming_type": 2,
            "streaming_url": "rtmps://1b48f0f4d2af.global-contribute.live-video.net:443/app/",
            "new_thumbnail_images": {
              "default": {
                "to": "gudsho-upload-new_thumbnail_images/12-10-2026/1791810102578-blob.jpg",
                "tl": "gudsho-upload-new_thumbnail_images/12-10-2026/1791810104036-blob.jpg",
                "tc": "gudsho-upload-new_thumbnail_images/12-10-2026/1791810104262-blob.jpg",
                "tm": "gudsho-upload-new_thumbnail_images/12-10-2026/1791810103940-blob.jpg",
                "ts": "gudsho-upload-new_thumbnail_images/12-10-2026/1791810104147-blob.jpg",
                "tp": "gudsho-upload-new_thumbnail_images/12-10-2026/1791810104627-blob.jpg",
                "ta": "gudsho-upload-new_thumbnail_images/12-10-2026/1791810104616-blob.jpg",
                "tv": "gudsho-upload-new_thumbnail_images/12-10-2026/1791810104740-blob.jpg"
              },
              "webp": {
                "to": "gudsho-upload-new_thumbnail_images/12-10-2026/1791810100729-blob.webp",
                "tl": "gudsho-upload-new_thumbnail_images/12-10-2026/1791810103995-blob.webp",
                "tc": "gudsho-upload-new_thumbnail_images/12-10-2026/1791810103665-blob.webp",
                "tm": "gudsho-upload-new_thumbnail_images/12-10-2026/1791810101333-blob.webp",
                "ts": "gudsho-upload-new_thumbnail_images/12-10-2026/1791810101496-blob.webp",
                "tp": "gudsho-upload-new_thumbnail_images/12-10-2026/1791810102289-blob.webp",
                "ta": "gudsho-upload-new_thumbnail_images/12-10-2026/1791810101878-blob.webp",
                "tv": "gudsho-upload-new_thumbnail_images/12-10-2026/1791810103132-blob.webp"
              },
              "avif": null
            },
            "geo_type": 2,
            "is_recording": false,
            "monetization": 5,
            "published_on": "2026-10-12T13:01:51.533Z",
            "stream_end_time": null,
            "stream_start_time": null,
            "updated_at": "2026-10-12T13:01:51.821Z",
            "_cacheVersion": 1,
            "studio_name": "VplayedDev",
            "default_title": "title-26",
            "title": "Test Live 3",
            "description": "Test Live 3Test Live 3Test Live 3",
            "_langUpdatedAt": "2026-10-12T13:01:51.821Z",
            "type": 6,
            "category_id": "6a33d9a8f4074a83f46a090d",
            "is_top_ten": false,
            "channel_logo": "images/1779980945139-blob.png",
            "is_user_subscribed": false,
            "is_geo_restricted": true,
            "is_proxy": false,
            "is_release_requested": 0,
            "is_user_notified": false,
            "is_allowed": 0
          },
          {
            "_id": "6accd838d49de81f8df113cf",
            "studio_id": "6a1432ca1121914bd8a167f2",
            "studio_slug": "vplayeddev",
            "slug": "test-live-2",
            "is_active": 1,
            "live_id": "GSVPLIVE-C8y0ha",
            "recording_url": null,
            "streaming_status": 0,
            "streaming_watch_url": {
              "hls": "https://1b48f0f4d2af.ap-south-1.playback.live-video.net/api/video/v1/ap-south-1.603425007851.channel.2M7Lsaryp4FN.m3u8"
            },
            "is_stream_key_deleted": false,
            "is_cf_enable": false,
            "cf_webhook_update": null,
            "is_premium": 0,
            "page_view": 0,
            "is_published": 1,
            "is_public": 1,
            "is_subscription": false,
            "genre": [
              {
                "_id": "6a1432f418d4d59ab56003c5",
                "title": "Adventure",
                "slug": "adventure"
              }
            ],
            "geo_area": [
              {
                "name": "Afghanistan",
                "code": "AF",
                "flag": "🇦🇫"
              }
            ],
            "facebook_shares": 0,
            "twitter_shares": 0,
            "whatsapp_shares": 0,
            "other_shares": 0,
            "is_archived": 0,
            "is_converted": 0,
            "status": 1,
            "publish_status": 0,
            "prices": [],
            "stream_type": 2,
            "streaming_id": "arn:aws:ivs:ap-south-1:603425007851:channel/2M7Lsaryp4FN",
            "streaming_key": "sk_ap-south-1_Xhw92yTXUB4A_1Nct2byB4pcy690ff5xsMKAUxRqZp3",
            "streaming_type": 2,
            "streaming_url": "rtmps://1b48f0f4d2af.global-contribute.live-video.net:443/app/",
            "new_thumbnail_images": {
              "default": {
                "to": "gudsho-upload-new_thumbnail_images/12-10-2026/1791809695935-blob.jpg",
                "tl": "gudsho-upload-new_thumbnail_images/12-10-2026/1791809697208-blob.jpg",
                "tc": "gudsho-upload-new_thumbnail_images/12-10-2026/1791809697564-blob.jpg",
                "tm": "gudsho-upload-new_thumbnail_images/12-10-2026/1791809697501-blob.jpg",
                "ts": "gudsho-upload-new_thumbnail_images/12-10-2026/1791809697437-blob.jpg",
                "tp": "gudsho-upload-new_thumbnail_images/12-10-2026/1791809697987-blob.jpg",
                "ta": "gudsho-upload-new_thumbnail_images/12-10-2026/1791809697978-blob.jpg",
                "tv": "gudsho-upload-new_thumbnail_images/12-10-2026/1791809698105-blob.jpg"
              },
              "webp": {
                "to": "gudsho-upload-new_thumbnail_images/12-10-2026/1791809694375-blob.webp",
                "tl": "gudsho-upload-new_thumbnail_images/12-10-2026/1791809697351-blob.webp",
                "tc": "gudsho-upload-new_thumbnail_images/12-10-2026/1791809697186-blob.webp",
                "tm": "gudsho-upload-new_thumbnail_images/12-10-2026/1791809694794-blob.webp",
                "ts": "gudsho-upload-new_thumbnail_images/12-10-2026/1791809694991-blob.webp",
                "tp": "gudsho-upload-new_thumbnail_images/12-10-2026/1791809695630-blob.webp",
                "ta": "gudsho-upload-new_thumbnail_images/12-10-2026/1791809695476-blob.webp",
                "tv": "gudsho-upload-new_thumbnail_images/12-10-2026/1791809696595-blob.webp"
              },
              "avif": null
            },
            "geo_type": 2,
            "is_recording": false,
            "is_scheduled": true,
            "monetization": 5,
            "published_on": "2026-10-12T12:55:27.759Z",
            "schedule_at": "2026-10-12T18:45:00.000Z",
            "schedule_timezone": "Asia/Kolkata",
            "scheduled_Date": "2026-10-13T00:15:00.000Z",
            "stream_end_time": null,
            "stream_start_time": null,
            "updated_at": "2026-10-12T12:58:11.776Z",
            "_cacheVersion": 1,
            "studio_name": "VplayedDev",
            "default_title": "title-25",
            "title": "Test Live 2",
            "description": "Test Live 2",
            "_langUpdatedAt": "2026-10-12T12:58:11.776Z",
            "type": 6,
            "category_id": "6a33d9a8f4074a83f46a090d",
            "is_top_ten": false,
            "channel_logo": "images/1779980945139-blob.png",
            "is_user_subscribed": false,
            "is_geo_restricted": true,
            "is_proxy": false,
            "is_release_requested": 0,
            "is_user_notified": false,
            "is_allowed": 0
          },
          {
            "_id": "6a9ff5e274ee643a0d8d522f",
            "studio_id": "6a1432ca1121914bd8a167f2",
            "studio_slug": "vplayeddev",
            "slug": "g-wagon",
            "is_active": 1,
            "live_id": "GSVPLIVE-W0HR5n",
            "recording_url": null,
            "streaming_status": 2,
            "streaming_watch_url": {
              "hls": "https://1b48f0f4d2af.ap-south-1.playback.live-video.net/api/video/v1/ap-south-1.603425007851.channel.ld3II9AItPds.m3u8"
            },
            "is_stream_key_deleted": true,
            "is_cf_enable": false,
            "cf_webhook_update": "2026-09-10T12:36:16.000Z",
            "is_premium": 0,
            "page_view": 0,
            "is_published": 1,
            "is_public": 1,
            "is_subscription": false,
            "genre": [
              {
                "_id": "6a1432f418d4d53c2c6003c4",
                "title": "Action",
                "slug": "action"
              }
            ],
            "geo_area": [
              {
                "name": "Afghanistan",
                "code": "AF",
                "flag": "🇦🇫"
              },
              {
                "name": "Aland Islands",
                "code": "AX",
                "flag": "🇦🇽"
              }
            ],
            "facebook_shares": 0,
            "twitter_shares": 0,
            "whatsapp_shares": 0,
            "other_shares": 0,
            "is_archived": 0,
            "is_converted": 0,
            "status": 1,
            "publish_status": 4,
            "prices": [],
            "stream_type": 2,
            "streaming_id": "arn:aws:ivs:ap-south-1:603425007851:channel/ld3II9AItPds",
            "streaming_key": "sk_ap-south-1_0Zo4A5ImdPEp_F2LdbDtBcpAD3h3vl7d5ylaGsIvgn6",
            "streaming_type": 2,
            "streaming_url": "rtmps://1b48f0f4d2af.global-contribute.live-video.net:443/app/",
            "new_thumbnail_images": {
              "default": {
                "to": "gudsho-upload-new_thumbnail_images/10-09-2026/1789041299183-blob.jpg",
                "tl": "gudsho-upload-new_thumbnail_images/10-09-2026/1789041300630-blob.jpg",
                "tc": "gudsho-upload-new_thumbnail_images/10-09-2026/1789041300571-blob.jpg",
                "tm": "gudsho-upload-new_thumbnail_images/10-09-2026/1789041299739-blob.jpg",
                "ts": "gudsho-upload-new_thumbnail_images/10-09-2026/1789041299670-blob.jpg",
                "tp": "gudsho-upload-new_thumbnail_images/10-09-2026/1789041300195-blob.jpg",
                "ta": "gudsho-upload-new_thumbnail_images/10-09-2026/1789041300124-blob.jpg",
                "tv": "gudsho-upload-new_thumbnail_images/10-09-2026/1789041300564-blob.jpg"
              },
              "webp": {
                "to": "gudsho-upload-new_thumbnail_images/10-09-2026/1789041296105-blob.webp",
                "tl": "gudsho-upload-new_thumbnail_images/10-09-2026/1789041298698-blob.webp",
                "tc": "gudsho-upload-new_thumbnail_images/10-09-2026/1789041298664-blob.webp",
                "tm": "gudsho-upload-new_thumbnail_images/10-09-2026/1789041296615-blob.webp",
                "ts": "gudsho-upload-new_thumbnail_images/10-09-2026/1789041296492-blob.webp",
                "tp": "gudsho-upload-new_thumbnail_images/10-09-2026/1789041297269-blob.webp",
                "ta": "gudsho-upload-new_thumbnail_images/10-09-2026/1789041297059-blob.webp",
                "tv": "gudsho-upload-new_thumbnail_images/10-09-2026/1789041298011-blob.webp"
              },
              "avif": null
            },
            "geo_type": 2,
            "is_recording": false,
            "monetization": 5,
            "published_on": "2026-09-10T11:55:13.591Z",
            "stream_end_time": "2026-09-10T12:36:16.000Z",
            "stream_start_time": "2026-09-10T12:29:20.000Z",
            "updated_at": "2026-10-12T12:51:51.324Z",
            "_cacheVersion": 1,
            "studio_name": "VplayedDev",
            "default_title": "title-15",
            "title": "G wagon",
            "description": "G wagon test",
            "_langUpdatedAt": "2026-10-12T12:51:51.324Z",
            "type": 6,
            "category_id": "6a33d9a8f4074a83f46a090d",
            "is_top_ten": false,
            "channel_logo": "images/1779980945139-blob.png",
            "is_user_subscribed": false,
            "is_geo_restricted": true,
            "is_proxy": false,
            "is_release_requested": 0,
            "is_user_notified": false,
            "is_allowed": 0
          },
          {
            "_id": "6acc8dc0d49de81f8df11195",
            "studio_id": "6a1432ca1121914bd8a167f2",
            "studio_slug": "vplayeddev",
            "slug": "test-streaming",
            "is_active": 1,
            "live_id": "GSVPLIVE-InzArT",
            "recording_url": null,
            "streaming_status": 0,
            "streaming_watch_url": {
              "hls": "https://1b48f0f4d2af.ap-south-1.playback.live-video.net/api/video/v1/ap-south-1.603425007851.channel.IOujQiAWb1P1.m3u8"
            },
            "is_stream_key_deleted": false,
            "is_cf_enable": false,
            "cf_webhook_update": null,
            "is_premium": 0,
            "page_view": 0,
            "is_published": 1,
            "is_public": 1,
            "is_subscription": true,
            "genre": [
              {
                "_id": "6a1432f418d4d53c2c6003c4",
                "title": "Action",
                "slug": "action"
              }
            ],
            "geo_area": [],
            "facebook_shares": 0,
            "twitter_shares": 0,
            "whatsapp_shares": 0,
            "other_shares": 0,
            "is_archived": 0,
            "is_converted": 0,
            "status": 1,
            "publish_status": 1,
            "prices": [],
            "stream_type": 2,
            "streaming_id": "arn:aws:ivs:ap-south-1:603425007851:channel/IOujQiAWb1P1",
            "streaming_key": "sk_ap-south-1_YUgyrvhgBjkr_MUyIZJm8l3xj5xm14iqnT457uosC6A",
            "streaming_type": 2,
            "streaming_url": "rtmps://1b48f0f4d2af.global-contribute.live-video.net:443/app/",
            "new_thumbnail_images": {
              "default": {
                "to": "gudsho-upload-new_thumbnail_images/12-10-2026/1791791072236-blob.jpg",
                "tl": "gudsho-upload-new_thumbnail_images/12-10-2026/1791791074462-blob.jpg",
                "tc": "gudsho-upload-new_thumbnail_images/12-10-2026/1791791074561-blob.jpg",
                "tm": "gudsho-upload-new_thumbnail_images/12-10-2026/1791791073833-blob.jpg",
                "ts": "gudsho-upload-new_thumbnail_images/12-10-2026/1791791074296-blob.jpg",
                "tp": "gudsho-upload-new_thumbnail_images/12-10-2026/1791791074934-blob.jpg",
                "ta": "gudsho-upload-new_thumbnail_images/12-10-2026/1791791074897-blob.jpg",
                "tv": "gudsho-upload-new_thumbnail_images/12-10-2026/1791791075367-blob.jpg"
              },
              "webp": {
                "to": "gudsho-upload-new_thumbnail_images/12-10-2026/1791791068392-blob.webp",
                "tl": "gudsho-upload-new_thumbnail_images/12-10-2026/1791791074923-blob.webp",
                "tc": "gudsho-upload-new_thumbnail_images/12-10-2026/1791791072823-blob.webp",
                "tm": "gudsho-upload-new_thumbnail_images/12-10-2026/1791791069176-blob.webp",
                "ts": "gudsho-upload-new_thumbnail_images/12-10-2026/1791791069086-blob.webp",
                "tp": "gudsho-upload-new_thumbnail_images/12-10-2026/1791791070589-blob.webp",
                "ta": "gudsho-upload-new_thumbnail_images/12-10-2026/1791791069909-blob.webp",
                "tv": "gudsho-upload-new_thumbnail_images/12-10-2026/1791791071555-blob.webp"
              },
              "avif": null
            },
            "geo_type": 1,
            "is_recording": false,
            "is_scheduled": true,
            "monetization": 8,
            "published_on": "2026-10-12T07:39:01.030Z",
            "schedule_at": "2026-10-12T20:45:00.000Z",
            "schedule_timezone": "Asia/Kolkata",
            "scheduled_Date": "2026-10-13T02:15:00.000Z",
            "stream_end_time": null,
            "stream_start_time": null,
            "updated_at": "2026-10-12T09:03:04.452Z",
            "_cacheVersion": 1,
            "studio_name": "VplayedDev",
            "default_title": "title-23",
            "title": "Test Streaming",
            "description": "Test StreamingTest StreamingTest StreamingTest StreamingTest Streaming",
            "_langUpdatedAt": "2026-10-12T09:03:04.452Z",
            "type": 6,
            "category_id": "6a33d9a8f4074a83f46a090d",
            "is_top_ten": false,
            "channel_logo": "images/1779980945139-blob.png",
            "is_user_subscribed": false,
            "is_geo_restricted": false,
            "is_proxy": false,
            "is_release_requested": 0,
            "is_user_notified": false,
            "is_allowed": 0
          },
          {
            "_id": "6acc8eaad49de81f8df111c5",
            "studio_id": "6a1432ca1121914bd8a167f2",
            "studio_slug": "vplayeddev",
            "slug": "streaming-right-now",
            "is_active": 1,
            "live_id": "GSVPLIVE-MT7Oxa",
            "recording_url": null,
            "streaming_status": 0,
            "streaming_watch_url": {
              "hls": "https://1b48f0f4d2af.ap-south-1.playback.live-video.net/api/video/v1/ap-south-1.603425007851.channel.q64yprTz2DHZ.m3u8"
            },
            "is_stream_key_deleted": false,
            "is_cf_enable": false,
            "cf_webhook_update": null,
            "is_premium": 0,
            "page_view": 0,
            "is_published": 1,
            "is_public": 1,
            "is_subscription": true,
            "genre": [
              {
                "_id": "6a1432f418d4d59ab56003c5",
                "title": "Adventure",
                "slug": "adventure"
              }
            ],
            "geo_area": [],
            "facebook_shares": 0,
            "twitter_shares": 0,
            "whatsapp_shares": 0,
            "other_shares": 0,
            "is_archived": 0,
            "is_converted": 0,
            "status": 1,
            "publish_status": 0,
            "prices": [
              {
                "currency": "INR",
                "currency_type": 1,
                "amount": 100,
                "is_active": true,
                "is_archived": false,
                "coin_value": null,
                "_id": "6acc8f95d49de81f8df111fc"
              },
              {
                "currency": "USD",
                "currency_type": 2,
                "amount": 1,
                "is_active": true,
                "is_archived": false,
                "coin_value": null,
                "_id": "6acc8f95d49de81f8df111fd"
              }
            ],
            "stream_type": 2,
            "streaming_id": "arn:aws:ivs:ap-south-1:603425007851:channel/q64yprTz2DHZ",
            "streaming_key": "sk_ap-south-1_r1RjXxmOkBS9_kAyawYnWzFTAQKE3kvFFFCmhgHqR2q",
            "streaming_type": 2,
            "streaming_url": "rtmps://1b48f0f4d2af.global-contribute.live-video.net:443/app/",
            "new_thumbnail_images": {
              "default": {
                "to": "gudsho-upload-new_thumbnail_images/12-10-2026/1791790987145-blob.jpg",
                "tl": "gudsho-upload-new_thumbnail_images/12-10-2026/1791790989383-blob.jpg",
                "tc": "gudsho-upload-new_thumbnail_images/12-10-2026/1791790989933-blob.jpg",
                "tm": "gudsho-upload-new_thumbnail_images/12-10-2026/1791790989682-blob.jpg",
                "ts": "gudsho-upload-new_thumbnail_images/12-10-2026/1791790989802-blob.jpg",
                "tp": "gudsho-upload-new_thumbnail_images/12-10-2026/1791790990207-blob.jpg",
                "ta": "gudsho-upload-new_thumbnail_images/12-10-2026/1791790990237-blob.jpg"
              }
            },
            "streaming_duration_type": 1,
            "is_blocked": 0,
            "bg_card_color": "#FFA0A0",
            "categories": [],
            "name": "Action Hub",
            "slug": "action-hub",
            "is_archived": 0,
            "status": 3,
            "sho_type": 3,
            "created_at": "2026-05-25T12:12:25.316Z",
            "updated_at": "2026-10-06T13:25:21.083Z",
            "__v": 0,
            "aadharcard_image": "",
            "aadharcard_name": "",
            "aadharcard_type": "",
            "account_number": "",
            "gtin_number": "",
            "ifsc_code": "",
            "is_published": 1,
            "pancard_image": "",
            "pancard_name": "",
            "pancard_type": "",
            "is_public": 1,
            "profile_picture": "images/1779787613207-blob.png",
            "_langUpdatedAt": "2026-10-06T13:25:21.083Z",
            "_cacheVersion": 1,
            "type": 4,
            "category_id": "6acc68178a16e2820c575a42",
            "is_top_ten": false
          },
          {
            "_id": "6a33a54b603589fb26292587",
            "profile_picture_avif": null,
            "otp_hit_count": 0,
            "is_email_verified": 1,
            "is_phone_verified": 1,
            "follow_count": 1,
            "is_active": 1,
            "streaming_duration_type": 1,
            "is_blocked": 0,
            "bg_card_color": "#FFA0A0",
            "categories": [],
            "address": "Vellore",
            "city": "Vellore",
            "state": "Arunachal Pradesh",
            "country": "India",
            "owner_name": "Sedhupathi M",
            "email": "msedhugreat@gmail.com",
            "name": "Sedhu",
            "zipcode": "632107",
            "is_published": 1,
            "is_public": 1,
            "profile_picture": "images/1781769542547-blob.png",
            "slug": "sedhu",
            "is_archived": 0,
            "status": 3,
            "sho_type": 3,
            "created_at": "2026-06-18T07:59:07.649Z",
            "updated_at": "2026-06-23T04:43:43.139Z",
            "__v": 0,
            "_langUpdatedAt": "2026-06-23T04:43:43.139Z",
            "_cacheVersion": 1,
            "type": 4,
            "category_id": "6acc68178a16e2820c575a42",
            "is_top_ten": false
          },
          {
            "_id": "6a69b22ac989603bd29685d9",
            "profile_picture_avif": null,
            "otp_hit_count": 0,
            "is_email_verified": 1,
            "is_phone_verified": 1,
            "follow_count": 0,
            "is_active": 1,
            "streaming_duration_type": 1,
            "is_blocked": 0,
            "bg_card_color": "#FFA0A0",
            "categories": [],
            "address": "willow swuare",
            "city": "chennai",
            "state": "Tamil Nadu",
            "country": "India",
            "owner_name": "karthik rajendiren",
            "email": "karthik1@yopmail.com",
            "phone": "7845459819",
            "name": "rk dev test",
            "zipcode": "600032",
            "is_published": 1,
            "is_public": 1,
            "profile_picture": "images/1785311785665-blob.png",
            "slug": "rk-dev-test",
            "is_archived": 0,
            "status": 3,
            "sho_type": 3,
            "created_at": "2026-07-29T07:56:26.180Z",
            "updated_at": "2026-07-29T07:56:26.180Z",
            "__v": 0,
            "_langUpdatedAt": "2026-07-29T07:56:26.180Z",
            "_cacheVersion": 1,
            "type": 4,
            "category_id": "6acc68178a16e2820c575a42",
            "is_top_ten": false
          },
          {
            "_id": "6a69b688c989606382968674",
            "profile_picture_avif": null,
            "otp_hit_count": 0,
            "is_email_verified": 1,
            "is_phone_verified": 1,
            "follow_count": 3,
            "is_active": 1,
            "streaming_duration_type": 1,
            "is_blocked": 0,
            "bg_card_color": "#FFA0A0",
            "categories": [],
            "address": "willow swuare",
            "city": "chennai",
            "state": "Tamil Nadu",
            "country": "India",
            "owner_name": "Karthik Rajendiran",
            "email": "karthik.r@contus.in",
            "name": "kdevtest",
            "zipcode": "600032",
            "is_published": 1,
            "is_public": 1,
            "profile_picture": "images/1785312903904-blob.png",
            "slug": "kdevtest",
            "is_archived": 0,
            "status": 3,
            "sho_type": 3,
            "created_at": "2026-07-29T08:15:04.262Z",
            "updated_at": "2026-08-01T06:39:29.425Z",
            "__v": 0,
            "_langUpdatedAt": "2026-08-01T06:39:29.425Z",
            "_cacheVersion": 1,
            "type": 4,
            "category_id": "6acc68178a16e2820c575a42",
            "is_top_ten": false
          },
          {
            "_id": "6abcb026a4f17b791caaeafe",
            "profile_picture_avif": null,
            "otp_hit_count": 0,
            "is_email_verified": 1,
            "is_phone_verified": 1,
            "follow_count": 0,
            "is_active": 1,
            "streaming_duration_type": 1,
            "is_blocked": 0,
            "categories": [],
            "streaming_duration": 20,
            "is_published": 1,
            "is_public": 1,
            "is_archived": 0,
            "status": 3,
            "sho_type": 3,
            "created_at": "2026-09-30T06:45:58.519Z",
            "updated_at": "2026-10-13T11:35:40.994Z",
            "__v": 0,
            "admin_block": false,
            "is_verified": true,
            "_cacheVersion": 1,
            "bg_card_color": "#1F7A44",
            "owner_name": "Raj Kiran Gopalakrishnan",
            "email": "rajkiran.g@contus.in",
            "name": "RK Entertainment checking channel",
            "profile_picture": "images/1790750757666-blob.png",
            "slug": "rk-entertainment",
            "aadharcard_image": "",
            "aadharcard_name": "",
            "aadharcard_type": "",
            "account_number": "",
            "gtin_number": "",
            "ifsc_code": "",
            "pancard_image": "",
            "pancard_name": "",
            "pancard_type": "",
            "_langUpdatedAt": "2026-10-13T11:35:40.994Z",
            "type": 4,
            "category_id": "6acc68178a16e2820c575a42",
            "is_top_ten": false
          }
        ]
      },
      {
        "_id": "6a7eb9b5a1330f8a8f624560",
        "is_top_ten": false,
        "title": "Fast",
        "type": 9,
        "row_count": 1,
        "count": 1,
        "total": 1,
        "recordsPerPage": 10,
        "currentPage": 1,
        "totalPages": 1,
        "previous": null,
        "next": null,
        "data": []
      }
    ],
    "total": 20,
    "recordsPerPage": 3,
    "currentPage": 1,
    "totalPages": 7,
    "previous": null,
    "next": 2
  },
  "error": false
}"""
}
