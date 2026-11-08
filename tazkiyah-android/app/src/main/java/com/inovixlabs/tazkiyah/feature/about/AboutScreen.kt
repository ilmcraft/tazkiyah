package com.inovixlabs.tazkiyah.feature.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.inovixlabs.tazkiyah.BuildConfig
import com.inovixlabs.tazkiyah.R
import com.inovixlabs.tazkiyah.core.designsystem.AppScreen
import com.inovixlabs.tazkiyah.core.designsystem.LocalShowMessage
import com.inovixlabs.tazkiyah.core.util.shareApp
import com.inovixlabs.tazkiyah.core.util.toBanglaDigits
import com.inovixlabs.tazkiyah.data.repository.AppSettings
import com.inovixlabs.tazkiyah.data.repository.SettingsRepository
import com.inovixlabs.tazkiyah.data.update.AppUpdateChecker
import com.inovixlabs.tazkiyah.data.update.UpdateCheckResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Open-source parts shipped in the APK (name to licence). */
private val LIBRARIES = listOf(
    "NewPipeExtractor" to "GPL-3.0",
    "Mozilla Rhino" to "MPL-2.0",
    "jsoup" to "MIT",
    "nanojson" to "MIT / Apache-2.0",
    "protobuf-javalite" to "BSD-3-Clause",
    "android-youtube-player" to "MIT",
    "AndroidX: Compose, Material 3, Media3, Room, WorkManager, DataStore, Navigation" to "Apache-2.0",
    "Dagger / Hilt" to "Apache-2.0",
    "Kotlin, kotlinx.coroutines, kotlinx.serialization" to "Apache-2.0",
    "OkHttp, Okio" to "Apache-2.0",
    "Coil" to "Apache-2.0",
    "Guava" to "Apache-2.0",
    "Noto Sans/Serif Bengali, Hind Siliguri, Anek Bangla, Baloo Da 2 (ফন্ট)" to "SIL OFL 1.1",
)

@HiltViewModel
class AboutViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val checker: AppUpdateChecker,
) : ViewModel() {
    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    private val _results = Channel<UpdateCheckResult>(Channel.BUFFERED)
    val results: Flow<UpdateCheckResult> = _results.receiveAsFlow()

    fun checkNow() {
        if (_checking.value) return
        viewModelScope.launch {
            _checking.value = true
            _results.send(checker.check())
            _checking.value = false
        }
    }
}

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val checking by viewModel.checking.collectAsStateWithLifecycle()
    val showMessage = LocalShowMessage.current
    val resources = LocalResources.current
    val context = LocalContext.current
    val openLink = rememberLinkOpener()
    var showLicense by rememberSaveable { mutableStateOf(false) }
    val repoUrl = "https://github.com/${BuildConfig.GITHUB_REPO}"
    val email = stringResource(R.string.contact_email)

    LaunchedEffect(viewModel) {
        viewModel.results.collect { result ->
            showMessage(
                resources.getString(
                    when (result) {
                        UpdateCheckResult.UpdateAvailable -> R.string.update_check_available
                        UpdateCheckResult.UpToDate -> R.string.update_check_up_to_date
                        UpdateCheckResult.Failed -> R.string.update_check_failed
                    },
                ),
            )
        }
    }

    AppScreen(title = stringResource(R.string.about_title), onBack = onBack) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.tagline), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(R.string.about_version, BuildConfig.VERSION_NAME.toBanglaDigits()),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(stringResource(R.string.about_description), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                }
            }
            item {
                val update = settings?.appUpdate
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_update)) },
                    supportingContent = {
                        Text(
                            if (update != null) stringResource(R.string.about_update_available, update.versionName.toBanglaDigits())
                            else stringResource(R.string.about_update_none),
                            color = if (update != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    leadingContent = { Icon(Icons.Rounded.SystemUpdate, contentDescription = null) },
                    trailingContent = {
                        TextButton(onClick = viewModel::checkNow, enabled = !checking) { Text(stringResource(R.string.about_update_check)) }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(enabled = update != null) { update?.let { openLink(it.url) } },
                )
            }
            item {
                LinkRow(Icons.Rounded.Share, stringResource(R.string.share_app), stringResource(R.string.share_app_desc)) { shareApp(context) }
            }
            item {
                val site = BuildConfig.DOWNLOAD_PAGE_URL
                LinkRow(Icons.Rounded.Language, stringResource(R.string.about_download_page), site.removePrefix("https://")) { openLink(site) }
            }
            item {
                LinkRow(Icons.Rounded.Code, stringResource(R.string.about_source), repoUrl.removePrefix("https://")) { openLink(repoUrl) }
            }
            item {
                LinkRow(Icons.Rounded.Email, stringResource(R.string.about_contact), email) { openLink("mailto:$email") }
            }
            item { Section(stringResource(R.string.about_privacy_title), stringResource(R.string.about_privacy)) }
            item { Section(null, stringResource(R.string.about_disclaimer)) }
            item {
                HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Section(stringResource(R.string.about_license_title), stringResource(R.string.about_license_app))
                LinkRow(Icons.Rounded.Description, stringResource(R.string.about_license_full), "GNU GPL v3.0") { showLicense = true }
                Text(
                    stringResource(R.string.about_libraries),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(LIBRARIES) { (name, license) ->
                ListItem(
                    headlineContent = { Text(name, style = MaterialTheme.typography.bodyMedium) },
                    trailingContent = { Text(license, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }

    if (showLicense) LicenseDialog(onDismiss = { showLicense = false })
}

@Composable
private fun LinkRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun Section(title: String?, body: String) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
        }
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun LicenseDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text by produceState("") {
        value = withContext(Dispatchers.IO) {
            context.assets.open("licenses/GPL-3.0.txt").bufferedReader().use { it.readText() }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("GNU GPL v3.0") },
        text = {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

/** Opens a web or mailto link in another app; tells the user when nothing can open it. */
@Composable
fun rememberLinkOpener(): (String) -> Unit {
    val uriHandler = LocalUriHandler.current
    val showMessage = LocalShowMessage.current
    val resources = LocalResources.current
    return { url ->
        try {
            uriHandler.openUri(url)
        } catch (e: Exception) {
            showMessage(resources.getString(R.string.no_app_for_link))
        }
    }
}
