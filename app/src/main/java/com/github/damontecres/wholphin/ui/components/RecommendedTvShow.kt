package com.github.damontecres.wholphin.ui.components

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.datastore.core.DataStore
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.LatestNextUpService
import com.github.damontecres.wholphin.services.MediaManagementService
import com.github.damontecres.wholphin.services.MediaReportService
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.RecommendedLibraryCacheService
import com.github.damontecres.wholphin.services.RecommendedRowPagingService
import com.github.damontecres.wholphin.services.SuggestionService
import com.github.damontecres.wholphin.services.SuggestionsResource
import com.github.damontecres.wholphin.ui.HOME_PAGE_ROW_SIZE
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.PaginatedRowKind
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import java.util.UUID

@HiltViewModel(assistedFactory = RecommendedTvShowViewModel.Factory::class)
class RecommendedTvShowViewModel
    @AssistedInject
    constructor(
        @ApplicationContext context: Context,
        api: ApiClient,
        musicService: MusicService,
        serverRepository: ServerRepository,
        private val preferencesDataStore: DataStore<AppPreferences>,
        private val latestNextUpService: LatestNextUpService,
        private val recommendedLibraryCacheService: RecommendedLibraryCacheService,
        private val recommendedRowPagingService: RecommendedRowPagingService,
        private val suggestionService: SuggestionService,
        @Assisted val parentId: UUID,
        navigationManager: NavigationManager,
        favoriteWatchManager: FavoriteWatchManager,
        mediaReportService: MediaReportService,
        backdropService: BackdropService,
        mediaManagementService: MediaManagementService,
    ) : RecommendedViewModel(
            context,
            api,
            serverRepository,
            navigationManager,
            favoriteWatchManager,
            mediaReportService,
            musicService,
            backdropService,
            mediaManagementService,
        ) {
        @AssistedFactory
        interface Factory {
            fun create(parentId: UUID): RecommendedTvShowViewModel
        }

        private var suggestionItems: List<BaseItem> = emptyList()
        private var combineContinueNext = false
        private var enableRewatchingNextUp = false
        private var maxDaysNextUp = 0
        private var cachedUserId: UUID? = null

        override val rows =
            MutableStateFlow<List<HomeRowLoadingState>>(
                rowTitles.keys.map {
                    HomeRowLoadingState.Pending(
                        context.getString(it),
                    )
                },
            )

        override suspend fun fetchMoreForRow(
            kind: PaginatedRowKind,
            startIndex: Int,
        ): Pair<List<BaseItem>, Boolean>? {
            val userId = serverRepository.currentUser.value?.id ?: return null
            return when (kind) {
                PaginatedRowKind.SUGGESTIONS -> {
                    val next = suggestionItems.drop(startIndex).take(HOME_PAGE_ROW_SIZE)
                    next to (startIndex + next.size < suggestionItems.size)
                }

                PaginatedRowKind.RESUME ->
                    if (combineContinueNext) {
                        latestNextUpService.fetchCombinedContinueWatching(
                            userId = userId,
                            limit = HOME_PAGE_ROW_SIZE,
                            startIndex = startIndex,
                            includeEpisodes = true,
                            enableRewatching = enableRewatchingNextUp,
                            enableResumable = false,
                            maxDays = maxDaysNextUp,
                            useSeriesForPrimary = true,
                            parentId = parentId,
                        )
                    } else {
                        recommendedRowPagingService.fetchTvRowPage(
                            kind = kind,
                            parentId = parentId,
                            userId = userId,
                            startIndex = startIndex,
                            combineContinueNext = false,
                            enableRewatchingNextUp = enableRewatchingNextUp,
                            maxDaysNextUp = maxDaysNextUp,
                        )
                    }

                else ->
                    recommendedRowPagingService.fetchTvRowPage(
                        kind = kind,
                        parentId = parentId,
                        userId = userId,
                        startIndex = startIndex,
                        combineContinueNext = combineContinueNext,
                        enableRewatchingNextUp = enableRewatchingNextUp,
                        maxDaysNextUp = maxDaysNextUp,
                    )
            }
        }

        override fun init() {
            viewModelScope.launch(Dispatchers.IO + ExceptionHandler()) {
                val preferences =
                    preferencesDataStore.data.firstOrNull() ?: AppPreferences.getDefaultInstance()
                combineContinueNext = preferences.homePagePreferences.combineContinueNext
                enableRewatchingNextUp = preferences.homePagePreferences.enableRewatchingNextUp
                maxDaysNextUp = preferences.homePagePreferences.maxDaysNextUp
                val userId = serverRepository.currentUser.value?.id
                if (userId == null) {
                    withContext(Dispatchers.Main) {
                        loading.value = LoadingState.Error(IllegalStateException("No current user"))
                    }
                    return@launch
                }

                recommendedLibraryCacheService.get(userId, parentId)?.let { cachedRows ->
                    cachedUserId = userId
                    rows.value = cachedRows
                    loading.setValueOnMain(LoadingState.Success)
                    launchSuggestions()
                    return@launch
                }

                cachedUserId = userId

                try {
                    if (combineContinueNext) {
                        val (combined, hasMore) =
                            latestNextUpService.fetchCombinedContinueWatching(
                                userId = userId,
                                limit = HOME_PAGE_ROW_SIZE,
                                startIndex = 0,
                                includeEpisodes = true,
                                enableRewatching = enableRewatchingNextUp,
                                enableResumable = false,
                                maxDays = maxDaysNextUp,
                                useSeriesForPrimary = true,
                                parentId = parentId,
                            )
                        update(
                            R.string.continue_watching,
                            HomeRowLoadingState.Success(
                                context.getString(R.string.continue_watching),
                                combined,
                                paginationKind = PaginatedRowKind.RESUME,
                                hasMore = hasMore,
                            ),
                        )
                        update(
                            R.string.next_up,
                            HomeRowLoadingState.Success(context.getString(R.string.next_up), listOf()),
                        )
                    } else {
                        val (resumeItems, resumeHasMore) =
                            recommendedRowPagingService.fetchTvRowPage(
                                kind = PaginatedRowKind.RESUME,
                                parentId = parentId,
                                userId = userId,
                                startIndex = 0,
                                combineContinueNext = false,
                                enableRewatchingNextUp = enableRewatchingNextUp,
                                maxDaysNextUp = maxDaysNextUp,
                            ) ?: (emptyList<BaseItem>() to false)
                        update(
                            R.string.continue_watching,
                            HomeRowLoadingState.Success(
                                context.getString(R.string.continue_watching),
                                resumeItems,
                                paginationKind = PaginatedRowKind.RESUME,
                                hasMore = resumeHasMore,
                            ),
                        )
                    }
                    loading.setValueOnMain(LoadingState.Success)

                    val semaphore = Semaphore(6)
                    val remainingRows =
                        buildList {
                            if (!combineContinueNext) {
                                add(R.string.next_up to PaginatedRowKind.NEXT_UP)
                            }
                            add(R.string.recently_released to PaginatedRowKind.RECENTLY_RELEASED)
                            add(R.string.recently_added to PaginatedRowKind.RECENTLY_ADDED)
                            add(R.string.top_unwatched to PaginatedRowKind.TOP_UNWATCHED)
                        }
                    remainingRows
                        .map { (title, kind) ->
                            async(Dispatchers.IO) {
                                semaphore.withPermit {
                                    loadPaginatedRowSync(title, kind)
                                }
                            }
                        }.forEach { it.await() }

                    launchSuggestions()

                    cacheCurrentRows()
                } catch (ex: Exception) {
                    Timber.e(ex, "Exception fetching tv recommendations")
                    withContext(Dispatchers.Main) {
                        loading.value = LoadingState.Error(ex)
                    }
                }
            }
        }

        private fun launchSuggestions() {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    suggestionService
                        .getSuggestionsFlow(parentId, BaseItemKind.SERIES)
                        .collect { resource ->
                            val state =
                                when (resource) {
                                    is SuggestionsResource.Loading -> {
                                        HomeRowLoadingState.Loading(
                                            context.getString(R.string.suggestions),
                                        )
                                    }

                                    is SuggestionsResource.Success -> {
                                        suggestionItems = resource.items
                                        val items = resource.items.take(HOME_PAGE_ROW_SIZE)
                                        HomeRowLoadingState.Success(
                                            context.getString(R.string.suggestions),
                                            items,
                                            paginationKind = PaginatedRowKind.SUGGESTIONS,
                                            hasMore = resource.items.size > HOME_PAGE_ROW_SIZE,
                                        )
                                    }

                                    is SuggestionsResource.Empty -> {
                                        suggestionItems = emptyList()
                                        HomeRowLoadingState.Success(
                                            context.getString(R.string.suggestions),
                                            emptyList(),
                                        )
                                    }
                                }
                            update(R.string.suggestions, state)
                            cacheCurrentRows()
                        }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Failed to fetch suggestions")
                    update(
                        R.string.suggestions,
                        HomeRowLoadingState.Error(
                            title = context.getString(R.string.suggestions),
                            exception = ex,
                        ),
                    )
                }
            }
        }

        private suspend fun loadPaginatedRowSync(
            @StringRes title: Int,
            kind: PaginatedRowKind,
        ): HomeRowLoadingState {
            val titleStr = context.getString(title)
            return try {
                val (items, hasMore) =
                    recommendedRowPagingService.fetchTvRowPage(
                        kind = kind,
                        parentId = parentId,
                        userId = serverRepository.currentUser.value?.id,
                        startIndex = 0,
                        combineContinueNext = combineContinueNext,
                        enableRewatchingNextUp = enableRewatchingNextUp,
                        maxDaysNextUp = maxDaysNextUp,
                    ) ?: (emptyList<BaseItem>() to false)
                HomeRowLoadingState.Success(
                    titleStr,
                    items,
                    paginationKind = kind,
                    hasMore = hasMore,
                )
            } catch (ex: Exception) {
                HomeRowLoadingState.Error(titleStr, null, ex)
            }.also { update(title, it) }
        }

        override fun update(
            @StringRes title: Int,
            row: HomeRowLoadingState,
        ): HomeRowLoadingState {
            rows.update { current ->
                current.toMutableList().apply { set(rowTitles[title]!!, row) }
            }
            return row
        }

        private fun cacheCurrentRows() {
            val userId = cachedUserId ?: return
            recommendedLibraryCacheService.put(userId, parentId, rows.value)
        }

        companion object {
            private val rowTitles =
                listOf(
                    R.string.continue_watching,
                    R.string.next_up,
                    R.string.recently_released,
                    R.string.recently_added,
                    R.string.suggestions,
                    R.string.top_unwatched,
                ).mapIndexed { index, i -> i to index }.toMap()
        }
    }

/**
 * The "recommended" tab of a TV show library
 */
@Composable
fun RecommendedTvShow(
    preferences: UserPreferences,
    parentId: UUID,
    onFocusPosition: (RowColumn) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecommendedTvShowViewModel =
        hiltViewModel<RecommendedTvShowViewModel, RecommendedTvShowViewModel.Factory>(
            viewModelStoreOwner = checkNotNull(LocalView.current.findViewTreeViewModelStoreOwner()),
            key = "recommended_tvshow_$parentId",
            creationCallback = { it.create(parentId) },
        ),
) {
    RecommendedContent(
        preferences = preferences,
        viewModel = viewModel,
        onFocusPosition = onFocusPosition,
        modifier = modifier,
    )
}
