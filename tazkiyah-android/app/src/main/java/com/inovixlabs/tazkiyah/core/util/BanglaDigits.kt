package com.inovixlabs.tazkiyah.core.util

private const val BANGLA_ZERO = '০'

/** Replaces ASCII digits with Bengali digits (০–৯); everything else is kept as is. */
fun String.toBanglaDigits(): String = buildString(this@toBanglaDigits.length) {
    for (c in this@toBanglaDigits) append(if (c in '0'..'9') BANGLA_ZERO + (c - '0') else c)
}

fun Int.toBanglaDigits(): String = toString().toBanglaDigits()

fun Long.toBanglaDigits(): String = toString().toBanglaDigits()

/** Replaces Bengali digits with ASCII digits, e.g. for search queries. */
fun String.toAsciiDigits(): String = buildString(this@toAsciiDigits.length) {
    for (c in this@toAsciiDigits) append(if (c in BANGLA_ZERO..'৯') '0' + (c - BANGLA_ZERO) else c)
}
