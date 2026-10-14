package com.vplayed.shared.core.serialization

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LenientTest {

    private fun parse(json: String): JsonElement = SharedJson.parseToJsonElement(json)

    @Test
    fun givenNumbersAsNumbersOrStrings_whenReadingLeniently_thenSameValue() {
        assertEquals(125, parse("125").lenientInt())
        assertEquals(267, parse("\"267.72\"").lenientInt())
        assertEquals(267.72, parse("\"267.72\"").lenientDouble())
        assertEquals(42L, parse("\" 42 \"").lenientLong())
    }

    @Test
    fun givenMalformedNumbers_whenReadingLeniently_thenNull() {
        listOf("\"abc\"", "null", "{}", "[]", "\"\"", "\"NaN\"").forEach { assertNull(parse(it).lenientDouble(), it) }
        assertNull((null as JsonElement?).lenientInt())
        assertNull(parse("1e12").lenientInt()) // out of Int range
    }

    @Test
    fun givenFlagShapes_whenReadingLeniently_thenBoolean() {
        mapOf("true" to true, "false" to false, "1" to true, "0" to false, "\"1\"" to true, "\"TRUE\"" to true, "\"no\"" to null, "2" to null)
            .forEach { (json, expected) -> assertEquals(expected, parse(json).lenientBool(), json) }
    }

    @Test
    fun givenStrings_whenReadingLeniently_thenTrimmedOrNull() {
        assertEquals("Action", parse("\"  Action \"").lenientString())
        assertEquals("12", parse("12").lenientString())
        assertNull(parse("\"   \"").lenientString())
        assertNull(JsonNull.lenientString())
    }

    @Test
    fun givenLocalizedMap_whenReadingText_thenPreferredLanguageThenEnglishThenFirstByKey() {
        val map = parse("""{"pt": "Ação", "en": "Action", "ar": "أكشن"}""")

        assertEquals("أكشن", map.localizedText("ar"))
        assertEquals("Action", map.localizedText("ta"))
        assertEquals("Action", map.localizedText())
        assertEquals("أكشن", parse("""{"pt": "Ação", "ar": "أكشن"}""").localizedText("ta")) // "ar" < "pt"
        assertEquals("Ação", parse("""{"pt": "Ação", "ar": "  "}""").localizedText()) // blanks skipped
    }

    @Test
    fun givenPlainOrInvalidText_whenReadingLocalized_thenStringOrNull() {
        assertEquals("Trending", parse("\"Trending\"").localizedText("ar"))
        assertNull(JsonPrimitive(5).localizedText())
        assertNull(parse("[]").localizedText())
    }

    @Serializable
    private data class Item(val id: String, val count: Int = 0)

    @Serializable
    private data class Page(@Serializable(with = LossyListSerializer::class) val data: List<Item> = emptyList())

    @Test
    fun givenArrayWithBadElements_whenDecodingLossyList_thenBadElementsAreDropped() {
        val page = SharedJson.decodeFromString(Page.serializer(), """{"data": [{"id": "a"}, {"count": 3}, "oops", {"id": "b", "count": 2}]}""")

        assertEquals(listOf(Item("a"), Item("b", 2)), page.data)
    }

    @Test
    fun givenNonArray_whenDecodingLossyList_thenEmpty() {
        assertEquals(emptyList(), SharedJson.decodeFromString(Page.serializer(), """{"data": {"id": "a"}}""").data)
        assertEquals(emptyList(), SharedJson.decodeFromString(Page.serializer(), """{"other": 1}""").data)
    }
}
