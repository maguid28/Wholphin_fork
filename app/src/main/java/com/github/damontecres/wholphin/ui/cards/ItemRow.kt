package com.github.damontecres.wholphin.ui.cards

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.components.CircularProgress
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.rememberInt
import com.github.damontecres.wholphin.ui.tryRequestFocus

@Composable
fun <T> ItemRow(
    title: String,
    items: List<T?>,
    onClickItem: (Int, T) -> Unit,
    onLongClickItem: (Int, T) -> Unit,
    cardContent: @Composable (
        index: Int,
        item: T?,
        modifier: Modifier,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
    ) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
    restoreFocusedIndex: Int? = null,
    showLoadMore: Boolean = false,
    isLoadingMore: Boolean = false,
    onClickLoadMore: () -> Unit = {},
    onLoadMoreFocus: (Int) -> Unit = {},
    loadMoreCardHeight: Dp = Cards.height2x3,
    loadMoreAspectRatio: Float = AspectRatios.TALL,
) {
    val initialPosition =
        remember(items.size, restoreFocusedIndex) {
            restoreFocusedIndex?.takeIf { it in items.indices } ?: 0
        }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialPosition)
    val firstFocus = remember { FocusRequester() }
    val focusRequester = remember { FocusRequester() }
    var position by rememberInt(initialPosition)

    val currentOnClickItem by rememberUpdatedState(onClickItem)
    val currentOnLongClickItem by rememberUpdatedState(onLongClickItem)
    val currentOnLoadMoreFocus by rememberUpdatedState(onLoadMoreFocus)

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier.focusProperties {
                onEnter = {
                    focusRequester.tryRequestFocus()
                }
            },
    ) {
        ItemRowTitle(title)

        LazyRow(
            state = state,
            horizontalArrangement = Arrangement.spacedBy(horizontalPadding),
            contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 8.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusGroup()
                    .focusRestorer(firstFocus)
                    .focusRequester(focusRequester),
        ) {
            itemsIndexed(items) { index, item ->
                val cardModifier =
                    remember(index, position) {
                        if (index == position) {
                            Modifier.focusRequester(firstFocus)
                        } else {
                            Modifier
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

                cardContent.invoke(
                    index,
                    item,
                    cardModifier,
                    onClick,
                    onLongClick,
                )
            }
            if (showLoadMore) {
                item {
                    val loadMoreIndex = items.size
                    ItemRowMoreCard(
                        onClick = {
                            position = loadMoreIndex
                            onClickLoadMore.invoke()
                        },
                        onLongClick = {},
                        cardHeight = loadMoreCardHeight,
                        aspectRatio = loadMoreAspectRatio,
                        isLoading = isLoadingMore,
                        modifier =
                            Modifier
                                .ifElse(position == loadMoreIndex, Modifier.focusRequester(firstFocus))
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        position = loadMoreIndex
                                        currentOnLoadMoreFocus.invoke(loadMoreIndex)
                                    }
                                },
                    )
                }
            }
        }
    }
}

@Composable
fun ItemRowMoreCard(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    cardHeight: Dp,
    aspectRatio: Float,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        interactionSource = interactionSource,
        colors =
            CardDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
            ),
        modifier =
            modifier
                .size(cardHeight * aspectRatio, cardHeight),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading) {
                CircularProgress(Modifier.size(32.dp))
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        tint = MaterialTheme.colorScheme.onSurface,
                        contentDescription = stringResource(R.string.more),
                    )
                    Text(
                        text = stringResource(R.string.more),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
@NonRestartableComposable
fun ItemRowTitle(
    title: String,
    modifier: Modifier = Modifier,
) = Text(
    text = title,
    style = MaterialTheme.typography.titleLarge,
    color = MaterialTheme.colorScheme.onBackground,
    modifier = modifier.padding(start = 8.dp),
)
