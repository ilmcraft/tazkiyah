package com.inovixlabs.tazkiyah.core.util

import android.content.Intent

const val YOUTUBE_APP_PACKAGE = "com.google.android.youtube"

private val blockedHosts = setOf(
    "youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be", "music.youtube.com",
    "youtube-nocookie.com", "www.youtube-nocookie.com",
)
private val uriHead = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):(?://(?:[^/?#@]*@)?([^/?#:]*))?")

/**
 * True when [url]/[targetPackage] would leave the app for YouTube (docs/PLAN.md, "স্তর ৪ — ইন্টেন্ট গার্ড").
 * Pure so it can be unit-tested without Android.
 */
fun isYouTubeEscape(url: String?, targetPackage: String?): Boolean {
    if (targetPackage == YOUTUBE_APP_PACKAGE) return true
    val match = url?.trim()?.let { uriHead.find(it) } ?: return false
    val scheme = match.groupValues[1].lowercase()
    val host = match.groupValues[2].lowercase()
    if (scheme == "vnd.youtube" || scheme == "youtube") return true
    return host in blockedHosts || host.endsWith(".youtube.com")
}

fun Intent.isYouTubeEscape(): Boolean =
    isYouTubeEscape(dataString, `package` ?: component?.packageName)
