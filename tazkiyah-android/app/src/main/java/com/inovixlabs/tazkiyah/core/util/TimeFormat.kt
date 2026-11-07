package com.inovixlabs.tazkiyah.core.util

import java.time.LocalDate
import java.util.Locale

/** 75 -> "১:১৫", 3725 -> "১:০২:০৫". */
fun formatClock(totalSec: Long): String {
    val s = totalSec.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    val text = if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.US, "%d:%02d", m, sec)
    return text.toBanglaDigits()
}

/** Parses an ISO date (YYYY-MM-DD); null when missing or malformed. */
fun parseIsoDate(value: String?): LocalDate? =
    value?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

/** "2026-11-07" -> "৭ নভেম্বর ২০২৬" using the month names from strings.xml. */
fun formatBanglaDate(value: String?, monthNames: Array<String>): String? {
    val date = parseIsoDate(value) ?: return null
    val month = monthNames.getOrNull(date.monthValue - 1) ?: return null
    return "${date.dayOfMonth.toBanglaDigits()} $month ${date.year.toBanglaDigits()}"
}
