package com.github.damontecres.wholphin.ui.util

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.withFrameNanos

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
    animateScrollToItem(index, -(viewportWidth * leadingMarginFraction).toInt())
}
