package com.github.damontecres.wholphin.ui.cards

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
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
import com.github.damontecres.wholphin.ui.rememberInt
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.HorizontalRowBringIntoViewSpec
import com.github.damontecres.wholphin.ui.util.scrollFocusedItemIntoRow
import com.github.damontecres.wholphin.ui.util.smoothScrollItemToStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
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
    savedFocusedColumn: Int? = null,
    detailReturnFocusSignal: Int = 0,
    detailReturnFocusColumn: Int? = null,
    suppressHorizontalScroll: Boolean = false,
    showLoadMore: Boolean = false,
    onClickLoadMore: suspend () -> Unit = {},
    onLoadMoreFocus: (Int) -> Unit = {},
    loadMoreCardHeight: Dp = Cards.height2x3,
    loadMoreAspectRatio: Float = AspectRatios.TALL,
) {
    val scope = rememberCoroutineScope()
    var isLoadingMore by remember { mutableStateOf(false) }
    var restoringMoreFocus by remember { mutableStateOf(false) }
    var pendingMoreFocus by remember { mutableStateOf(false) }
    var loadMoreFromIndex by remember { mutableIntStateOf(-1) }
    val keepMoreCard = showLoadMore || isLoadingMore || pendingMoreFocus || restoringMoreFocus
    val loadMoreIndex = items.size
    val maxFocusIndex = if (keepMoreCard) loadMoreIndex else items.lastIndex.coerceAtLeast(0)
    val initialPosition =
        remember(items.size, showLoadMore, restoreFocusedIndex, savedFocusedColumn) {
            restoreFocusedIndex?.takeIf { it in 0..maxFocusIndex }
                ?: savedFocusedColumn?.takeIf { it in 0..maxFocusIndex }
                ?: 0
        }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialPosition.coerceAtMost(maxFocusIndex))
    var position by rememberInt(initialPosition.coerceAtMost(maxFocusIndex))
    val focusRequesterCount =
        if (keepMoreCard) {
            items.size + 1
        } else {
            items.size.coerceAtLeast(1)
        }
    val itemFocusRequesters = remember { mutableStateListOf<FocusRequester>() }
    if (itemFocusRequesters.size < focusRequesterCount) {
        repeat(focusRequesterCount - itemFocusRequesters.size) {
            itemFocusRequesters.add(FocusRequester())
        }
    }
    val unusedFocus = remember { FocusRequester() }
    val moreFocus =
        if (keepMoreCard && loadMoreIndex in itemFocusRequesters.indices) {
            itemFocusRequesters[loadMoreIndex]
        } else {
            unusedFocus
        }
    val blockFocusExit by rememberUpdatedState(isLoadingMore || restoringMoreFocus || pendingMoreFocus)

    val currentOnClickItem by rememberUpdatedState(onClickItem)
    val currentOnLongClickItem by rememberUpdatedState(onLongClickItem)
    val currentOnLoadMoreFocus by rememberUpdatedState(onLoadMoreFocus)
    val currentOnClickLoadMore by rememberUpdatedState(onClickLoadMore)
    val currentMoreFocus by rememberUpdatedState(moreFocus)

    val onMore = position >= loadMoreIndex && keepMoreCard
    var suppressFocusScroll by remember { mutableStateOf(false) }
    var loadedMoreForCurrentFocus by remember { mutableStateOf(false) }

    fun focusRequesterFor(index: Int): FocusRequester =
        itemFocusRequesters.getOrElse(index) { itemFocusRequesters.first() }

    fun scrollRowToFocusedItem(index: Int) {
        scope.launch {
            state.scrollFocusedItemIntoRow(index)
        }
    }

    fun onItemFocused(index: Int) {
        if (position != index) {
            position = index
        }
        if (!suppressFocusScroll && !suppressHorizontalScroll) {
            scrollRowToFocusedItem(index)
        }
    }

    fun startLoadMore() {
        if (isLoadingMore || !showLoadMore || pendingMoreFocus || restoringMoreFocus) return
        position = loadMoreIndex
        loadMoreFromIndex = items.size
        pendingMoreFocus = true
        isLoadingMore = true
        moreFocus.tryRequestFocus()
        scope.launch {
            try {
                currentOnClickLoadMore()
            } finally {
                isLoadingMore = false
            }
        }
    }

    LaunchedEffect(keepMoreCard, items.size) {
        val maxIndex = if (keepMoreCard) loadMoreIndex else items.lastIndex.coerceAtLeast(0)
        if (position > maxIndex) {
            position = maxIndex
        }
    }

    LaunchedEffect(isLoadingMore) {
        if (isLoadingMore) {
            if (onMore) {
                currentMoreFocus.tryRequestFocus()
            }
            return@LaunchedEffect
        }
        if (!pendingMoreFocus) return@LaunchedEffect
        restoringMoreFocus = true
        suppressFocusScroll = true
        try {
            suspend fun restoreTo(
                index: Int,
                requester: FocusRequester,
                tag: String,
            ): Boolean {
                position = index
                repeat(8) {
                    withFrameNanos { }
                }
                state.smoothScrollItemToStart(index)
                repeat(10) { attempt ->
                    if (attempt > 0) {
                        delay(50)
                    }
                    withFrameNanos { }
                    if (requester.tryRequestFocus(tag)) {
                        return true
                    }
                }
                return false
            }
            val firstNewIndex = loadMoreFromIndex
            when {
                firstNewIndex in items.indices ->
                    restoreTo(
                        firstNewIndex,
                        focusRequesterFor(firstNewIndex),
                        "load_more_new",
                    )
                items.isNotEmpty() ->
                    restoreTo(
                        items.lastIndex,
                        focusRequesterFor(items.lastIndex),
                        "load_more_end",
                    )
            }
        } finally {
            delay(100)
            suppressFocusScroll = false
            restoringMoreFocus = false
            pendingMoreFocus = false
        }
    }

    LaunchedEffect(restoreFocusedIndex, showLoadMore, items.size) {
        val index = restoreFocusedIndex?.takeIf { it in 0..maxFocusIndex } ?: return@LaunchedEffect
        position = index
        repeat(10) {
            delay(50)
            state.scrollFocusedItemIntoRow(index)
            withFrameNanos { }
            val focusTarget = if (index == loadMoreIndex && keepMoreCard) moreFocus else focusRequesterFor(index)
            if (focusTarget.tryRequestFocus("item_row_restore")) {
                return@LaunchedEffect
            }
        }
    }

    LaunchedEffect(detailReturnFocusSignal, detailReturnFocusColumn) {
        if (detailReturnFocusSignal == 0) return@LaunchedEffect
        val index = detailReturnFocusColumn?.takeIf { it in 0..maxFocusIndex } ?: return@LaunchedEffect
        position = index
        suppressFocusScroll = true
        try {
            repeat(15) { attempt ->
                if (attempt > 0) {
                    delay(50)
                }
                withFrameNanos { }
                val focusTarget =
                    if (index == loadMoreIndex && keepMoreCard) {
                        moreFocus
                    } else {
                        focusRequesterFor(index)
                    }
                if (focusTarget.tryRequestFocus("detail_return")) {
                    return@LaunchedEffect
                }
            }
        } finally {
            delay(100)
            suppressFocusScroll = false
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier.focusProperties {
                onEnter = {
                    focusRequesterFor(position.coerceIn(0, itemFocusRequesters.lastIndex)).tryRequestFocus()
                }
                onExit = {
                    if (blockFocusExit) {
                        cancelFocusChange()
                    }
                }
            },
    ) {
        ItemRowTitle(title)

        CompositionLocalProvider(
            LocalBringIntoViewSpec provides
                if (restoringMoreFocus || suppressFocusScroll) {
                    HorizontalRowBringIntoViewSpec.None
                } else {
                    HorizontalRowBringIntoViewSpec.Default
                },
        ) {
            LazyRow(
                state = state,
                horizontalArrangement = Arrangement.spacedBy(horizontalPadding),
                contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 8.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusGroup()
                        .focusProperties {
                            onExit = {
                                if (blockFocusExit) {
                                    cancelFocusChange()
                                }
                            }
                        },
            ) {
                itemsIndexed(items) { index, item ->
                    val cardModifier =
                        remember(index, focusRequesterCount) {
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

                    cardContent.invoke(
                        index,
                        item,
                        cardModifier,
                        onClick,
                        onLongClick,
                    )
                }
                if (keepMoreCard) {
                    item(key = "load-more") {
                        ItemRowMoreCard(
                            onClick = { startLoadMore() },
                            onLongClick = {},
                            cardHeight = loadMoreCardHeight,
                            aspectRatio = loadMoreAspectRatio,
                            isLoading = isLoadingMore,
                            modifier =
                                Modifier
                                    .focusRequester(moreFocus)
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            onItemFocused(loadMoreIndex)
                                            currentOnLoadMoreFocus.invoke(loadMoreIndex)
                                            if (!suppressFocusScroll && !loadedMoreForCurrentFocus) {
                                                loadedMoreForCurrentFocus = true
                                                startLoadMore()
                                            }
                                        } else if (!isLoadingMore && !pendingMoreFocus && !restoringMoreFocus) {
                                            loadedMoreForCurrentFocus = false
                                        }
                                    },
                        )
                    }
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.alpha(if (isLoading) 0.35f else 1f),
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
            if (isLoading) {
                CircularProgress(Modifier.size(32.dp))
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
