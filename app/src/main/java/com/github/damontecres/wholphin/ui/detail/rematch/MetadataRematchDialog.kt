package com.github.damontecres.wholphin.ui.detail.rematch

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.ConfirmDialog
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.SearchEditTextBox
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.DataLoadingState
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.RemoteSearchResult

@Composable
fun MetadataRematchDialog(
    itemTitle: String,
    initialQuery: String,
    results: DataLoadingState<List<RemoteSearchResult>>,
    onSearch: (String) -> Unit,
    onApply: (RemoteSearchResult) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var query by rememberSaveable(initialQuery) { mutableStateOf(initialQuery) }
    var selectedResult by remember { mutableStateOf<RemoteSearchResult?>(null) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }
    val textFieldFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(200L)
        searchFocusRequester.tryRequestFocus()
    }

    LaunchedEffect(query) {
        delay(500L)
        onSearch(query)
    }

    BasicDialog(
        onDismissRequest = onDismissRequest,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
            ),
        elevation = 3.dp,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth(.78f)
                    .fillMaxHeight(.72f),
        ) {
            Text(
                text = stringResource(R.string.rematch_metadata),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = itemTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            var isSearchActive by remember { mutableStateOf(false) }
            var isTextFieldFocused by remember { mutableStateOf(false) }

            BackHandler(isTextFieldFocused) {
                when {
                    isSearchActive -> {
                        isSearchActive = false
                        keyboardController?.hide()
                    }

                    else -> focusManager.moveFocus(FocusDirection.Next)
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusGroup()
                        .focusRestorer(textFieldFocusRequester)
                        .focusRequester(searchFocusRequester),
            ) {
                SearchEditTextBox(
                    value = query,
                    onValueChange = {
                        isSearchActive = true
                        query = it
                    },
                    onSearchClick = { onSearch(query) },
                    readOnly = !isSearchActive,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(textFieldFocusRequester)
                            .onFocusChanged { state ->
                                isTextFieldFocused = state.isFocused
                                if (!state.isFocused) isSearchActive = false
                            }.onPreviewKeyEvent { event ->
                                val isActivationKey =
                                    event.key in listOf(Key.DirectionCenter, Key.Enter)
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

            when (results) {
                DataLoadingState.Pending -> {
                    Text(
                        text = stringResource(R.string.search),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                DataLoadingState.Loading -> {
                    Text(
                        text = stringResource(R.string.searching),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is DataLoadingState.Error -> {
                    ErrorMessage(
                        results.message ?: stringResource(R.string.metadata_rematch_search_error),
                        results.exception,
                    )
                }

                is DataLoadingState.Success -> {
                    if (results.data.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_results),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 360.dp),
                        ) {
                            items(results.data) { result ->
                                MetadataRematchResultRow(
                                    result = result,
                                    onClick = { selectedResult = result },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedResult?.let { result ->
        ConfirmDialog(
            title = result.name ?: stringResource(R.string.unknown),
            body = stringResource(R.string.metadata_rematch_confirm),
            onCancel = { selectedResult = null },
            onConfirm = {
                selectedResult = null
                onApply(result)
                onDismissRequest()
            },
            elevation = 4.dp,
        )
    }
}

@Composable
private fun MetadataRematchResultRow(
    result: RemoteSearchResult,
    onClick: () -> Unit,
) {
    val year = result.productionYear ?: result.premiereDate?.year
    val providerIds =
        result.providerIds.orEmpty().entries.joinToString("  ") {
            "${it.key}: ${it.value}"
        }
    val interactionSource = remember { MutableInteractionSource() }

    ListItem(
        selected = false,
        onClick = onClick,
        headlineContent = {
            Text(
                text =
                    buildString {
                        append(result.name ?: stringResource(R.string.unknown))
                        year?.let { append(" ($it)") }
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        overlineContent =
            result.searchProviderName?.let { provider ->
                {
                    Text(
                        text = provider,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
        supportingContent = {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (providerIds.isNotBlank()) {
                    Text(
                        text = providerIds,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                result.overview?.let { overview ->
                    Text(
                        text = overview,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        interactionSource = interactionSource,
        modifier = Modifier.fillMaxWidth(),
    )
}
