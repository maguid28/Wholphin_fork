package com.github.damontecres.wholphin.ui.discover

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.ui.components.SearchEditTextBox
import com.github.damontecres.wholphin.ui.components.VoiceInputManager
import com.github.damontecres.wholphin.ui.components.VoiceSearchButton
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.DataLoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class SeerrSearchViewModel
    @Inject
    constructor(
        private val seerrService: SeerrService,
        val navigationManager: NavigationManager,
        val voiceInputManager: VoiceInputManager,
        private val backdropService: BackdropService,
    ) : ViewModel() {
        val results =
            MutableStateFlow<DataLoadingState<List<DiscoverItem>>>(DataLoadingState.Pending)

        private var searchJob: Job? = null
        private var currentQuery: String? = null

        init {
            addCloseable(voiceInputManager)
        }

        fun search(query: String) {
            val normalized = query.trim()
            if (currentQuery == normalized) return
            currentQuery = normalized
            searchJob?.cancel()
            if (normalized.isBlank()) {
                results.value = DataLoadingState.Pending
                return
            }
            searchJob =
                viewModelScope.launchIO {
                    results.value = DataLoadingState.Loading
                    try {
                        val items =
                            seerrService
                                .search(normalized)
                                .map { seerrService.createDiscoverItem(it) }
                                .filter {
                                    (it.type == SeerrItemType.MOVIE || it.type == SeerrItemType.TV) &&
                                        !it.isInLibrary
                                }.distinctBy { it.type to it.id }
                        results.value = DataLoadingState.Success(items)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        results.value = DataLoadingState.Error(ex)
                    }
                }
        }

        fun updateBackdrop(item: DiscoverItem?) {
            viewModelScope.launchIO {
                if (item == null) {
                    backdropService.clearBackdrop()
                } else {
                    backdropService.submit(item)
                }
            }
        }
    }

@Composable
fun SeerrSearchPage(
    focusRequesterOnEmpty: FocusRequester?,
    modifier: Modifier = Modifier,
    viewModel: SeerrSearchViewModel =
        hiltViewModel(
            viewModelStoreOwner = checkNotNull(LocalView.current.findViewTreeViewModelStoreOwner()),
            key = "seerr_search",
        ),
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val results by viewModel.results.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var immediateSearchQuery by rememberSaveable { mutableStateOf<String?>(null) }
    val searchFocusRequester = remember { FocusRequester() }
    val textFieldFocusRequester = remember { FocusRequester() }
    val resultsFocusRequester = remember { FocusRequester() }

    LifecycleResumeEffect(Unit) {
        onPauseOrDispose {
            viewModel.voiceInputManager.stopListening()
        }
    }

    fun triggerImmediateSearch(searchQuery: String) {
        immediateSearchQuery = searchQuery
        viewModel.search(searchQuery)
    }

    LaunchedEffect(query) {
        if (immediateSearchQuery == query) {
            immediateSearchQuery = null
        } else {
            delay(750L)
            viewModel.search(query)
        }
    }
    LaunchedEffect(Unit) {
        searchFocusRequester.tryRequestFocus("seerr_search")
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            var isSearchActive by remember { mutableStateOf(false) }
            var isTextFieldFocused by remember { mutableStateOf(false) }

            BackHandler(isTextFieldFocused) {
                if (isSearchActive) {
                    isSearchActive = false
                    keyboardController?.hide()
                } else {
                    focusManager.moveFocus(FocusDirection.Next)
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .focusGroup()
                        .focusRestorer(textFieldFocusRequester)
                        .focusRequester(searchFocusRequester),
            ) {
                VoiceSearchButton(
                    onSpeechResult = { spokenText ->
                        query = spokenText
                        triggerImmediateSearch(spokenText)
                    },
                    voiceInputManager = viewModel.voiceInputManager,
                )
                SearchEditTextBox(
                    value = query,
                    onValueChange = {
                        isSearchActive = true
                        query = it
                    },
                    onSearchClick = { triggerImmediateSearch(query) },
                    readOnly = !isSearchActive,
                    modifier =
                        Modifier
                            .focusRequester(textFieldFocusRequester)
                            .onFocusChanged { state ->
                                isTextFieldFocused = state.isFocused
                                if (!state.isFocused) isSearchActive = false
                            }.onPreviewKeyEvent { event ->
                                val isActivationKey =
                                    event.key == Key.DirectionCenter || event.key == Key.Enter
                                if (event.type == KeyEventType.KeyUp && isActivationKey && !isSearchActive) {
                                    isSearchActive = true
                                    keyboardController?.show()
                                    true
                                } else {
                                    false
                                }
                            },
                )
            }
        }

        when (val current = results) {
            DataLoadingState.Pending -> Unit
            DataLoadingState.Loading -> {
                SearchStatusText(stringResource(R.string.searching))
            }

            is DataLoadingState.Error -> {
                SearchStatusText(
                    current.message ?: current.exception?.localizedMessage ?: stringResource(R.string.unknown),
                    color = MaterialTheme.colorScheme.error,
                )
            }

            is DataLoadingState.Success -> {
                if (current.data.isEmpty()) {
                    SearchStatusText(
                        stringResource(R.string.no_results),
                        modifier =
                            focusRequesterOnEmpty?.let {
                                Modifier.focusRequester(it)
                            } ?: Modifier,
                    )
                } else {
                    DiscoverItemRow(
                        title = stringResource(R.string.discover),
                        items = current.data,
                        onClickItem = { _, item ->
                            viewModel.navigationManager.navigateTo(item.destination)
                        },
                        onLongClickItem = { _, _ -> },
                        onCardFocus = { index ->
                            viewModel.updateBackdrop(current.data.getOrNull(index))
                        },
                        enableViewMore = false,
                        modifier = Modifier.focusRequester(resultsFocusRequester),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchStatusText(
    text: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onBackground,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = color,
        modifier = modifier.padding(start = 16.dp),
    )
}
