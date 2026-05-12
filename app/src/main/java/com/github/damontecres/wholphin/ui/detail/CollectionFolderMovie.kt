package com.github.damontecres.wholphin.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.damontecres.wholphin.data.model.CollectionFolderFilter
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.CollectionFolderGrid
import com.github.damontecres.wholphin.ui.components.GenreCardGrid
import com.github.damontecres.wholphin.ui.components.NetworkCardGrid
import com.github.damontecres.wholphin.ui.components.RecommendedMovie
import com.github.damontecres.wholphin.ui.components.StudioCardGrid
import com.github.damontecres.wholphin.ui.components.TabRow
import com.github.damontecres.wholphin.ui.components.ViewOptionsPoster
import com.github.damontecres.wholphin.ui.data.MovieSortOptions
import com.github.damontecres.wholphin.ui.data.VideoSortOptions
import com.github.damontecres.wholphin.ui.logTab
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.preferences.PreferencesViewModel
import org.jellyfin.sdk.model.api.BaseItemKind

@Composable
fun CollectionFolderMovie(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    preferencesViewModel: PreferencesViewModel = hiltViewModel(),
) {
    val rememberedTabIndex =
        remember { preferencesViewModel.getRememberedTab(preferences, destination.itemId, 0) }

    val savedTabOrder = preferences.appPreferences.interfacePreferences.movieLibraryTabOrderList
    var tabs by remember(savedTabOrder) {
        mutableStateOf(orderedLibraryTabs(DefaultMovieLibraryTabs, savedTabOrder))
    }
    val initialSelectedTabId =
        remember(destination.itemId, savedTabOrder) {
            tabs.getOrNull(rememberedTabIndex)?.id ?: DefaultMovieLibraryTabs.first().id
        }
    var selectedTabId by rememberSaveable(destination.itemId) { mutableStateOf(initialSelectedTabId) }
    val selectedTabIndex = tabs.indexOfFirst { it.id == selectedTabId }.takeIf { it >= 0 } ?: 0
    val selectedTab = tabs.getOrElse(selectedTabIndex) { DefaultMovieLibraryTabs.first() }
    val focusRequester = remember { FocusRequester() }
    val tabFocusRequesterById = remember { DefaultMovieLibraryTabs.associate { it.id to FocusRequester() } }
    val tabFocusRequesters = remember(tabs) { tabs.map { tabFocusRequesterById.getValue(it.id) } }

    val firstTabFocusRequester = remember { FocusRequester() }
//    LaunchedEffect(Unit) { firstTabFocusRequester.tryRequestFocus() }

    LaunchedEffect(savedTabOrder) {
        val ordered = orderedLibraryTabs(DefaultMovieLibraryTabs, savedTabOrder)
        if (ordered.map { it.id } != tabs.map { it.id }) {
            tabs = ordered
        }
    }

    LaunchedEffect(tabs) {
        if (tabs.none { it.id == selectedTabId }) {
            selectedTabId = tabs.firstOrNull()?.id ?: DefaultMovieLibraryTabs.first().id
        }
    }

    LaunchedEffect(selectedTabIndex) {
        logTab("movie", selectedTabIndex)
        preferencesViewModel.saveRememberedTab(preferences, destination.itemId, selectedTabIndex)
        preferencesViewModel.backdropService.clearBackdrop()
    }

    var showHeader by rememberSaveable { mutableStateOf(true) }

    Column(
        modifier = modifier,
    ) {
        AnimatedVisibility(
            showHeader,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                modifier =
                    Modifier
                        .padding(PaddingValues(top = 8.dp, bottom = 4.dp))
                        .focusRequester(firstTabFocusRequester),
                tabs = tabs.map { stringResource(it.titleRes) },
                tabKeys = tabs.map { it.id },
                onClick = { selectedTabId = tabs[it].id },
                focusRequesters = tabFocusRequesters,
            )
        }
        when (selectedTab) {
            LibraryTopTab.RECOMMENDED -> {
                RecommendedMovie(
                    preferences = preferences,
                    parentId = destination.itemId,
                    onFocusPosition = { pos ->
                        showHeader = pos.row < 1
                    },
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(focusRequester),
                )
            }

            LibraryTopTab.LIBRARY -> {
                CollectionFolderGrid(
                    preferences = preferences,
                    onClickItem = { _, item ->
                        preferencesViewModel.navigationManager.navigateTo(item.destination())
                    },
                    itemId = destination.itemId,
                    viewModelKey = "${destination.itemId}_library",
                    initialFilter =
                        CollectionFolderFilter(
                            filter =
                                GetItemsFilter(
                                    includeItemTypes = listOf(BaseItemKind.MOVIE),
                                ),
                        ),
                    showTitle = false,
                    recursive = true,
                    sortOptions = MovieSortOptions,
                    defaultViewOptions = ViewOptionsPoster,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(focusRequester),
                    positionCallback = { columns, position ->
                        showHeader = position < columns
                    },
                    playEnabled = true,
                    focusRequesterOnEmpty = tabFocusRequesters.getOrNull(selectedTabIndex),
                )
            }

            LibraryTopTab.COLLECTIONS -> {
                CollectionFolderGrid(
                    preferences = preferences,
                    onClickItem = { _, item ->
                        preferencesViewModel.navigationManager.navigateTo(item.destination())
                    },
                    itemId = destination.itemId,
                    viewModelKey = "${destination.itemId}_collection",
                    initialFilter =
                        CollectionFolderFilter(
                            filter =
                                GetItemsFilter(
                                    includeItemTypes = listOf(BaseItemKind.BOX_SET),
                                ),
                        ),
                    showTitle = false,
                    recursive = true,
                    sortOptions = VideoSortOptions,
                    defaultViewOptions = ViewOptionsPoster,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(focusRequester),
                    positionCallback = { columns, position ->
                        showHeader = position < columns
                    },
                    playEnabled = false,
                    focusRequesterOnEmpty = tabFocusRequesters.getOrNull(selectedTabIndex),
                )
            }

            LibraryTopTab.GENRES -> {
                GenreCardGrid(
                    itemId = destination.itemId,
                    includeItemTypes = listOf(BaseItemKind.MOVIE),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(focusRequester),
                )
            }

            LibraryTopTab.STUDIOS -> {
                StudioCardGrid(
                    itemId = destination.itemId,
                    includeItemTypes = listOf(BaseItemKind.MOVIE),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(focusRequester),
                )
            }

            LibraryTopTab.NETWORKS -> {
                NetworkCardGrid(
                    itemId = destination.itemId,
                    includeItemTypes = listOf(BaseItemKind.MOVIE),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(focusRequester),
                )
            }
        }
    }
}
