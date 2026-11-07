package com.inovixlabs.tazkiyah.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inovixlabs.tazkiyah.R
import com.inovixlabs.tazkiyah.core.designsystem.EmptyState
import com.inovixlabs.tazkiyah.core.designsystem.LectureListItem
import com.inovixlabs.tazkiyah.core.util.toBanglaDigits

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenLecture: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                },
                title = {
                    TextField(
                        value = query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onQueryChange("") }) {
                                    Icon(Icons.Rounded.Clear, stringResource(R.string.clear))
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            viewModel.rememberQuery()
                            keyboard?.hide()
                        }),
                        shape = RoundedCornerShape(28.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(end = 12.dp).focusRequester(focusRequester),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                is SearchUiState.Idle -> RecentSearches(
                    recent = s.recent,
                    onPick = viewModel::onQueryChange,
                    onClear = viewModel::clearRecent,
                )
                SearchUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is SearchUiState.Results -> if (s.lectures.isEmpty()) {
                    EmptyState(stringResource(R.string.search_no_result, s.query), icon = Icons.Rounded.Search)
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                        item {
                            Text(
                                stringResource(R.string.search_result_count, s.lectures.size.toBanglaDigits()),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        items(s.lectures, key = { it.id }) { row ->
                            LectureListItem(row, onClick = {
                                viewModel.rememberQuery()
                                onOpenLecture(row.id)
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentSearches(recent: List<String>, onPick: (String) -> Unit, onClear: () -> Unit) {
    if (recent.isEmpty()) {
        EmptyState(stringResource(R.string.search_prompt), icon = Icons.Rounded.Search)
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.search_recent), style = MaterialTheme.typography.titleSmall) },
                trailingContent = { TextButton(onClick = onClear) { Text(stringResource(R.string.search_clear_recent)) } },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        items(recent) { q ->
            ListItem(
                headlineContent = { Text(q) },
                leadingContent = { Icon(Icons.Rounded.History, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onPick(q) },
            )
        }
    }
}
