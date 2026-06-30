package com.github.damontecres.wholphin.ui.components

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import java.util.UUID

@HiltViewModel(assistedFactory = RecommendedMovieViewModel.Factory::class)
class RecommendedMovieViewModel
    @AssistedInject
    constructor(
        @ApplicationContext context: Context,
        api: ApiClient,
        musicService: MusicService,
        serverRepository: ServerRepository,
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
            recommendedLibraryCacheService,
        ) {
        @AssistedFactory
        interface Factory {
            fun create(parentId: UUID): RecommendedMovieViewModel
        }

        override val libraryParentId: UUID = parentId

        private var suggestionItems: List<BaseItem> = emptyList()
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
        ): Pair<List<BaseItem>, Boolean>? =
            when (kind) {
                PaginatedRowKind.SUGGESTIONS -> {
                    val next = suggestionItems.drop(startIndex).take(HOME_PAGE_ROW_SIZE)
                    next to (startIndex + next.size < suggestionItems.size)
                }

                else ->
                    recommendedRowPagingService.fetchMovieRowPage(
                        kind = kind,
                        parentId = parentId,
                        userId = serverRepository.currentUser.value?.id,
                        startIndex = startIndex,
                    )
            }

        override fun init() {
            viewModelScope.launch(Dispatchers.IO + ExceptionHandler()) {
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
                    val (resumeItems, resumeHasMore) =
                        recommendedRowPagingService.fetchMovieRowPage(
                            kind = PaginatedRowKind.RESUME,
                            parentId = parentId,
                            userId = userId,
                            startIndex = 0,
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
                    loading.setValueOnMain(LoadingState.Success)

                    val semaphore = Semaphore(6)
                    listOf(
                        R.string.recently_released to PaginatedRowKind.RECENTLY_RELEASED,
                        R.string.recently_added to PaginatedRowKind.RECENTLY_ADDED,
                        R.string.top_unwatched to PaginatedRowKind.TOP_UNWATCHED,
                    ).map { (title, kind) ->
                        async(Dispatchers.IO) {
                            semaphore.withPermit {
                                loadPaginatedRowSync(title, kind)
                            }
                        }
                    }.forEach { it.await() }

                    launchSuggestions()

                    cacheCurrentRows()
                } catch (ex: Exception) {
                    Timber.e(ex, "Exception fetching movie recommendations")
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
                        .getSuggestionsFlow(parentId, BaseItemKind.MOVIE)
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
                    recommendedRowPagingService.fetchMovieRowPage(
                        kind = kind,
                        parentId = parentId,
                        userId = serverRepository.currentUser.value?.id,
                        startIndex = 0,
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
                    R.string.recently_released,
                    R.string.recently_added,
                    R.string.suggestions,
                    R.string.top_unwatched,
                ).mapIndexed { index, i -> i to index }.toMap()
        }
    }

/**
 * The "recommended" tab of a movie library
 */
@Composable
fun RecommendedMovie(
    preferences: UserPreferences,
    parentId: UUID,
    onFocusPosition: (RowColumn) -> Unit,
    modifier: Modifier = Modifier,
    takeFocus: Boolean = true,
    suppressContentScroll: () -> Boolean = { false },
    viewModel: RecommendedMovieViewModel =
        hiltViewModel<RecommendedMovieViewModel, RecommendedMovieViewModel.Factory>(
            viewModelStoreOwner = checkNotNull(LocalView.current.findViewTreeViewModelStoreOwner()),
            key = "recommended_movie_$parentId",
            creationCallback = { it.create(parentId) },
        ),
) {
    RecommendedContent(
        preferences = preferences,
        viewModel = viewModel,
        onFocusPosition = onFocusPosition,
        takeFocus = takeFocus,
        suppressContentScroll = suppressContentScroll,
        modifier = modifier,
    )
}
