package com.github.damontecres.wholphin.ui.main.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.LoadingState

@Composable
fun HomeSettingsGenreList(
    library: Library,
    genres: List<String>,
    loading: LoadingState,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    firstFocus: FocusRequester = remember { FocusRequester() },
) {
    LaunchedEffect(loading, genres) {
        if (loading == LoadingState.Success && genres.isNotEmpty()) {
            firstFocus.tryRequestFocus()
        }
    }
    Column(modifier = modifier) {
        TitleText(stringResource(R.string.choose_genre_for, library.name))
        when (loading) {
            LoadingState.Pending,
            LoadingState.Loading,
            -> {
                LoadingPage(modifier = Modifier.fillMaxHeight())
            }

            is LoadingState.Error -> {
                TitleText(loading.localizedMessage)
            }

            LoadingState.Success -> {
                LazyColumn(
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier =
                        modifier
                            .fillMaxHeight()
                            .focusRestorer(firstFocus),
                ) {
                    itemsIndexed(genres) { index, genre ->
                        HomeSettingsListItem(
                            selected = false,
                            headlineText = genre,
                            onClick = { onClick.invoke(genre) },
                            modifier = Modifier.ifElse(index == 0, Modifier.focusRequester(firstFocus)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun rememberGenreListState(
    library: Library,
    loadGenres: suspend (Library) -> List<String>,
): Pair<List<String>, LoadingState> {
    var genres by remember(library) { mutableStateOf(emptyList<String>()) }
    var loading by remember(library) { mutableStateOf<LoadingState>(LoadingState.Loading) }
    LaunchedEffect(library) {
        loading = LoadingState.Loading
        try {
            genres = loadGenres(library)
            loading = LoadingState.Success
        } catch (ex: Exception) {
            loading = LoadingState.Error(ex.localizedMessage ?: ex.toString(), ex)
        }
    }
    return genres to loading
}
