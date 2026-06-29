package com.github.damontecres.wholphin.ui.components

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.MediaManagementService
import com.github.damontecres.wholphin.services.MediaReportService
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.deleteItem
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialog
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialogInfo
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.detail.PlaylistDialog
import com.github.damontecres.wholphin.ui.detail.PlaylistLoadingState
import com.github.damontecres.wholphin.ui.detail.rematch.MetadataRematchHost
import com.github.damontecres.wholphin.ui.detail.rematch.MetadataRematchViewModel
import com.github.damontecres.wholphin.ui.detail.music.addToQueue
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.main.HomePageContent
import com.github.damontecres.wholphin.ui.main.HomePageHeader
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.rememberPosition
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.MediaType
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

private val RecommendedLibraryHeaderPadding =
    PaddingValues(
        top = 16.dp,
        bottom = 18.dp,
        start = HeaderUtils.startPadding,
    )
private val RecommendedLibraryHeaderHeight = 168.dp

/**
 * Abstract [ViewModel] for the "Recommended" tab for a library
 */
abstract class RecommendedViewModel(
    @param:ApplicationContext val context: Context,
    val api: ApiClient,
    val serverRepository: ServerRepository,
    val navigationManager: NavigationManager,
    val favoriteWatchManager: FavoriteWatchManager,
    val mediaReportService: MediaReportService,
    private val musicService: MusicService,
    private val backdropService: BackdropService,
    private val mediaManagementService: MediaManagementService,
) : ViewModel() {
    abstract fun init()

    abstract val rows: MutableStateFlow<List<HomeRowLoadingState>>

    val loading = MutableLiveData<LoadingState>(LoadingState.Loading)

    private val initStarted = AtomicBoolean(false)

    init {
        mediaManagementService.deletedItemFlow
            .onEach { removeItemFromRows(it.item) }
            .catch { ex -> Timber.e(ex, "Error refreshing recommended after delete") }
            .launchIn(viewModelScope)
    }

    fun initIfNeeded() {
        if (loading.value == LoadingState.Success) return
        if (!initStarted.compareAndSet(false, true)) return
        init()
    }

    fun refreshItem(
        position: RowColumn,
        itemId: UUID,
    ) {
        viewModelScope.launchIO {
            val row = rows.value.getOrNull(position.row)
            if (row is HomeRowLoadingState.Success) {
                (row.items as? ApiRequestPager<*>)?.refreshItem(position.column, itemId)
            }
        }
    }

    fun setWatched(
        position: RowColumn,
        itemId: UUID,
        watched: Boolean,
    ) {
        viewModelScope.launchIO {
            favoriteWatchManager.setWatched(itemId, watched)
            refreshItem(position, itemId)
        }
    }

    fun setFavorite(
        position: RowColumn,
        itemId: UUID,
        watched: Boolean,
    ) {
        viewModelScope.launchIO {
            favoriteWatchManager.setFavorite(itemId, watched)
            refreshItem(position, itemId)
        }
    }

    fun updateBackdrop(item: BaseItem) {
        viewModelScope.launchIO {
            backdropService.submit(item)
        }
    }

    abstract fun update(
        @StringRes title: Int,
        row: HomeRowLoadingState,
    ): HomeRowLoadingState

    fun update(
        @StringRes title: Int,
        viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
        block: suspend () -> List<BaseItem>,
    ): Deferred<HomeRowLoadingState> =
        viewModelScope.async(Dispatchers.IO) {
            val titleStr = context.getString(title)
            val row =
                try {
                    HomeRowLoadingState.Success(titleStr, block.invoke(), viewOptions)
                } catch (ex: Exception) {
                    HomeRowLoadingState.Error(titleStr, null, ex)
                }
            update(title, row)
        }

    fun deleteItem(
        position: RowColumn,
        item: BaseItem,
    ) {
        deleteItem(context, mediaManagementService, item) {
            viewModelScope.launchDefault {
                removeItemFromRows(item, position)
            }
        }
    }

    private suspend fun removeItemFromRows(
        item: BaseItem,
        position: RowColumn? = null,
    ) {
        val row = position?.row?.let { rows.value.getOrNull(it) }
        if (row is HomeRowLoadingState.Success) {
            (row.items as? ApiRequestPager<*>)?.refreshAfterItemDeleted(
                item.id,
                position?.column,
            )
        }
        rows.update { homeRows ->
            homeRows.map { homeRow ->
                if (homeRow is HomeRowLoadingState.Success && homeRow.items !is ApiRequestPager<*>) {
                    val filtered = homeRow.items.filterNot { it?.id == item.id }
                    if (filtered.size == homeRow.items.size) {
                        homeRow
                    } else {
                        homeRow.copy(items = filtered)
                    }
                } else {
                    homeRow
                }
            }
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

    fun addToQueue(
        item: BaseItem,
        index: Int,
    ) = addToQueue(api, musicService, item, index)
}

@Composable
fun RecommendedContent(
    preferences: UserPreferences,
    viewModel: RecommendedViewModel,
    modifier: Modifier = Modifier,
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
    metadataRematchViewModel: MetadataRematchViewModel = hiltViewModel(),
    onFocusPosition: ((RowColumn) -> Unit)? = null,
) {
    var showContextMenu by remember { mutableStateOf<ContextMenu?>(null) }
    var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }
    var showPlaylistDialog by remember { mutableStateOf<Optional<UUID>>(Optional.absent()) }
    val playlistState by playlistViewModel.playlistState.observeAsState(PlaylistLoadingState.Pending)
    val currentUserDto by viewModel.serverRepository.currentUserDto.observeAsState()
    val isAdministrator = currentUserDto?.policy?.isAdministrator == true

    LaunchedEffect(Unit) {
        viewModel.initIfNeeded()
    }
    val loading by viewModel.loading.observeAsState(LoadingState.Loading)
    val rows by viewModel.rows.collectAsState()
    val showContent = loading == LoadingState.Success || rows.any { it.completed }

    when {
        loading is LoadingState.Error && !showContent -> {
            ErrorMessage(loading as LoadingState.Error, modifier)
        }

        !showContent -> {
            LoadingPage(modifier)
        }

        else -> {
            var position by rememberPosition()
            fun contextActionsFor(rowColumn: RowColumn) =
                ContextMenuActions(
                    navigateTo = viewModel.navigationManager::navigateTo,
                    onClickWatch = { itemId, watched ->
                        viewModel.setWatched(rowColumn, itemId, watched)
                    },
                    onClickFavorite = { itemId, favorite ->
                        viewModel.setFavorite(rowColumn, itemId, favorite)
                    },
                    onClickAddPlaylist = { itemId ->
                        playlistViewModel.loadPlaylists(MediaType.VIDEO)
                        showPlaylistDialog.makePresent(itemId)
                    },
                    onSendMediaInfo = viewModel.mediaReportService::sendReportFor,
                    onDeleteItem = { viewModel.deleteItem(rowColumn, it) },
                    onShowOverview = { overviewDialog = ItemDetailsDialogInfo(it) },
                    onChooseVersion = { _, _ ->
                        // Not supported on this page
                    },
                    onChooseTracks = { result ->
                        // Not supported on this page
                    },
                    onClearChosenStreams = {
                        // Not supported on this page
                    },
                    onClickRematchMetadata = { item ->
                        metadataRematchViewModel.startRematch(item) {
                            viewModel.refreshItem(rowColumn, it.id)
                        }
                    },
                )

            HomePageContent(
                homeRows = rows,
                position = position,
                onClickItem = { _, item ->
                    viewModel.navigationManager.navigateTo(item.destination())
                },
                onLongClickItem = { rowColumn, item ->
                    position = rowColumn
                    showContextMenu =
                        ContextMenu.ForBaseItem(
                            fromLongClick = true,
                            item = item,
                            chosenStreams = null,
                            showGoTo = true,
                            showStreamChoices = false,
                            canDelete = viewModel.canDelete(item, preferences.appPreferences),
                            canRematchMetadata =
                                canRematchMetadata(
                                    item,
                                    isAdministrator,
                                    preferences.appPreferences,
                                ),
                            canRemoveContinueWatching = false,
                            canRemoveNextUp = false,
                            actions = contextActionsFor(rowColumn),
                        )
                },
                onClickPlay = { _, item ->
                    viewModel.navigationManager.navigateTo(Destination.Playback(item))
                },
                onFocusPosition = {
                    position = it
                    val nonEmptyRowBefore =
                        rows
                            .subList(0, it.row)
                            .count {
                                it is HomeRowLoadingState.Success && it.items.isEmpty()
                            }
                    onFocusPosition?.invoke(
                        RowColumn(
                            it.row - nonEmptyRowBefore,
                            it.column,
                        ),
                    )
                },
                onUpdateBackdrop = viewModel::updateBackdrop,
                showLogo = preferences.appPreferences.interfacePreferences.showLogos,
                modifier = modifier,
                headerComposable = { focusedItem ->
                    HomePageHeader(
                        item = focusedItem,
                        showLogo = preferences.appPreferences.interfacePreferences.showLogos,
                        modifier =
                            Modifier
                                .padding(RecommendedLibraryHeaderPadding)
                                .height(RecommendedLibraryHeaderHeight),
                    )
                },
            )
        }
    }
    overviewDialog?.let { info ->
        ItemDetailsDialog(
            info = info,
            showFilePath =
                viewModel.serverRepository.currentUserDto.value
                    ?.policy
                    ?.isAdministrator == true,
            onDismissRequest = { overviewDialog = null },
        )
    }
    showContextMenu?.let { contextMenu ->
        ContextMenuDialog(
            onDismissRequest = { showContextMenu = null },
            getMediaSource = null,
            contextMenu = contextMenu,
            preferredSubtitleLanguage = null,
        )
    }
    showPlaylistDialog.compose { itemId ->
        PlaylistDialog(
            title = stringResource(R.string.add_to_playlist),
            state = playlistState,
            onDismissRequest = { showPlaylistDialog.makeAbsent() },
            onClick = {
                playlistViewModel.addToPlaylist(it.id, itemId)
                showPlaylistDialog.makeAbsent()
            },
            createEnabled = true,
            onCreatePlaylist = {
                playlistViewModel.createPlaylistAndAddItem(it, itemId)
                showPlaylistDialog.makeAbsent()
            },
            elevation = 3.dp,
        )
    }
    MetadataRematchHost(metadataRematchViewModel)
}

data class RowColumnItem(
    val position: RowColumn,
    val item: BaseItem,
)
