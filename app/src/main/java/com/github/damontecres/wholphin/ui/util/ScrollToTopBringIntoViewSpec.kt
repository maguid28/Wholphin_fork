package com.github.damontecres.wholphin.ui.util

import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.LazyColumn

/**
 * Overrides scrolling so that the item being scrolled to is at the top of the view offset by the provided pixels
 *
 * Note: the offset is necessary for anything that is focuseable, but has content before (eg a title) that needs to be displayed too
 *
 * Note: this applies to ALL scrollable composables within its scope, so a [LazyColumn] of [androidx.compose.foundation.lazy.LazyRow]s likely needs nested [LocalBringIntoViewSpec] overrides
 *
 * [suppressScroll] can be used to temporarily disable the re-anchoring scroll. This is needed when
 * focus is restored to an already-positioned item (eg returning from the nav drawer), where
 * re-anchoring would otherwise scroll the list and visibly jump the content.
 *
 * While [suppressScroll] is active the spec performs no scrolling at all (returns `0`). This is used
 * during the nav-drawer -> content focus handoff: the handoff also restores focus to the row the user
 * actually left (see HomePage), which is already on-screen, so no bring-into-view scroll is needed and
 * the content stays exactly where it was.
 *
 * Example:
 * ```kotlin
 * val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current
 * CompositionLocalProvider(LocalBringIntoViewSpec provides ScrollToTopBringIntoViewSpec(spaceAbovePx)) {
 *     LazyColumn{
 *         items(list){
 *             CompositionLocalProvider(LocalBringIntoViewSpec provides defaultBringIntoViewSpec) {
 *                 // Content
 *             }
 *         }
 *     }
 * }
 * ```
 */
class ScrollToTopBringIntoViewSpec(
    val spaceAbovePx: Float = 100f,
    private val suppressScroll: () -> Boolean = { false },
) : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float {
        if (suppressScroll()) {
            return 0f
        }
        return offset - spaceAbovePx
    }
}
