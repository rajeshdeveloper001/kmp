package com.vplayed.shared.core.serialization

import kotlinx.serialization.json.Json

/**
 * The one JSON configuration for VPlayed APIs (same settings as AndroidJP's `NetworkModule.provideJson`):
 * unknown keys are ignored, and a `null` where a model has a default falls back to the default.
 * Fields whose JSON type varies are declared as `JsonElement`/`JsonPrimitive` and read with the
 * helpers in `Lenient.kt`.
 */
val SharedJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}
