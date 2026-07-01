package com.github.damontecres.wholphin.ui.discover

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.api.seerr.infrastructure.ClientException
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.DiscoverRating
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.preferences.SeerrPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.listToDotString
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.main.HomePageHeader
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.rememberPosition
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.ScrollToTopBringIntoViewSpec
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.DiscoverRequestType
import com.google.common.cache.CacheBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SeerrDiscoverViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val seerrService: SeerrService,
        val navigationManager: NavigationManager,
        private val backdropService: BackdropService,
        private val userPreferencesService: UserPreferencesService,
    ) : ViewModel() {
        val state = MutableStateFlow<DiscoverState>(DiscoverState())
        val rating = MutableStateFlow<Map<Int, DiscoverRating>>(mapOf())

        init {
            viewModelScope.launchIO {
                backdropService.clearBackdrop()
                userPreferencesService.flow
                    .map { it.appPreferences.seerrPreferences }
                    .distinctUntilChanged()
                    .collectLatest(::loadCategories)
            }
        }

        private suspend fun loadCategories(preferences: SeerrPreferences) {
            val categories =
                enabledDiscoverCategories(
                    discoverCategories(seerrService.discoverGenres()),
                    preferences,
                )
            val categoryKeys = categories.map { it.key }
            val currentRows = state.value.rows
            if (currentRows.isNotEmpty() &&
                currentRows.map { it.key } == categoryKeys &&
                currentRows.all {
                    it.items is DataLoadingState.Success || it.items is DataLoadingState.Error
                }
            ) {
                return
            }

            state.value =
                DiscoverState(
                    rows =
                        categories.map { category ->
                            currentRows.firstOrNull {
                                it.key == category.key && it.items is DataLoadingState.Success
                            } ?: category.toRow(DataLoadingState.Loading)
                        },
                )
            coroutineScope {
                categories
                    .map { category ->
                        async {
                            val existingRow = state.value.rows.firstOrNull { it.key == category.key }
                            if (existingRow?.items is DataLoadingState.Success) {
                                return@async
                            }
                            val result =
                                try {
                                    DataLoadingState.Success(fetchCategory(category))
                                } catch (ex: CancellationException) {
                                    throw ex
                                } catch (ex: Exception) {
                                    DataLoadingState.Error(ex)
                                }
                            state.update { current ->
                                current.copy(
                                    rows =
                                        current.rows.map { row ->
                                            if (row.key == category.key) {
                                                category.toRow(result)
                                            } else {
                                                row
                                            }
                                        },
                                )
                            }
                        }
                    }.awaitAll()
            }
        }

        private suspend fun fetchCategory(category: DiscoverCategory): List<DiscoverItem> =
            when (category.requestType) {
                DiscoverRequestType.DISCOVER_TV -> seerrService.discoverTv()
                DiscoverRequestType.DISCOVER_MOVIES -> seerrService.discoverMovies()
                DiscoverRequestType.TRENDING -> seerrService.trending()
                DiscoverRequestType.UPCOMING_TV -> seerrService.upcomingTv()
                DiscoverRequestType.UPCOMING_MOVIES -> seerrService.upcomingMovies()
                DiscoverRequestType.UNKNOWN ->
                    category.genre
                        ?.let { seerrService.discoverGenreItems(listOf(it)).firstOrNull()?.items }
                        .orEmpty()
            }

        private fun DiscoverCategory.toRow(items: DataLoadingState<List<DiscoverItem>>) =
            DiscoverRowData(
                key = key,
                title = title(context),
                items = items,
                type = requestType,
            )

        fun updateBackdrop(item: DiscoverItem?) {
            viewModelScope.launchIO {
                if (item != null) {
                    backdropService.submit("discover_${item.id}", item.backDropUrl)
                    fetchRating(item)
                } else {
                    backdropService.clearBackdrop()
                }
            }
        }

        private val ratingCache =
            CacheBuilder
                .newBuilder()
                .maximumSize(100)
                .build<Int, DiscoverRating>()

        // TODO this is not very efficient
        fun fetchRating(item: DiscoverItem) {
            viewModelScope.launchIO {
                val cachedResult = ratingCache.getIfPresent(item.id)
                if (cachedResult != null) {
                    return@launchIO
                }
                val result =
                    try {
                        when (item.type) {
                            SeerrItemType.MOVIE -> {
                                DiscoverRating(
                                    seerrService.api.moviesApi.movieMovieIdRatingsGet(
                                        movieId = item.id,
                                    ),
                                )
                            }

                            SeerrItemType.TV -> {
                                DiscoverRating(seerrService.api.tvApi.tvTvIdRatingsGet(tvId = item.id))
                            }

                            SeerrItemType.PERSON -> {
                                DiscoverRating(null, null)
                            }

                            SeerrItemType.UNKNOWN -> {
                                DiscoverRating(null, null)
                            }
                        }
                    } catch (ex: ClientException) {
                        if (ex.statusCode == 404) {
                            Timber.w("No rating found for %s", item.id)
                            DiscoverRating(null, null)
                        } else {
                            Timber.e(ex, "Error getting rating for %s", item.id)
                            return@launchIO
                        }
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error getting rating for %s", item.id)
                        return@launchIO
                    }
                ratingCache.put(item.id, result)
                rating.update {
                    ratingCache.asMap().toMap()
                }
            }
        }
    }

