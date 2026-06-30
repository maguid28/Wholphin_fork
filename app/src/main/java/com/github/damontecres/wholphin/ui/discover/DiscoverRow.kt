package com.github.damontecres.wholphin.ui.discover

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.github.damontecres.wholphin.ui.util.scrollFocusedItemIntoRow
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.ui.cards.DiscoverItemCard
import com.github.damontecres.wholphin.ui.cards.DiscoverViewMoreCard
import com.github.damontecres.wholphin.ui.cards.ItemRowTitle
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.rememberInt
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.HorizontalRowBringIntoViewSpec
import com.github.damontecres.wholphin.util.DataLoadingState

@Composable
fun DiscoverRow(
    row: DiscoverRowData,
    onClickItem: (Int, DiscoverItem) -> Unit,
    onLongClickItem: (Int, DiscoverItem) -> Unit,
    onCardFocus: (Int) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    enableViewMore: Boolean = false,
    onClickViewMore: () -> Unit = {},
) {
    when (val state = row.items) {
        is DataLoadingState.Error -> {
            ErrorMessage(state.message, state.exception, modifier)
        }

        DataLoadingState.Loading,
        DataLoadingState.Pending,
        -> {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = modifier,
            ) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.loading),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        is DataLoadingState.Success<List<DiscoverItem>> -> {
            DiscoverItemRow(
                title = row.title,
                items = state.data,
                onClickItem = onClickItem,
                onLongClickItem = onLongClickItem,
                onCardFocus = onCardFocus,
                enableViewMore = enableViewMore,
                onClickViewMore = onClickViewMore,
                modifier = modifier.focusRequester(focusRequester),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DiscoverItemRow(
    title: String,
    items: List<DiscoverItem?>,
    onClickItem: (Int, DiscoverItem) -> Unit,
    onLongClickItem: (Int, DiscoverItem) -> Unit,
    onCardFocus: (Int) -> Unit,
    enableViewMore: Boolean,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
    onClickViewMore: () -> Unit = {},
) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val itemCount = items.size + if (enableViewMore) 1 else 0
    val itemFocusRequesters = remember(itemCount) { List(itemCount.coerceAtLeast(1)) { FocusRequester() } }
    val focusRequester = remember { FocusRequester() }
    var position by rememberInt()

    val currentOnClickItem by rememberUpdatedState(onClickItem)
    val currentOnLongClickItem by rememberUpdatedState(onLongClickItem)
    val currentOnCardFocus by rememberUpdatedState(onCardFocus)

    fun onItemFocused(index: Int) {
        position = index
        currentOnCardFocus.invoke(index)
        scope.launch {
            state.scrollFocusedItemIntoRow(index)
        }
    }

    val focusRestorerTarget =
        itemFocusRequesters.getOrElse(position.coerceIn(0, itemFocusRequesters.lastIndex)) {
            itemFocusRequesters.first()
        }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier.focusProperties {
                onEnter = {
                    itemFocusRequesters.getOrElse(position.coerceIn(0, itemFocusRequesters.lastIndex)) {
                        itemFocusRequesters.first()
                    }.tryRequestFocus()
                }
            },
    ) {
        ItemRowTitle(title)

        CompositionLocalProvider(
            LocalBringIntoViewSpec provides HorizontalRowBringIntoViewSpec.Default,
        ) {
            LazyRow(
            state = state,
            horizontalArrangement = Arrangement.spacedBy(horizontalPadding),
            contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 8.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusGroup()
                    .focusRestorer(focusRestorerTarget)
                    .focusRequester(focusRequester),
        ) {
            itemsIndexed(items) { index, item ->
                val cardModifier =
                    remember(index, itemCount) {
                        Modifier
                            .focusRequester(itemFocusRequesters.getOrElse(index) { itemFocusRequesters.first() })
                            .onFocusChanged {
                                if (it.isFocused) {
                                    onItemFocused(index)
                                }
                            }
                    }

                val onClick =
                    remember(index, item) {
                        {
                            position = index
                            if (item != null) currentOnClickItem(index, item)
                        }
                    }

                val onLongClick =
                    remember(index, item) {
                        {
                            position = index
                            if (item != null) currentOnLongClickItem(index, item)
                        }
                    }

                DiscoverItemCard(
                    item = item,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    showOverlay = true,
                    modifier = cardModifier,
                )
            }
            if (enableViewMore) {
                item {
                    DiscoverViewMoreCard(
                        onClick =
                            remember {
                                {
                                    position = items.size
                                    onClickViewMore.invoke()
                                }
                            },
                        onLongClick = {},
                        modifier =
                            Modifier
                                .focusRequester(
                                    itemFocusRequesters.getOrElse(items.size) { itemFocusRequesters.first() },
                                )
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        onItemFocused(items.size)
                                    }
                                },
                    )
                }
            }
        }
        }
    }
}
