package com.inovixlabs.tazkiyah.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Logic behind Intent.isYouTubeEscape() (the extension only feeds it dataString + package). */
class YouTubeEscapeGuardTest {
    @Test
    fun blocksYoutubeDotCom() {
        assertTrue(isYouTubeEscape("https://youtube.com/watch?v=abcdefghijk", null))
        assertTrue(isYouTubeEscape("https://www.youtube.com/watch?v=abcdefghijk", null))
        assertTrue(isYouTubeEscape("HTTPS://WWW.YOUTUBE.COM/", null))
    }

    @Test
    fun blocksMobileAndMusicHosts() {
        assertTrue(isYouTubeEscape("https://m.youtube.com/watch?v=abcdefghijk", null))
        assertTrue(isYouTubeEscape("https://music.youtube.com/", null))
    }

    @Test
    fun blocksShortLinks() {
        assertTrue(isYouTubeEscape("https://youtu.be/abcdefghijk", null))
        assertTrue(isYouTubeEscape("http://youtu.be/abcdefghijk?t=10", null))
    }

    @Test
    fun blocksVndYoutubeScheme() {
        assertTrue(isYouTubeEscape("vnd.youtube:abcdefghijk", null))
        assertTrue(isYouTubeEscape("vnd.youtube://abcdefghijk", null))
    }

    @Test
    fun blocksYoutubeAppPackageWithoutData() {
        assertTrue(isYouTubeEscape(null, YOUTUBE_APP_PACKAGE))
    }

    @Test
    fun allowsNormalHttpsUrls() {
        assertFalse(isYouTubeEscape("https://archive.org/details/lecture", null))
        assertFalse(isYouTubeEscape("https://notyoutube.com/watch", null))
        assertFalse(isYouTubeEscape("https://example.com/?u=youtube.com", null))
        assertFalse(isYouTubeEscape(null, null))
    }
}
