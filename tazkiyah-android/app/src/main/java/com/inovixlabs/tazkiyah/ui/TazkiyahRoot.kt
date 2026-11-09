package com.inovixlabs.tazkiyah.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.inovixlabs.tazkiyah.R
import com.inovixlabs.tazkiyah.core.designsystem.AppFont
import com.inovixlabs.tazkiyah.core.designsystem.LocalShowMessage
import com.inovixlabs.tazkiyah.core.designsystem.TazkiyahTheme
import com.inovixlabs.tazkiyah.core.designsystem.ThemeMode
import com.inovixlabs.tazkiyah.core.designsystem.isAppInDarkTheme
import com.inovixlabs.tazkiyah.feature.about.AboutScreen
import com.inovixlabs.tazkiyah.feature.categories.CategoriesScreen
import com.inovixlabs.tazkiyah.feature.category.CategoryScreen
import com.inovixlabs.tazkiyah.feature.home.HomeScreen
import com.inovixlabs.tazkiyah.feature.library.LibraryScreen
import com.inovixlabs.tazkiyah.feature.player.DownloadSection
import com.inovixlabs.tazkiyah.feature.player.FullPlayerScreen
import com.inovixlabs.tazkiyah.feature.player.MiniPlayer
import com.inovixlabs.tazkiyah.feature.player.PlayerViewModel
import com.inovixlabs.tazkiyah.feature.search.SearchScreen
import com.inovixlabs.tazkiyah.feature.settings.SettingsScreen
import com.inovixlabs.tazkiyah.feature.speaker.SpeakerScreen
import com.inovixlabs.tazkiyah.feature.speakers.SpeakersScreen
import com.inovixlabs.tazkiyah.playback.PlayerSurface
import com.inovixlabs.tazkiyah.playback.video.FocusedFileVideoPlayer
import com.inovixlabs.tazkiyah.playback.youtube.FocusedYouTubePlayer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class Tab(val route: Any, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(HomeRoute, R.string.tab_home, Icons.Rounded.Home),
    Tab(SpeakersRoute, R.string.tab_speakers, Icons.Rounded.Groups),
    Tab(CategoriesRoute, R.string.tab_categories, Icons.Rounded.Category),
    Tab(LibraryRoute, R.string.tab_library, Icons.Rounded.LibraryMusic),
)

/**
 * Layering (bottom → top): the video host (YouTube, or a saved/direct file) while collapsed
 * (zIndex −1, still attached so audio keeps going), the tabbed app with the mini player, the full
 * player, and the video host again when expanded/fullscreen. The host is never re-created when
 * the player is collapsed.
 */
