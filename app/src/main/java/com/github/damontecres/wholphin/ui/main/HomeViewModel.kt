package com.github.damontecres.wholphin.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.HomePageResolvedSettings
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
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
                        val state = state.value

                        // Refreshing if a load has already occurred and the rows haven't significantly changed
                        val refresh =
                            state.loadingState == LoadingState.Success && state.settings == settings
                        Timber.v(
                            "refresh=%s, state.loadingState=%s, %s rows",
                            refresh,
                            state.loadingState,
                            settings.rows.size,
                        )
                        _state.update {
                            it.copy(
                                loadingState = if (refresh) LoadingState.Success else LoadingState.Loading,
                                refreshState = if (refresh) LoadingState.Loading else LoadingState.Pending,
                                settings = settings,
                                mediaBannerItems = if (refresh) it.mediaBannerItems else emptyList(),
                                mediaBannerAudienceScores = if (refresh) it.mediaBannerAudienceScores else emptyMap(),
                                homeRows =
                                    if (refresh) {
                                        it.homeRows
                                    } else {
                                        settings.rows.map { row -> HomeRowLoadingState.Loading(row.title) }
                                    },
                            )
                        }

                        val semaphore = Semaphore(4)
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

                        val deferred =
                            settings.rows
                                .map { row ->
                                    viewModelScope.async(Dispatchers.IO) {
                                        semaphore.withPermit {
                                            Timber.v("Fetching row: %s", row)
                                            try {
                                                homeSettingsService.fetchDataForRow(
                                                    row = row.config,
                                                    scope = viewModelScope,
                                                    prefs = prefs,
                                                    userDto = userDto,
                                                    libraries = libraries,
                                                    limit = prefs.maxItemsPerRow,
                                                    isRefresh = refresh,
                                                )
                                            } catch (ex: Exception) {
                                                Timber.e(ex, "Error on row %s", row)
                                                HomeRowLoadingState.Error(
                                                    row.title,
                                                    exception = ex,
                                                )
                                            }
                                        }
                                    }
                                }

                        // Replace rows as they complete so the home page becomes usable as soon as
                        // the first row is ready instead of waiting for every home section.
                        val remaining = deferred.withIndex().toMutableList()
                        while (remaining.isNotEmpty()) {
                            val (rowIndex, rowData) =
                                select {
                                    // "Return" the first remaining that is completed
                                    remaining
                                        .forEach { (rowIndex, deferred) ->
                                            deferred.onAwait { rowIndex to it }
                                        }
                                }
                            Timber.v("Got row data index=%s", rowIndex)
                            remaining.removeIf { it.index == rowIndex }
                            _state.update { state ->
                                val newRows =
                                    state.homeRows.toMutableList().apply {
                                        if (rowIndex in indices) {
                                            set(rowIndex, rowData)
                                        }
                                    }
                                state.copy(
                                    homeRows = newRows,
                                )
                            }
                        }
                        Timber.v("Got all rows")
                        _state.update {
                            it.copy(
                                loadingState = LoadingState.Success,
                                refreshState = LoadingState.Success,
                            )
                        }

                        mediaBannerDeferred.await()?.let { mediaBannerItems ->
                            _state.update {
                                it.copy(
                                    mediaBannerItems = mediaBannerItems,
                                    mediaBannerAudienceScores = emptyMap(),
                                )
                            }
                            loadMediaBannerAudienceScores(mediaBannerItems)
                        }
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

        private fun loadMediaBannerAudienceScores(items: List<BaseItem>) {
            viewModelScope.launchIO {
                val scores =
                    items
                        .mapNotNull { item ->
                            mdbListRatingsService
                                .getRottenTomatoesAudienceScore(item)
                                ?.takeIf { it > 0f }
                                ?.let { item.id to it }
                        }.toMap()
                _state.update {
                    it.copy(mediaBannerAudienceScores = scores)
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

        fun deleteItem(
            position: RowColumn,
            item: BaseItem,
        ) {
            deleteItem(context, mediaManagementService, item) {
                viewModelScope.launchDefault {
                    val row = state.value.homeRows.getOrNull(position.row)
                    if (row is HomeRowLoadingState.Success) {
                        _state.update {
                            val newRow =
                                row.items.toMutableList().apply {
                                    removeAt(position.column)
                                }
                            it.copy(
                                homeRows =
                                    it.homeRows.toMutableList().apply {
                                        set(position.row, row.copy(items = newRow))
                                    },
                            )
                        }
                    }
                }
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
    val mediaBannerAudienceScores: Map<UUID, Float>,
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
