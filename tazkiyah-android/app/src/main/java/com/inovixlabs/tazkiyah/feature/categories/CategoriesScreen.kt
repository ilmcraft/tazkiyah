package com.inovixlabs.tazkiyah.feature.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.inovixlabs.tazkiyah.R
import com.inovixlabs.tazkiyah.core.designsystem.AppScreen
import com.inovixlabs.tazkiyah.core.designsystem.EmptyState
import com.inovixlabs.tazkiyah.core.designsystem.UiState
import com.inovixlabs.tazkiyah.core.designsystem.UiStateContent
import com.inovixlabs.tazkiyah.core.designsystem.asUiState
import com.inovixlabs.tazkiyah.core.designsystem.categoryIcon
import com.inovixlabs.tazkiyah.core.util.toBanglaDigits
import com.inovixlabs.tazkiyah.data.local.entity.CategoryWithCount
import com.inovixlabs.tazkiyah.data.repository.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class CategoryNode(val item: CategoryWithCount, val children: List<CategoryWithCount>)

@HiltViewModel
class CategoriesViewModel @Inject constructor(catalog: CatalogRepository) : ViewModel() {
    val uiState: StateFlow<UiState<List<CategoryNode>>> = catalog.categories()
        .map { all ->
            val children = all.filter { it.category.parentId != null }.groupBy { it.category.parentId }
            all.filter { it.category.parentId == null }.map { CategoryNode(it, children[it.category.id].orEmpty()) }
        }
        .asUiState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoriesScreen(
    onOpenCategory: (String) -> Unit,
    onSearch: () -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AppScreen(
        title = stringResource(R.string.categories_title),
        actions = { IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, stringResource(R.string.search)) } },
    ) { padding ->
        UiStateContent(state, Modifier.padding(padding)) { nodes ->
            if (nodes.isEmpty()) {
                EmptyState(stringResource(R.string.categories_empty), Modifier.padding(padding))
                return@UiStateContent
            }
            LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(nodes, key = { it.item.category.id }) { node ->
                    val category = node.item.category
                    Column {
                        ListItem(
                            headlineContent = { Text(category.name, style = MaterialTheme.typography.titleMedium) },
                            supportingContent = {
                                Text(stringResource(R.string.lecture_count, node.item.lectureCount.toBanglaDigits()))
                            },
                            leadingContent = {
                                Icon(categoryIcon(category.icon), null, tint = MaterialTheme.colorScheme.primary)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { onOpenCategory(category.id) },
                        )
                        if (node.children.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(start = 72.dp, end = 16.dp, bottom = 8.dp),
                            ) {
                                node.children.forEach { child ->
                                    AssistChip(
                                        onClick = { onOpenCategory(child.category.id) },
                                        label = {
                                            Text(child.category.name + " · " + child.lectureCount.toBanglaDigits())
                                        },
                                        leadingIcon = { Icon(categoryIcon(child.category.icon), null, Modifier.size(18.dp)) },
                                    )
                                }
                            }
                        }
                        HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}