@Composable
fun TazkiyahRoot(
    messages: Flow<String>,
    openPlayerRequests: Flow<Unit>,
    player: PlayerViewModel = hiltViewModel(),
) {
    val settings by player.settings.collectAsStateWithLifecycle()
    TazkiyahTheme(themeMode = settings?.themeMode ?: ThemeMode.SYSTEM, font = settings?.appFont ?: AppFont.NOTO_SANS) {
        val navController = rememberNavController()
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val showMessage: (String) -> Unit = remember { { text -> scope.launch { snackbar.showSnackbar(text) } } }

        val current by player.current.collectAsStateWithLifecycle()
        val state by player.playerState.collectAsStateWithLifecycle()
        val listenOnly by player.listenOnly.collectAsStateWithLifecycle()
        val nextInSeries by player.nextInSeries.collectAsStateWithLifecycle()
        val sleepTimer by player.sleepTimer.collectAsStateWithLifecycle()
        val seriesParts by player.seriesParts.collectAsStateWithLifecycle()
        val categories by player.categories.collectAsStateWithLifecycle()
        val audioPlayer by player.audioPlayer.collectAsStateWithLifecycle()
        val surface by player.surface.collectAsStateWithLifecycle()
        val playingSaved by player.playingSaved.collectAsStateWithLifecycle()
        val download by player.download.collectAsStateWithLifecycle()
        val downloadChoices by player.downloadChoices.collectAsStateWithLifecycle()

        var expanded by rememberSaveable { mutableStateOf(false) }
        var fullscreen by rememberSaveable { mutableStateOf(false) }
        var videoSlot by remember { mutableStateOf<Rect?>(null) }

        LaunchedEffect(messages) { messages.collect { showMessage(it) } }
        LaunchedEffect(openPlayerRequests) { openPlayerRequests.collect { if (player.current.value != null) expanded = true } }
        LaunchedEffect(current == null) {
            if (current == null) {
                expanded = false
                fullscreen = false
            }
        }
        LaunchedEffect(surface) {
            if (surface != PlayerSurface.YOUTUBE && surface != PlayerSurface.VIDEO) fullscreen = false
        }
        FullscreenEffect(fullscreen)
        SystemBarIconsFollowTheme()

        val requestNotifications = rememberNotificationPermission()
        // Every lecture can keep playing in the background with a media notification.
        val hasLecture = current != null
        LaunchedEffect(hasLecture) { if (hasLecture) requestNotifications() }
        val openLecture: (String) -> Unit = { id ->
            player.open(id)
            expanded = true
        }
        val navigateFromPlayer: (Any) -> Unit = { route ->
            expanded = false
            navController.navigate(route)
        }

        CompositionLocalProvider(LocalShowMessage provides showMessage) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                val lecture = current
                val videoSurface = surface == PlayerSurface.YOUTUBE || surface == PlayerSurface.VIDEO

                if (lecture != null && videoSurface) {
                    val density = LocalDensity.current
                    val slot = videoSlot
                    val hostModifier = when {
                        fullscreen -> Modifier.fillMaxSize()
                        slot != null -> with(density) {
                            Modifier
                                .offset { IntOffset(slot.left.roundToInt(), slot.top.roundToInt()) }
                                .size(slot.width.toDp(), slot.height.toDp())
                        }
                        else -> Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    }
                    val frameModifier = hostModifier.zIndex(if (expanded || fullscreen) 2f else -1f)
                    if (surface == PlayerSurface.YOUTUBE) {
                        FocusedYouTubePlayer(
                            controller = player.youTubeController,
                            lecture = lecture,
                            state = state,
                            listenOnly = listenOnly,
                            fullscreen = fullscreen,
                            nextInSeries = nextInSeries,
                            onPlayPause = player::togglePlayPause,
                            onSeekBy = player::seekBy,
                            onSeekTo = player::seekTo,
                            onSpeed = player::setSpeed,
                            onReplay = player::replay,
                            onPlayNext = { openLecture(it.id) },
                            onToggleFullscreen = { fullscreen = !fullscreen },
                            modifier = frameModifier,
                        )
                    } else {
                        FocusedFileVideoPlayer(
                            player = audioPlayer,
                            lecture = lecture,
                            state = state,
                            listenOnly = listenOnly,
                            fullscreen = fullscreen,
                            nextInSeries = nextInSeries,
                            onPlayPause = player::togglePlayPause,
                            onSeekBy = player::seekBy,
                            onSeekTo = player::seekTo,
                            onSpeed = player::setSpeed,
                            onReplay = player::replay,
                            onPlayNext = { openLecture(it.id) },
                            onToggleFullscreen = { fullscreen = !fullscreen },
                            modifier = frameModifier,
                        )
                    }
                }

                Scaffold(
                    modifier = Modifier.zIndex(0f),
                    snackbarHost = { SnackbarHost(snackbar) },
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        Column {
                            AnimatedVisibility(
                                visible = lecture != null && !expanded,
                                enter = slideInVertically { it },
                                exit = slideOutVertically { it },
                            ) {
                                if (lecture != null) {
                                    MiniPlayer(
                                        lecture = lecture,
                                        state = state,
                                        onExpand = { expanded = true },
                                        onPlayPause = player::togglePlayPause,
                                        onClose = player::close,
                                    )
                                }
                            }
                            BottomBar(navController)
                        }
                    },
                ) { padding ->
                    AppNavHost(
                        navController = navController,
                        onOpenLecture = openLecture,
                        modifier = Modifier.padding(padding),
                    )
                }

                if (expanded && lecture != null) {
                    FullPlayerScreen(
                        lecture = lecture,
                        state = state,
                        listenOnly = listenOnly,
                        sleepTimer = sleepTimer,
                        seriesParts = seriesParts,
                        categories = categories,
                        surface = surface ?: PlayerSurface.ARTWORK,
                        onVideoSlot = { videoSlot = it },
                        onCollapse = { expanded = false },
                        onPlayPause = player::togglePlayPause,
                        onSeekBy = player::seekBy,
                        onSeekTo = player::seekTo,
                        onSpeed = player::setSpeed,
                        onToggleFavorite = { player.toggleFavorite(lecture.id) },
                        onListenOnly = player::setListenOnly,
                        onSleepMinutes = player::setSleepTimer,
                        onSleepAtEnd = player::setSleepAtEnd,
                        onSleepCancel = player::cancelSleepTimer,
                        onOpenLecture = openLecture,
                        onOpenSpeaker = { navigateFromPlayer(SpeakerRoute(it)) },
                        onOpenCategory = { navigateFromPlayer(CategoryRoute(it)) },
                        downloadSection = {
                            DownloadSection(
                                download = download,
                                playingSaved = playingSaved,
                                choices = downloadChoices,
                                onSave = player::showDownloadChoices,
                                onChoose = player::startDownload,
                                onDismissChoices = player::dismissDownloadChoices,
                                onRetry = player::retryDownload,
                                onRemove = player::removeDownload,
                            )
                        },
                        modifier = Modifier.zIndex(1f),
                    )
                }
                if (fullscreen) {
                    // Black backdrop behind the fullscreen video.
                    Box(Modifier.fillMaxSize().zIndex(1.5f).background(Color.Black))
                }

                // Registered after NavHost's own handler, so Back first collapses the player /
                // leaves fullscreen before it pops a screen.
                if (expanded && !fullscreen) BackHandler { expanded = false }
                if (fullscreen) BackHandler { fullscreen = false }
                // Back on the first screen while a lecture is loaded minimizes the app instead of
                // finishing it — finishing would destroy the YouTube player and stop playback.
                val backStack by navController.currentBackStackEntryAsState()
                val atRoot = backStack != null && navController.previousBackStackEntry == null
                val activity = LocalActivity.current
                if (lecture != null && !expanded && !fullscreen && atRoot && activity != null) {
                    BackHandler { activity.moveTaskToBack(true) }
                }
            }
        }
    }
}

