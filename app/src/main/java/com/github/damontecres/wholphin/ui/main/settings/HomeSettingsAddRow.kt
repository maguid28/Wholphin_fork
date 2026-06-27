package com.github.damontecres.wholphin.ui.main.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.ifElse

@Composable
fun HomeSettingsAddRow(
    libraries: List<Library>,
    showDiscover: Boolean,
    onClick: (Library) -> Unit,
    onClickGenres: (Library) -> Unit,
    onClickMeta: (MetaRowType) -> Unit,
    modifier: Modifier,
    firstFocus: FocusRequester = remember { FocusRequester() },
) {
//    LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
    Column(modifier = modifier) {
        TitleText(stringResource(R.string.add_row))
        LazyColumn(
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                modifier
                    .fillMaxHeight()
                    .focusRestorer(firstFocus),
        ) {
            itemsIndexed(
                listOf(
                    MetaRowType.CONTINUE_WATCHING,
                    MetaRowType.NEXT_UP,
                    MetaRowType.COMBINED_CONTINUE_WATCHING,
                ),
            ) { index, type ->
                HomeSettingsListItem(
                    selected = false,
                    headlineText = stringResource(type.stringId),
                    onClick = { onClickMeta.invoke(type) },
                    modifier = Modifier.ifElse(index == 0, Modifier.focusRequester(firstFocus)),
                )
            }
            item {
                TitleText(stringResource(R.string.categories))
                HorizontalDivider()
            }
            itemsIndexed(
                listOf(
                    MetaRowType.TOP_RATED_MOVIES,
                    MetaRowType.TOP_RATED_TV,
                    MetaRowType.POPULAR_MOVIES,
                    MetaRowType.POPULAR_TV,
                    MetaRowType.RECENTLY_ADDED,
                    MetaRowType.RECENTLY_RELEASED,
                    MetaRowType.UNWATCHED_MOVIES,
                    MetaRowType.UNWATCHED_TV,
                ),
            ) { _, type ->
                HomeSettingsListItem(
                    selected = false,
                    headlineText = stringResource(type.stringId),
                    onClick = { onClickMeta.invoke(type) },
                    modifier = Modifier,
                )
            }
            val genreLibraries =
                libraries.filter {
                    LibraryRowType.GENRES in getSupportedRowTypes(it)
                }
            if (genreLibraries.isNotEmpty()) {
                item {
                    TitleText(stringResource(R.string.genres))
                    HorizontalDivider()
                }
                itemsIndexed(genreLibraries) { _, library ->
                    HomeSettingsListItem(
                        selected = false,
                        headlineText = stringResource(R.string.genres_in, library.name),
                        onClick = { onClickGenres.invoke(library) },
                        modifier = Modifier,
                    )
                }
            }
            item {
                TitleText(stringResource(R.string.seasonal))
                HorizontalDivider()
            }
            itemsIndexed(
                listOf(
                    MetaRowType.HALLOWEEN,
                    MetaRowType.CHRISTMAS,
                ),
            ) { _, type ->
                HomeSettingsListItem(
                    selected = false,
                    headlineText = stringResource(type.stringId),
                    supportingContent = {
                        Text(
                            stringResource(
                                R.string.seasonal_month_summary,
                                stringResource(type.monthStringId!!),
                            ),
                        )
                    },
                    onClick = { onClickMeta.invoke(type) },
                    modifier = Modifier,
                )
            }
            item {
                TitleText(stringResource(R.string.library))
                HorizontalDivider()
            }
            itemsIndexed(libraries) { index, library ->
                HomeSettingsListItem(
                    selected = false,
                    headlineText = library.name,
                    supportingContent = {
                        Text(stringResource(R.string.library_home_sections_summary))
                    },
                    onClick = { onClick.invoke(library) },
                    modifier = Modifier, // .ifElse(index == 0, Modifier.focusRequester(firstFocus)),
                )
            }
            item {
                TitleText(stringResource(R.string.more))
                HorizontalDivider()
            }
            itemsIndexed(
                listOf(
                    MetaRowType.FAVORITES,
                    MetaRowType.COLLECTION,
                    MetaRowType.PLAYLIST,
                ),
            ) { index, type ->
                HomeSettingsListItem(
                    selected = false,
                    headlineText = stringResource(type.stringId),
                    onClick = { onClickMeta.invoke(type) },
                    modifier = Modifier,
                )
            }
            if (showDiscover) {
                item {
                    HomeSettingsListItem(
                        selected = false,
                        headlineText = stringResource(MetaRowType.DISCOVER.stringId),
                        onClick = { onClickMeta.invoke(MetaRowType.DISCOVER) },
                        modifier = Modifier,
                    )
                }
            }
        }
    }
}

enum class MetaRowType(
    @param:StringRes val stringId: Int,
    @param:StringRes val monthStringId: Int? = null,
) {
    CONTINUE_WATCHING(R.string.continue_watching),
    NEXT_UP(R.string.next_up),
    COMBINED_CONTINUE_WATCHING(R.string.combine_continue_next),
    TOP_RATED_MOVIES(R.string.top_rated_movies),
    TOP_RATED_TV(R.string.top_rated_tv),
    POPULAR_MOVIES(R.string.popular_movies),
    POPULAR_TV(R.string.popular_tv),
    RECENTLY_ADDED(R.string.recently_added),
    RECENTLY_RELEASED(R.string.recently_released),
    UNWATCHED_MOVIES(R.string.unwatched_movies),
    UNWATCHED_TV(R.string.unwatched_tv),
    HALLOWEEN(R.string.halloween, R.string.october),
    CHRISTMAS(R.string.christmas, R.string.december),
    FAVORITES(R.string.favorites),
    DISCOVER(R.string.discover),
    COLLECTION(R.string.collection),
    PLAYLIST(R.string.playlist),
}
