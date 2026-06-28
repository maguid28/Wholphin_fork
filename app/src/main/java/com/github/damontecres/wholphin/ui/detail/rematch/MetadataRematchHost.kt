package com.github.damontecres.wholphin.ui.detail.rematch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun MetadataRematchHost(
    viewModel: MetadataRematchViewModel = hiltViewModel(),
) {
    val rematchItem by viewModel.rematchItem.collectAsState()
    val metadataRematchResults by viewModel.metadataRematchResults.collectAsState()

    rematchItem?.let { item ->
        MetadataRematchDialog(
            itemTitle = item.title ?: "",
            initialQuery = item.title ?: "",
            results = metadataRematchResults,
            onSearch = viewModel::searchMetadataMatches,
            onApply = viewModel::applyMetadataMatch,
            onDismissRequest = viewModel::dismissRematch,
        )
    }
}