@Composable
private fun BottomBar(navController: NavHostController) {
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        tabs.forEach { tab ->
            val selected = destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.label)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    onOpenLecture: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val openSpeaker: (String) -> Unit = { navController.navigate(SpeakerRoute(it)) }
    val openCategory: (String) -> Unit = { navController.navigate(CategoryRoute(it)) }
    val openSearch: () -> Unit = { navController.navigate(SearchRoute) { launchSingleTop = true } }
    val openSettings: () -> Unit = { navController.navigate(SettingsRoute) { launchSingleTop = true } }
    val back: () -> Unit = { navController.popBackStack() }

    NavHost(navController = navController, startDestination = HomeRoute, modifier = modifier) {
        composable<HomeRoute> {
            HomeScreen(onOpenLecture, openCategory, openSearch, openSettings)
        }
        composable<SpeakersRoute> {
            SpeakersScreen(openSpeaker, openSearch)
        }
        composable<CategoriesRoute> {
            CategoriesScreen(openCategory, openSearch)
        }
        composable<LibraryRoute> {
            LibraryScreen(onOpenLecture, openSettings)
        }
        composable<SpeakerRoute> {
            SpeakerScreen(onBack = back, onOpenLecture = onOpenLecture)
        }
        composable<CategoryRoute> {
            CategoryScreen(onBack = back, onOpenLecture = onOpenLecture, onOpenCategory = openCategory)
        }
        composable<SearchRoute> {
            SearchScreen(onBack = back, onOpenLecture = onOpenLecture)
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = back, onOpenAbout = { navController.navigate(AboutRoute) { launchSingleTop = true } })
        }
        composable<AboutRoute> {
            AboutScreen(onBack = back)
        }
    }
}

/**
 * Status bar icons are always light: every screen's top bar is charcoal (brandTopBarColors).
 * Navigation bar icons follow the app theme (which can differ from the system's).
 */
@Composable
private fun SystemBarIconsFollowTheme() {
    val activity = LocalActivity.current ?: return
    val dark = isAppInDarkTheme()
    SideEffect {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = !dark
    }
}

/**
 * Portrait everywhere; landscape with hidden system bars only while the video is fullscreen.
 * (Rotation is handled via configChanges, so switching never restarts the video.)
 */
// Intentional: the lecture layouts are portrait-only; large screens ignore this lock anyway.
@SuppressLint("SourceLockedOrientationActivity")
@Composable
private fun FullscreenEffect(fullscreen: Boolean) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(fullscreen) {
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (fullscreen) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { }
    }
}

/** Asks once for notification permission (Android 13+) the first time something is played. */
@Composable
private fun rememberNotificationPermission(): () -> Unit {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !asked &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
