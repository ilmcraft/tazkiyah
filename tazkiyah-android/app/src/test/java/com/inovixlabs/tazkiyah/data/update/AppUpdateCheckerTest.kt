package com.inovixlabs.tazkiyah.data.update

import com.inovixlabs.tazkiyah.data.remote.ContentJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {

    private fun release(tag: String, draft: Boolean = false, prerelease: Boolean = false) =
        GitHubRelease(tag, "https://github.com/o/r/releases/tag/$tag", draft, prerelease)

    @Test
    fun picksTheNewestPublishedAppTag() {
        val latest = AppUpdateChecker.latestAppRelease(
            listOf(
                release("app-v1.9.0"),
                release("app-v1.10.0"),
                release("app-v2.0.0", draft = true),
                release("app-v1.11.0-beta", prerelease = true),
                release("content-2026-11"),
                release("app-vX"),
            ),
        )
        assertEquals(AppRelease("1.10.0", "https://github.com/o/r/releases/tag/app-v1.10.0"), latest)
    }

    @Test
    fun noAppReleaseGivesNull() {
        assertNull(AppUpdateChecker.latestAppRelease(listOf(release("v1.0"), release("app-v2.0.0", draft = true))))
    }

    @Test
    fun versionsCompareNumerically() {
        assertTrue(AppUpdateChecker.isNewer("1.10.0", "1.9.2"))
        assertTrue(AppUpdateChecker.isNewer("1.1.1", "1.1"))
        assertFalse(AppUpdateChecker.isNewer("1.1.0", "1.1"))
        assertFalse(AppUpdateChecker.isNewer("1.0.9", "1.1.0"))
        assertFalse(AppUpdateChecker.isNewer("garbage", "1.0.0"))
    }

    @Test
    fun parsesGitHubApiJson() {
        val json = """[{"tag_name":"app-v1.1.0","html_url":"https://x/1.1.0","draft":false,"prerelease":false,"assets":[]}]"""
        val releases = ContentJson.decodeFromString<List<GitHubRelease>>(json)
        assertEquals(AppRelease("1.1.0", "https://x/1.1.0"), AppUpdateChecker.latestAppRelease(releases))
    }
}
