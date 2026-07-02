package com.github.damontecres.wholphin.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.theme.NdorfinTheme
import com.github.damontecres.wholphin.ui.tryRequestFocus
import timber.log.Timber

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabRow(
    selectedTabIndex: Int,
    tabs: List<String>,
    focusRequesters: List<FocusRequester>,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tabTextSize: TextUnit = 16.sp,
    tabKeys: List<String> = tabs,
    onMove: ((fromIndex: Int, toIndex: Int) -> Unit)? = null,
) {
    val state = rememberLazyListState()
    var reorderTabIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(selectedTabIndex) {
        if (selectedTabIndex >= 0 && reorderTabIndex < 0) {
            state.animateScrollToItem(selectedTabIndex, -(state.layoutInfo.viewportSize.width / 3.5).toInt())
        }
    }
    LaunchedEffect(reorderTabIndex, tabs) {
        if (reorderTabIndex >= 0) {
            focusRequesters.getOrNull(reorderTabIndex)?.tryRequestFocus("tab_reorder")
            state.animateScrollToItem(reorderTabIndex, -(state.layoutInfo.viewportSize.width / 3.5).toInt())
        }
    }
    var rowHasFocus by remember { mutableStateOf(false) }

    val currentSelectedTabIndex by rememberUpdatedState(selectedTabIndex)
    val currentFocusRequesters by rememberUpdatedState(focusRequesters)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnMove by rememberUpdatedState(onMove)
    val lazyItemKeys = remember(tabs, tabKeys) { tabRowItemKeys(tabs, tabKeys) }

    LazyRow(
        state = state,
        modifier =
            modifier
                .onFocusChanged {
                    rowHasFocus = it.hasFocus
                }.focusGroup()
                .focusProperties {
                    onEnter = {
                        // If entering from left or right, use last or first tab
                        // Otherwise use the selected tab
                        val index = currentSelectedTabIndex
                        val requesters = currentFocusRequesters
                        Timber.v("onEnter requestedFocusDirection=$requestedFocusDirection, selectedTabIndex=$index")
                        val focusRequester =
                            if (requestedFocusDirection == FocusDirection.Left) {
                                requesters.lastOrNull()
                            } else if (requestedFocusDirection == FocusDirection.Right) {
                                requesters.firstOrNull()
                            } else {
                                requesters.getOrNull(index)
                            }
                        (focusRequester ?: FocusRequester.Default).tryRequestFocus()
                    }
                },
    ) {
        itemsIndexed(
            items = tabs,
            key = { index, _ -> lazyItemKeys[index] },
        ) { index, tabTitle ->
            val interactionSource = remember { MutableInteractionSource() }
            val onTabClick =
                remember(index) {
                    {
                        if (reorderTabIndex >= 0) {
                            reorderTabIndex = -1
                        } else {
                            currentOnClick(index)
                        }
                    }
                }
            val onMoveTab =
                remember(index) {
                    { direction: Int ->
                        val from = reorderTabIndex.takeIf { it >= 0 } ?: index
                        val to = (from + direction).coerceIn(0, tabs.lastIndex)
                        if (from != to) {
                            currentOnMove?.invoke(from, to)
                            reorderTabIndex = to
                        }
                    }
                }
            Tab(
                title = tabTitle,
                selected = index == selectedTabIndex,
                rowActive = rowHasFocus,
                reordering = index == reorderTabIndex,
                textSize = tabTextSize,
                interactionSource = interactionSource,
                onClick = onTabClick,
                onLongClick =
                    currentOnMove?.let {
                        {
                            reorderTabIndex = index
                        }
                    },
                onMove = onMoveTab,
                onCancelReorder = {
                    reorderTabIndex = -1
                },
                modifier = Modifier.focusRequester(focusRequesters.getOrElse(index) { FocusRequester() }),
            )
        }
    }
}

internal fun tabRowItemKeys(
    tabs: List<String>,
    tabKeys: List<String>,
): List<String> =
    buildList {
        val usedKeys = mutableSetOf<String>()
        tabs.forEachIndexed { index, tabTitle ->
            val baseKey =
                tabKeys.getOrNull(index)?.takeUnless { it.isBlank() }
                    ?: tabTitle.takeUnless { it.isBlank() }
                    ?: "tab:$index"
            var key = baseKey
            var suffix = 0
            while (!usedKeys.add(key)) {
                key = "$index:$suffix:$baseKey"
                suffix += 1
            }
            add(key)
        }
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Tab(
    title: String,
    selected: Boolean,
    rowActive: Boolean,
    reordering: Boolean = false,
    textSize: TextUnit = 16.sp,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onMove: ((direction: Int) -> Unit)? = null,
    onCancelReorder: () -> Unit = {},
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    var tabWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    val focused by interactionSource.collectIsFocusedAsState()
    val contentColor =
        if (reordering) {
            MaterialTheme.colorScheme.border
        } else if (rowActive || selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = .5f)
        }
    Box(
        modifier =
            modifier
                .onPreviewKeyEvent { event ->
                    if (!reordering) {
                        return@onPreviewKeyEvent false
                    }

                    when (event.nativeKeyEvent.keyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                        AndroidKeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT,
                        -> {
                            if (event.type == KeyEventType.KeyDown) {
                                onMove?.invoke(-1)
                            }
                            true
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                        AndroidKeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT,
                        -> {
                            if (event.type == KeyEventType.KeyDown) {
                                onMove?.invoke(1)
                            }
                            true
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER,
                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                        AndroidKeyEvent.KEYCODE_BACK,
                        -> {
                            if (event.type == KeyEventType.KeyDown) {
                                onCancelReorder()
                            }
                            true
                        }

                        else -> false
                    }
                }.combinedClickable(
                    enabled = true,
                    interactionSource = interactionSource,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    indication = null,
                ).onGloballyPositioned {
                    tabWidth = with(density) { it.size.width.toDp() }
                },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier,
        ) {
            Text(
                text = title,
                fontSize = textSize,
                color = contentColor,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            TabIndicator(
                selected = selected,
                rowActive = rowActive,
                focused = focused,
                reordering = reordering,
                tabWidth = tabWidth,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
fun TabIndicator(
    selected: Boolean,
    rowActive: Boolean,
    focused: Boolean,
    reordering: Boolean = false,
    tabWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val width by animateDpAsState(if (reordering || rowActive && focused) tabWidth else tabWidth * .25f)
    val backgroundColor =
        if (reordering || rowActive && focused) {
            MaterialTheme.colorScheme.border
        } else if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            Color.Transparent
        }
    Box(
        modifier =
            modifier
                .height(2.dp)
                .fillMaxWidth()
                .width(width)
                .background(backgroundColor),
    )
}

@PreviewTvSpec
@Composable
private fun TabRowPreview() {
    NdorfinTheme {
        Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
            TabRow(
                selectedTabIndex = 1,
                tabs = listOf("Tab 1", "Tab 2", "Tab 3"),
                focusRequesters = listOf(),
                onClick = {},
            )
            Tab(
                title = "This is a Tab",
                selected = true,
                rowActive = true,
                onClick = {},
                interactionSource = remember { MutableInteractionSource() },
                modifier = Modifier.width(120.dp),
            )
        }
    }
}
