package com.github.damontecres.wholphin.ui.detail.rematch

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.MetadataRematchService
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.DataLoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.model.api.RemoteSearchResult
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MetadataRematchViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val metadataRematchService: MetadataRematchService,
        private val backdropService: BackdropService,
    ) : ViewModel() {
        private val _rematchItem = MutableStateFlow<BaseItem?>(null)
        val rematchItem: StateFlow<BaseItem?> = _rematchItem.asStateFlow()

        private val _metadataRematchResults =
            MutableStateFlow<DataLoadingState<List<RemoteSearchResult>>>(DataLoadingState.Pending)
        val metadataRematchResults: StateFlow<DataLoadingState<List<RemoteSearchResult>>> =
            _metadataRematchResults.asStateFlow()

        private var metadataSearchJob: Job? = null
        private var onItemUpdated: ((BaseItem) -> Unit)? = null

        fun startRematch(
            item: BaseItem,
            onUpdated: (BaseItem) -> Unit = {},
        ) {
            resetMetadataRematch()
            onItemUpdated = onUpdated
            _rematchItem.value = item
        }

        fun dismissRematch() {
            resetMetadataRematch()
            _rematchItem.value = null
            onItemUpdated = null
        }

        fun resetMetadataRematch() {
            metadataSearchJob?.cancel()
            _metadataRematchResults.update { DataLoadingState.Pending }
        }

        fun searchMetadataMatches(query: String) {
            metadataSearchJob?.cancel()
            val item = _rematchItem.value
            if (item == null || query.isBlank()) {
                _metadataRematchResults.update { DataLoadingState.Pending }
                return
            }
            metadataSearchJob =
                viewModelScope.launchIO {
                    _metadataRematchResults.update { DataLoadingState.Loading }
                    try {
                        val results = metadataRematchService.search(item, query)
                        _metadataRematchResults.update { DataLoadingState.Success(results) }
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error searching metadata matches for %s", item.id)
                        _metadataRematchResults.update {
                            DataLoadingState.Error(
                                "Error searching metadata matches",
                                ex,
                            )
                        }
                    }
                }
        }

        fun applyMetadataMatch(result: RemoteSearchResult) {
            val item = _rematchItem.value ?: return
            viewModelScope.launchIO {
                try {
                    metadataRematchService.apply(item.id, result)
                    val updatedItem = metadataRematchService.waitForUpdatedItem(item)
                    backdropService.submit(updatedItem)
                    onItemUpdated?.invoke(updatedItem)
                    showToast(context, context.getString(R.string.metadata_rematch_complete))
                    dismissRematch()
                } catch (ex: Exception) {
                    Timber.e(ex, "Error applying metadata match for %s", item.id)
                    showToast(context, context.getString(R.string.metadata_rematch_error))
                }
            }
        }
    }
