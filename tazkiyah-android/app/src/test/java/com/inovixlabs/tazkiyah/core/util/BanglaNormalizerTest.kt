package com.inovixlabs.tazkiyah.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BanglaNormalizerTest {

    @Test
    fun yaWithNukta_matchesPrecomposedYa() {
        val precomposed = "দো\u09DFা"        // YA as one code point
        val decomposed = "দোয\u09BCা"   // য + nukta
        assertEquals(BanglaNormalizer.normalize(precomposed), BanglaNormalizer.normalize(decomposed))
    }

    @Test
    fun rraAndRhaBothEncodings_areUnified() {
        assertEquals(BanglaNormalizer.normalize("প\u09DCা"), BanglaNormalizer.normalize("পড\u09BCা"))
        assertEquals(BanglaNormalizer.normalize("আ\u09DDা"), BanglaNormalizer.normalize("আঢ\u09BCা"))
    }

    @Test
    fun zeroWidthJoiners_areRemoved() {
        assertEquals(BanglaNormalizer.normalize("র্যাব"), BanglaNormalizer.normalize("র্\u200Dযাব"))
        assertEquals("কিতাব", BanglaNormalizer.normalize("কি\u200Cতা\u200Dব"))
    }

    @Test
    fun legacyKhandaTa_becomesKhandaTa() {
        assertEquals("হঠা\u09CE", BanglaNormalizer.normalize("হঠাত্\u200D"))
    }

    @Test
    fun caseWhitespaceAndDigits_areNormalized() {
        assertEquals("jakaria masud 2025", BanglaNormalizer.normalize("  Jakaria   MASUD ২০২৫ "))
    }

    @Test
    fun ftsQuery_isPrefixPerToken_andIgnoresPunctuation() {
        assertEquals("তাওবার* শর্ত*", BanglaNormalizer.toFtsQuery("তাওবার \"শর্ত\" —"))
        assertNull(BanglaNormalizer.toFtsQuery("  *** "))
    }

    @Test
    fun vowelSignsStayInsideTokens() {
        assertEquals(listOf("সালাতে", "খুশু"), BanglaNormalizer.tokens("সালাতে খুশু।"))
    }
}
