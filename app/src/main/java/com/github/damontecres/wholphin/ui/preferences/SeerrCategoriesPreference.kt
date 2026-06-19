package com.github.damontecres.wholphin.ui.preferences

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.discover.DiscoverCategory
import com.github.damontecres.wholphin.ui.discover.defaultDiscoverCategoryKeys
import com.github.damontecres.wholphin.ui.discover.orderedDiscoverCategories
import com.github.damontecres.wholphin.util.DataLoadingState

data class SeerrCategorySetting(
    val category: DiscoverCategory,
    val enabled: Boolean,
)

@Composable
fun SeerrCategoriesPreference(
    title: String,
    summary: String?,
    preferences: AppPreferences,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    viewModel: PreferencesViewModel = hiltViewModel(),
) {
    var showDialog by remember { mutableStateOf(false) }
    ClickPreference(
        title = title,
        summary = summary,
        onClick = {
            viewModel.loadSeerrCategories()
            showDialog = true
        },
        interactionSource = interactionSource,
        modifier = modifier,
    )

    if (showDialog) {
        val categoryState by viewModel.seerrCategories.collectAsState()
        var items by remember { mutableStateOf<List<SeerrCategorySetting>>(emptyList()) }
        val updateItems: (List<SeerrCategorySetting>) -> Unit = { updated ->
            items = updated
            viewModel.saveSeerrCategories(updated)
        }
        LaunchedEffect(categoryState, preferences.seerrPreferences) {
            val categories = (categoryState as? DataLoadingState.Success)?.data ?: return@LaunchedEffect
            val seerrPreferences = preferences.seerrPreferences
            val ordered = orderedDiscoverCategories(categories, seerrPreferences)
            val enabledKeys =
                if (seerrPreferences.discoverCategoriesCustomized) {
                    seerrPreferences.discoverCategoryOrderList.toSet() -
                        seerrPreferences.disabledDiscoverCategoryKeysList.toSet()
                } else {
                    defaultDiscoverCategoryKeys(ordered).toSet()
                }
            items =
                ordered.map { category ->
                    SeerrCategorySetting(category, category.key in enabledKeys)
                }
        }

        BasicDialog(
            onDismissRequest = {
                showDialog = false
            },
            elevation = 3.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.seerr_discover_categories),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                when (val current = categoryState) {
                    DataLoadingState.Pending,
                    DataLoadingState.Loading,
                    -> LoadingPage()

                    is DataLoadingState.Error -> ErrorMessage(current, Modifier)
                    is DataLoadingState.Success -> {
                        val context = LocalContext.current
                        val listState = rememberLazyListState()
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            itemsIndexed(items, key = { _, item -> item.category.key }) { index, item ->
                                NavDrawerPreferenceListItem(
                                    title = item.category.title(context),
                                    pinned = item.enabled,
                                    moveUpAllowed = index > 0,
                                    moveDownAllowed = index < items.lastIndex,
                                    onClick = {
                                        updateItems(
                                            items.toMutableList().apply {
                                                set(index, item.copy(enabled = !item.enabled))
                                            },
                                        )
                                    },
                                    onMoveUp = {
                                        updateItems(items.move(index, index - 1))
                                    },
                                    onMoveDown = {
                                        updateItems(items.move(index, index + 1))
                                    },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun <T> List<T>.move(
    fromIndex: Int,
    toIndex: Int,
): List<T> =
    toMutableList().apply {
        add(toIndex, removeAt(fromIndex))
    }
