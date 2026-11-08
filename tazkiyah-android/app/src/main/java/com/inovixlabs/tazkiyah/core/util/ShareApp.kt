package com.inovixlabs.tazkiyah.core.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.inovixlabs.tazkiyah.BuildConfig
import com.inovixlabs.tazkiyah.R

/**
 * Shares the app itself — the download page, never a YouTube link (CLAUDE.md: no way out to
 * YouTube). Returns false when no app on the phone can share text.
 */
fun shareApp(context: Context): Boolean {
    val text = context.getString(R.string.share_app_text, BuildConfig.DOWNLOAD_PAGE_URL)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.app_name))
        .putExtra(Intent.EXTRA_TEXT, text)
    val chooser = Intent.createChooser(send, context.getString(R.string.share_app))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(chooser)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
