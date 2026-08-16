package com.github.damontecres.wholphin.ui.util

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.withFrameNanos
import kotlin.math.abs

/**
 * Scrolls a horizontal [LazyListState] so [index] sits near [leadingMarginFraction] of the viewport,
 * instead of staying pinned to the leading edge until the item would scroll off-screen.
 *
 * Uses the same negative [LazyListState.animateScrollToItem] offset pattern as [TabRow].
 */
suspend fun LazyListState.scrollFocusedItemIntoRow(
    index: Int,
    leadingMarginFraction: Float = 0.25f,
) {
    if (index < 0) return
    repeat(2) { withFrameNanos { } }
    val viewportWidth = layoutInfo.viewportSize.width
    if (viewportWidth <= 0) {
        animateScrollToItem(index)
        return
    }
    val targetScrollOffset = -(viewportWidth * leadingMarginFraction).toInt()
    val visibleItem = layoutInfo.visibleItemsInfo.find { it.index == index }
    if (visibleItem != null) {
        // Re-scrolling an already visible item (e.g. after backing out of details) can nudge the
        // row and move TV focus to the adjacent item.
        val targetLeadingOffset = -targetScrollOffset
        val tolerance = (viewportWidth * 0.1f).toInt().coerceAtLeast(1)
        if (abs(visibleItem.offset - targetLeadingOffset) <= tolerance) {
            return
        }
    }
    animateScrollToItem(index, targetScrollOffset)
}

/**
 * Smoothly scrolls [index] to the start of the row without spring overshoot.
 */
suspend fun LazyListState.smoothScrollItemToStart(
    index: Int,
    durationMillis: Int = 400,
) {
    if (index < 0) return
    repeat(2) { withFrameNanos { } }
    val visible = layoutInfo.visibleItemsInfo.find { it.index == index }
    val distance =
        if (visible != null) {
            visible.offset.toFloat()
        } else {
            val itemSize = layoutInfo.visibleItemsInfo.firstOrNull()?.size
            if (itemSize == null) {
                scrollToItem(index)
                return
            }
            ((index - firstVisibleItemIndex) * itemSize - firstVisibleItemScrollOffset).toFloat()
        }
    if (abs(distance) < 1f) return
    animateScrollBy(
        distance,
        tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
    )
}
