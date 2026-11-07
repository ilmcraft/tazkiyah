package com.inovixlabs.tazkiyah.core.util

import java.text.Normalizer
import java.util.Locale

/**
 * Makes Bengali text comparable for search (docs/PLAN.md, "বাংলা সার্চ"):
 * Unicode NFC, invisible joiners removed, and the two encodings of YA, RRA and RHA (single code point vs.
 * letter + nukta) unified. The same function is applied to indexed text and to queries.
 */
object BanglaNormalizer {
    private val invisible = Regex("[\u200B\u200C\u200D\u2060\uFEFF\u00AD]")
    private val whitespace = Regex("\\s+")
    private val nonWord = Regex("[^\\p{L}\\p{M}\\p{N}]+")

    fun normalize(input: String): String {
        var s = Normalizer.normalize(input, Normalizer.Form.NFC)
        // Legacy khanda-ta (TA + HASANTA + ZWJ) becomes U+09CE; must run before joiners are stripped.
        s = s.replace("ত্\u200D", "\u09CE")
        s = s.replace(invisible, "")
        // NFC decomposes these three (composition exclusions); fold both spellings to one code point.
        s = s.replace("য\u09BC", "\u09DF")
            .replace("ড\u09BC", "\u09DC")
            .replace("ঢ\u09BC", "\u09DD")
        s = s.toAsciiDigits().lowercase(Locale.ROOT)
        return s.replace(whitespace, " ").trim()
    }

    fun tokens(input: String): List<String> =
        normalize(input).split(nonWord).filter { it.isNotBlank() }

    /** FTS4 MATCH expression: every token must match as a prefix. Null when there is nothing to search. */
    fun toFtsQuery(input: String): String? =
        tokens(input).takeIf { it.isNotEmpty() }?.joinToString(" ") { "$it*" }
}
