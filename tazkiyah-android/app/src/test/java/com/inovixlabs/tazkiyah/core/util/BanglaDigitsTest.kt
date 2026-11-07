package com.inovixlabs.tazkiyah.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class BanglaDigitsTest {
    @Test
    fun convertsAllDigits() {
        assertEquals("০১২৩৪৫৬৭৮৯", "0123456789".toBanglaDigits())
    }

    @Test
    fun keepsOtherCharacters() {
        assertEquals("পর্ব ১২: ৩:০৫", "পর্ব 12: 3:05".toBanglaDigits())
        assertEquals("১০৫", 105.toBanglaDigits())
        assertEquals("৪২", 42L.toBanglaDigits())
    }

    @Test
    fun roundTripsToAscii() {
        assertEquals("2026-11-07", "২০২৬-১১-০৭".toAsciiDigits())
    }

    @Test
    fun clockFormat_usesBanglaDigits() {
        assertEquals("১:১৫", formatClock(75))
        assertEquals("১:০২:০৫", formatClock(3725))
        assertEquals("০:০০", formatClock(-5))
    }

    @Test
    fun banglaDate() {
        val months = arrayOf("জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন", "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর")
        assertEquals("৭ নভেম্বর ২০২৬", formatBanglaDate("2026-11-07", months))
        assertEquals(null, formatBanglaDate("not a date", months))
    }
}
