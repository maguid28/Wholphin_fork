package com.github.damontecres.wholphin.ui.util

import androidx.compose.foundation.gestures.BringIntoViewSpec

/**
 * Positions the focused item in a horizontal row away from the leading edge instead of flush left.
 *
 * Without this, Compose's default bring-into-view keeps the focus ring pinned to the start of the
 * row until the item would scroll off the trailing edge.
 */
class HorizontalRowBringIntoViewSpec(
    private val fractionFromStart: Float = 0.25f,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float {
        val targetOffset = containerSize * fractionFromStart
        return offset - targetOffset
    }

    companion object {
        val Default = HorizontalRowBringIntoViewSpec()
    }
}
