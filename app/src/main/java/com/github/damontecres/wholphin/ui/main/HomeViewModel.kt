package com.github.damontecres.wholphin.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.shouldFetchRtAudience
import com.github.damontecres.wholphin.preferences.shouldFetchRtCritic
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.HomePageResolvedSettings
import com.github.damontecres.wholphin.services.HomeRowConfigDisplay
import com.github.damontecres.wholphin.services.HomeSettingsService
import com.github.damontecres.wholphin.services.LatestNextUpService
import com.github.damontecres.wholphin.services.MediaManagementService
import com.github.damontecres.wholphin.services.MediaReportService
import com.github.damontecres.wholphin.services.MdbListRatingsService
import com.github.damontecres.wholphin.services.NavDrawerService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.TrailerService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.services.deleteItem
import com.github.damontecres.wholphin.services.tvAccess
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.HOME_PAGE_ROW_SIZE
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        val navigationManager: NavigationManager,
        val serverRepository: ServerRepository,
        val mediaReportService: MediaReportService,
        private val navDrawerService: NavDrawerService,
        private val homeSettingsService: HomeSettingsService,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val backdropService: BackdropService,
        private val userPreferencesService: UserPreferencesService,
        private val mediaManagementService: MediaManagementService,
        private val latestNextUpService: LatestNextUpService,
        private val trailerService: TrailerService,
        private val mdbListRatingsService: MdbListRatingsService,
    ) : ViewModel() {
        private val _state = MutableStateFlow(HomeState.EMPTY)
        val state: StateFlow<HomeState> = _state

        init {
            mediaManagementService.deletedItemFlow
                .onEach { removedItemFromHomeState(it.item) }
                .catch { ex -> Timber.e(ex, "Error refreshing home after delete") }
                .launchIn(viewModelScope)
        }

        fun init() {
            viewModelScope.launchIO {
                Timber.d("init HomeViewModel")
                try {
                    val preferences = userPreferencesService.getCurrent()
                    val prefs = preferences.appPreferences.homePagePreferences

                    serverRepository.currentUserDto.value?.let { userDto ->
                        val libraries =
                            navDrawerService.getAllUserLibraries(userDto.id, userDto.tvAccess)
                        val settings =
                            homeSettingsService.currentSettings.first { it != HomePageResolvedSettings.EMPTY }
                        val previousState = state.value
                        val refresh =
                            previousState.loadingState == LoadingState.Success &&
                                previousState.settings == settings
                        val cached =
                            homeSettingsService.getHomePageCache(userDto.id, settings)
                        val usedCache = cached != null && !refresh
                        Timber.v(
                            "refresh=%s, usedCache=%s, %s rows",
                            refresh,
                            usedCache,
                            settings.rows.size,
                        )
                        _state.update {
                            it.copy(
                                loadingState =
                                    when {
                                        usedCache || refresh -> LoadingState.Success
                                        else -> LoadingState.Loading
                                    },
                                refreshState =
                                    when {
                                        usedCache || refresh -> LoadingState.Loading
                                        else -> LoadingState.Pending
                                    },
                                settings = settings,
                                mediaBannerItems =
                                    when {
                                        usedCache -> cached.mediaBannerItems
                                        refresh -> it.mediaBannerItems
                                        else -> emptyList()
                                    },
                                itemAudienceScores =
                                    when {
                                        usedCache || refresh -> it.itemAudienceScores
                                        else -> emptyMap()
                                    },
                                itemCriticScores =
                                    when {
                                        usedCache || refresh -> it.itemCriticScores
                                        else -> emptyMap()
                                    },
                                homeRows =
                                    when {
                                        usedCache -> cached.homeRows
                                        refresh -> it.homeRows
                                        else ->
                                            settings.rows.map { row ->
                                                HomeRowLoadingState.Loading(row.title)
                                            }
                                    },
                            )
                        }

                        suspend fun fetchRow(row: HomeRowConfigDisplay): HomeRowLoadingState =
                            try {
                                homeSettingsService.fetchDataForRow(
                                    row = row.config,
                                    scope = viewModelScope,
                                    prefs = prefs,
                                    userDto = userDto,
                                    libraries = libraries,
                                    limit = HOME_PAGE_ROW_SIZE,
                                    isRefresh = refresh || usedCache,
                                )
                            } catch (ex: Exception) {
                                Timber.e(ex, "Error on row %s", row)
                                HomeRowLoadingState.Error(row.title, exception = ex)
                            }

                        fun updateRow(
                            rowIndex: Int,
                            rowData: HomeRowLoadingState,
                        ) {
                            _state.update { current ->
                                val newRows =
                                    current.homeRows.toMutableList().apply {
                                        if (rowIndex in indices) {
                                            set(rowIndex, rowData)
                                        }
                                    }
                                val firstRowReady =
                                    rowIndex == 0 && rowData !is HomeRowLoadingState.Error
                                current.copy(
                                    homeRows = newRows,
                                    loadingState =
                                        if (
                                            current.loadingState == LoadingState.Loading &&
                                            firstRowReady
                                        ) {
                                            LoadingState.Success
                                        } else {
                                            current.loadingState
                                        },
                                )
                            }
                        }

                        val mediaBannerDeferred =
                            viewModelScope.async(Dispatchers.IO) {
                                try {
                                    homeSettingsService.fetchMediaBannerItems(
                                        userDto = userDto,
                                        libraries = libraries,
                                    )
                                } catch (ex: Exception) {
                                    Timber.w(ex, "Could not fetch home media banner")
                                    null
                                }
                            }

                        if (settings.rows.isNotEmpty()) {
                            updateRow(0, fetchRow(settings.rows[0]))
                        }

                        if (settings.rows.size > 1) {
                            val semaphore = Semaphore(6)
                            val remaining =
                                settings.rows
                                    .drop(1)
                                    .mapIndexed { offset, row ->
                                        val rowIndex = offset + 1
                                        rowIndex to
                                            viewModelScope.async(Dispatchers.IO) {
                                                semaphore.withPermit {
                                                    fetchRow(row)
                                                }
                                            }
                                    }.toMutableList()
                            while (remaining.isNotEmpty()) {
                                val (rowIndex, rowData) =
                                    select {
                                        remaining.forEach { (index, deferred) ->
                                            deferred.onAwait { index to it }
                                        }
                                    }
                                remaining.removeIf { it.first == rowIndex }
                                updateRow(rowIndex, rowData)
                            }
                        }

                        Timber.v("Got all rows")
                        val mediaBannerItems =
                            mediaBannerDeferred.await() ?: _state.value.mediaBannerItems
                        _state.update {
                            it.copy(
                                loadingState = LoadingState.Success,
                                refreshState = LoadingState.Success,
                                mediaBannerItems = mediaBannerItems,
                                itemAudienceScores = emptyMap(),
                                itemCriticScores = emptyMap(),
                            )
                        }
                        homeSettingsService.putHomePageCache(
                            userId = userDto.id,
                            settings = settings,
                            homeRows = _state.value.homeRows,
                            mediaBannerItems = mediaBannerItems,
                        )
                        loadExternalRatings(
                            items = collectRatingItems(_state.value.homeRows, mediaBannerItems),
                        )
                        Timber.d("Home page load complete")
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Exception during home page loading")
                    if (state.value.loadingState == LoadingState.Success) {
                        showToast(context, "Error refreshing home: ${ex.localizedMessage}")
                        _state.update { it.copy(refreshState = LoadingState.Error(ex)) }
                    } else {
                        _state.update {
                            it.copy(loadingState = LoadingState.Error(ex))
                        }
                    }
                }
            }
        }

        private fun collectRatingItems(
            homeRows: List<HomeRowLoadingState>,
            mediaBannerItems: List<BaseItem>,
        ): List<BaseItem> =
            buildList {
                homeRows.forEach { row ->
                    if (row is HomeRowLoadingState.Success) {
                        addAll(row.items.filterNotNull())
                    }
                }
                addAll(mediaBannerItems)
            }.distinctBy { it.id }

        private fun loadExternalRatings(items: List<BaseItem>) {
            if (items.isEmpty()) return
            viewModelScope.launchIO {
                val interfacePreferences =
                    userPreferencesService.getCurrent().appPreferences.interfacePreferences
                val audienceScores =
                    if (interfacePreferences.shouldFetchRtAudience()) {
                        mdbListRatingsService.loadAudienceScores(items)
                    } else {
                        emptyMap()
                    }
                val criticScores =
                    if (interfacePreferences.shouldFetchRtCritic()) {
                        mdbListRatingsService.loadCriticScores(items)
                    } else {
                        emptyMap()
                    }
                _state.update {
                    it.copy(
                        itemAudienceScores = audienceScores,
                        itemCriticScores = criticScores,
                    )
                }
            }
        }

        fun setWatched(
            itemId: UUID,
            played: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
            favoriteWatchManager.setWatched(itemId, played)
            withContext(Dispatchers.Main) {
                init()
            }
        }

        fun setFavorite(
            itemId: UUID,
            favorite: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
            favoriteWatchManager.setFavorite(itemId, favorite)
            withContext(Dispatchers.Main) {
                init()
            }
        }

        fun updateBackdrop(item: BaseItem) {
            viewModelScope.launchIO {
                backdropService.submit(item)
            }
        }

        fun playFirstTrailer(
            activityContext: Context,
            item: BaseItem,
        ) {
            viewModelScope.launchIO {
                val trailer =
                    try {
                        trailerService.getTrailers(item).firstOrNull()
                    } catch (ex: Exception) {
                        Timber.w(ex, "Failed to load trailer for %s", item.id)
                        null
                    }
                withContext(Dispatchers.Main) {
                    if (trailer == null) {
                        showToast(activityContext, "No trailer found")
                    } else {
                        try {
                            TrailerService.onClick(
                                context = activityContext,
                                trailer = trailer,
                                navigateTo = navigationManager::navigateTo,
                            )
                        } catch (ex: Exception) {
                            Timber.w(ex, "Failed to play trailer for %s", item.id)
                            showToast(activityContext, "Unable to open trailer")
                        }
                    }
                }
            }
        }

        suspend fun loadMoreRow(rowIndex: Int) {
            val currentRow =
                state.value.homeRows.getOrNull(rowIndex) as? HomeRowLoadingState.Success ?: return
            val rowConfig = currentRow.rowType ?: return
            if (!currentRow.hasMore) return

            try {
                val preferences = userPreferencesService.getCurrent()
                val prefs = preferences.appPreferences.homePagePreferences
                val userDto = serverRepository.currentUserDto.value ?: return
                val libraries =
                    withContext(Dispatchers.IO) {
                        navDrawerService.getAllUserLibraries(userDto.id, userDto.tvAccess)
                    }
                val result =
                    withContext(Dispatchers.IO) {
                        homeSettingsService.fetchDataForRow(
                            row = rowConfig,
                            scope = viewModelScope,
                            prefs = prefs,
                            userDto = userDto,
                            libraries = libraries,
                            limit = HOME_PAGE_ROW_SIZE,
                            startIndex = currentRow.items.size,
                            isRefresh = false,
                        ) as? HomeRowLoadingState.Success
                    } ?: return

                _state.update { homeState ->
                    homeState.copy(
                        homeRows =
                            homeState.homeRows.mapIndexed { index, row ->
                                if (index == rowIndex && row is HomeRowLoadingState.Success) {
                                    row.copy(
                                        items = row.items + result.items,
                                        hasMore = result.hasMore,
                                    )
                                } else {
                                    row
                                }
                            },
                    )
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Error loading more items for home row %s", rowIndex)
                showToast(context, "Error loading more items")
            }
        }

        fun deleteItem(
            position: RowColumn,
            item: BaseItem,
        ) {
            deleteItem(context, mediaManagementService, item) {
                viewModelScope.launchDefault {
                    removedItemFromHomeState(item, position)
                }
            }
        }

        private suspend fun removedItemFromHomeState(
            item: BaseItem,
            position: RowColumn? = null,
        ) {
            val row = position?.row?.let { state.value.homeRows.getOrNull(it) }
            if (row is HomeRowLoadingState.Success) {
                val pager = row.items as? ApiRequestPager<*>
                if (pager != null && position != null) {
                    pager.refreshAfterItemDeleted(item.id, position.column)
                }
            }
            _state.update { current ->
                current.copy(
                    homeRows =
                        current.homeRows.mapIndexed { rowIndex, homeRow ->
                            if (homeRow is HomeRowLoadingState.Success) {
                                if (homeRow.items is ApiRequestPager<*>) {
                                    homeRow
                                } else {
                                    val filtered =
                                        homeRow.items.filterNot { it?.id == item.id }
                                    if (filtered.size == homeRow.items.size) {
                                        homeRow
                                    } else {
                                        homeRow.copy(items = filtered)
                                    }
                                }
                            } else {
                                homeRow
                            }
                        },
                    mediaBannerItems = current.mediaBannerItems.filter { it.id != item.id },
                )
            }
        }

        fun refreshItem(
            position: RowColumn,
            updatedItem: BaseItem,
        ) {
            viewModelScope.launchDefault {
                val row = state.value.homeRows.getOrNull(position.row)
                if (row is HomeRowLoadingState.Success) {
                    _state.update {
                        val newRow =
                            row.items.toMutableList().apply {
                                if (getOrNull(position.column)?.id == updatedItem.id) {
                                    set(position.column, updatedItem)
                                }
                            }
                        it.copy(
                            homeRows =
                                it.homeRows.toMutableList().apply {
                                    set(position.row, row.copy(items = newRow))
                                },
                        )
                    }
                }
                backdropService.submit(updatedItem)
            }
        }

        fun canDelete(
            item: BaseItem,
            appPreferences: AppPreferences,
        ): Boolean =
            mediaManagementService.canDelete(
                item,
                appPreferences,
                serverRepository.currentUserDto.value?.policy?.isAdministrator == true,
            )

        fun removeFromNextUp(item: BaseItem) {
            if (item.type == BaseItemKind.EPISODE) {
                viewModelScope.launchDefault {
                    serverRepository.currentUser.value?.id?.let { userId ->
                        latestNextUpService.removeFromNextUp(userId, item)
                        init()
                    }
                }
            } else {
                Timber.w("Item is not an episode %s", item.id)
            }
        }
    }

data class HomeState(
    val loadingState: LoadingState,
    val refreshState: LoadingState,
    val homeRows: List<HomeRowLoadingState>,
    val mediaBannerItems: List<BaseItem>,
    val itemAudienceScores: Map<UUID, Float>,
    val itemCriticScores: Map<UUID, Float>,
    val settings: HomePageResolvedSettings,
) {
    companion object {
        val EMPTY =
            HomeState(
                LoadingState.Pending,
                LoadingState.Pending,
                listOf(),
                listOf(),
                emptyMap(),
                emptyMap(),
                HomePageResolvedSettings.EMPTY,
            )
    }
}

/**
 * Whether a row is a "is watching" type
 */
private fun isWatchingRow(row: HomeRowConfig) =
    row is HomeRowConfig.ContinueWatching ||
        row is HomeRowConfig.NextUp ||
        row is HomeRowConfig.ContinueWatchingCombined