data class DiscoverRowData(
    val title: String,
    val items: DataLoadingState<List<DiscoverItem>>,
    val type: DiscoverRequestType,
    val key: String = title,
) {
    companion object {
        val EMPTY = DiscoverRowData("", DataLoadingState.Pending, DiscoverRequestType.UNKNOWN)
    }
}

data class DiscoverState(
    val rows: List<DiscoverRowData> = emptyList(),
)

private fun DiscoverRowData.loadedItems(): List<DiscoverItem>? =
    when (val currentItems = items) {
        is DataLoadingState.Success -> currentItems.data
        else -> null
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SeerrDiscoverPage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: SeerrDiscoverViewModel =
        hiltViewModel(
            viewModelStoreOwner = checkNotNull(LocalView.current.findViewTreeViewModelStoreOwner()),
            key = "seerr_discover",
        ),
) {
    val state by viewModel.state.collectAsState()
    val rows = state.rows
    val ratingMap by viewModel.rating.collectAsState()
    val isPageLoading =
        rows.isEmpty() ||
            rows.any {
                it.items is DataLoadingState.Loading || it.items is DataLoadingState.Pending
            }

    if (isPageLoading) {
        LoadingPage(modifier.fillMaxSize())
        return
    }

    val focusRequesters = remember(rows.size) { List(rows.size) { FocusRequester() } }
    var position by rememberPosition(0, -1)
    val firstAvailablePosition =
        rows
            .indexOfFirst { !it.loadedItems().isNullOrEmpty() }
            .takeIf { it >= 0 }
            ?.let { RowColumn(it, 0) }
    val selectedPosition =
        if (position.column >= 0) {
            position
        } else {
            firstAvailablePosition ?: position
        }
    val focusedItem =
        remember(rows, selectedPosition) {
            rows
                .getOrNull(selectedPosition.row)
                ?.loadedItems()
                ?.getOrNull(selectedPosition.column)
        }
    LaunchedEffect(focusedItem) {
        viewModel.updateBackdrop(focusedItem)
    }
    var firstFocused by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(firstAvailablePosition) {
        val firstPosition = firstAvailablePosition ?: return@LaunchedEffect
        if (position.column < 0) {
            position = firstPosition
        }
        if (!firstFocused) {
            firstFocused = focusRequesters.getOrNull(firstPosition.row)?.tryRequestFocus("discover") == true
        } else if (firstFocused) {
            focusRequesters.getOrNull(position.row)?.tryRequestFocus()
        }
    }

    Column(
        modifier = modifier,
    ) {
        val details =
            remember(focusedItem, ratingMap) {
                buildList {
                    focusedItem
                        ?.releaseDate
                        ?.year
                        ?.toString()
                        ?.let(::add)
                }.let {
                    val rating = focusedItem?.id?.let { ratingMap[it] }
                    listToDotString(
                        it,
                        rating?.audienceRating,
                        rating?.criticRating?.toFloat(),
                        preferences.appPreferences.interfacePreferences,
                    )
                }
            }
        HomePageHeader(
            title = focusedItem?.title,
            subtitle = focusedItem?.subtitle,
            overview = focusedItem?.overview,
            overviewTwoLines = true,
            quickDetails = details,
            timeRemaining = null,
            showLogo = preferences.appPreferences.interfacePreferences.showLogos,
            logoImageUrl = null, // TODO
            modifier =
                Modifier
                    .padding(top = 24.dp, bottom = 16.dp, start = 32.dp)
                    .fillMaxHeight(.25f),
        )
        val density = LocalDensity.current
        val spaceAbovePx =
            with(density) {
                // The size of the row titles & spacing
                50.dp.toPx()
            }
        val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current
        CompositionLocalProvider(
            LocalBringIntoViewSpec provides ScrollToTopBringIntoViewSpec(spaceAbovePx),
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
                modifier =
                    Modifier
                        .focusRestorer()
                        .fillMaxSize(),
            ) {
                itemsIndexed(rows) { rowIndex, row ->
                    CompositionLocalProvider(LocalBringIntoViewSpec provides defaultBringIntoViewSpec) {
                        DiscoverRow(
                            row = row,
                            onClickItem = { index, item ->
                                position = RowColumn(rowIndex, index)
                                viewModel.navigationManager.navigateTo(item.destination)
                            },
                            onLongClickItem = { index, item -> },
                            onCardFocus = { index -> position = RowColumn(rowIndex, index) },
                            focusRequester = focusRequesters[rowIndex],
                            enableViewMore = row.type != DiscoverRequestType.UNKNOWN,
                            onClickViewMore = {
                                (row.items as? DataLoadingState.Success<List<DiscoverItem>>)?.data?.size?.let {
                                    position = RowColumn(rowIndex, it)
                                }
                                viewModel.navigationManager.navigateTo(
                                    Destination.DiscoverMoreResult(row.type),
                                )
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
