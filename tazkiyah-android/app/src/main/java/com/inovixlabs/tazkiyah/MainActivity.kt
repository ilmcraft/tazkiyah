package com.inovixlabs.tazkiyah

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.inovixlabs.tazkiyah.core.util.isYouTubeEscape
import com.inovixlabs.tazkiyah.ui.TazkiyahRoot
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val messages = Channel<String>(Channel.BUFFERED)
    private val openPlayerRequests = Channel<Unit>(Channel.CONFLATED)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            TazkiyahRoot(
                messages = messages.receiveAsFlow(),
                openPlayerRequests = openPlayerRequests.receiveAsFlow(),
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_PLAYER) openPlayerRequests.trySend(Unit)
    }

    // Layer 4 of the YouTube lock (docs/PLAN.md): nothing may leave the app for YouTube.
    override fun startActivity(intent: Intent, options: Bundle?) {
        if (intent.isYouTubeEscape()) {
            messages.trySend(getString(R.string.focus_mode_blocked))
            return
        }
        super.startActivity(intent, options)
    }

    override fun startActivity(intent: Intent) = startActivity(intent, null)

    companion object {
        const val ACTION_OPEN_PLAYER = "com.inovixlabs.tazkiyah.action.OPEN_PLAYER"
    }
}
