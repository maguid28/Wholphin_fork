package com.github.damontecres.wholphin.ui.preferences

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.detail.DefaultMovieLibraryTabs
import com.github.damontecres.wholphin.ui.detail.DefaultTvLibraryTabs
import com.github.damontecres.wholphin.ui.detail.LibraryTopTab
import com.github.damontecres.wholphin.ui.detail.moveTab
import com.github.damontecres.wholphin.ui.detail.orderedLibraryTabs

@Composable
fun LibraryTabsPreference(
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
        onClick = { showDialog = true },
        interactionSource = interactionSource,
        modifier = modifier,
    )

    if (showDialog) {
        var movieTabs by remember(preferences.interfacePreferences.movieLibraryTabOrderList) {
            mutableStateOf(
                orderedLibraryTabs(
                    DefaultMovieLibraryTabs,
                    preferences.interfacePreferences.movieLibraryTabOrderList,
                ),
            )
        }
        var tvTabs by remember(preferences.interfacePreferences.tvLibraryTabOrderList) {
            mutableStateOf(
                orderedLibraryTabs(
                    DefaultTvLibraryTabs,
                    preferences.interfacePreferences.tvLibraryTabOrderList,
                ),
            )
        }
        LibraryTabsPreferenceDialog(
            movieTabs = movieTabs,
            tvTabs = tvTabs,
            onDismissRequest = {
                viewModel.saveLibraryTabOrder(movieLibrary = true, tabIds = movieTabs.map { it.id })
                viewModel.saveLibraryTabOrder(movieLibrary = false, tabIds = tvTabs.map { it.id })
                showDialog = false
            },
            onMoveMovie = { fromIndex, toIndex ->
                movieTabs = movieTabs.moveTab(fromIndex, toIndex)
            },
            onMoveTv = { fromIndex, toIndex ->
                tvTabs = tvTabs.moveTab(fromIndex, toIndex)
            },
        )
    }
}

@Composable
private fun LibraryTabsPreferenceDialog(
    movieTabs: List<LibraryTopTab>,
    tvTabs: List<LibraryTopTab>,
    onDismissRequest: () -> Unit,
    onMoveMovie: (fromIndex: Int, toIndex: Int) -> Unit,
    onMoveTv: (fromIndex: Int, toIndex: Int) -> Unit,
) {
    BasicDialog(
        onDismissRequest = onDismissRequest,
        elevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.library_tab_order),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                item {
                    LibraryTabsSectionHeader(stringResource(R.string.movies))
                }
                itemsIndexed(movieTabs, key = { _, item -> "movie_${item.id}" }) { index, item ->
                    LibraryTabsPreferenceListItem(
                        title = stringResource(item.titleRes),
                        moveUpAllowed = index > 0,
                        moveDownAllowed = index < movieTabs.lastIndex,
                        onMoveUp = { onMoveMovie(index, index - 1) },
                        onMoveDown = { onMoveMovie(index, index + 1) },
                        modifier = Modifier.animateItem(),
                    )
                }

                item {
                    LibraryTabsSectionHeader(stringResource(R.string.tv_shows))
                }
                itemsIndexed(tvTabs, key = { _, item -> "tv_${item.id}" }) { index, item ->
                    LibraryTabsPreferenceListItem(
                        title = stringResource(item.titleRes),
                        moveUpAllowed = index > 0,
                        moveDownAllowed = index < tvTabs.lastIndex,
                        onMoveUp = { onMoveTv(index, index - 1) },
                        onMoveDown = { onMoveTv(index, index + 1) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryTabsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun LibraryTabsPreferenceListItem(
    title: String,
    moveUpAllowed: Boolean,
    moveDownAllowed: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp, max = 88.dp),
        ) {
            ListItem(
                selected = false,
                headlineContent = {
                    Text(text = title)
                },
                onClick = { },
                modifier = Modifier.weight(1f),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.wrapContentWidth(),
            ) {
                MoveButton(R.string.fa_caret_up, moveUpAllowed, onMoveUp)
                MoveButton(R.string.fa_caret_down, moveDownAllowed, onMoveDown)
            }
        }
    }
}
