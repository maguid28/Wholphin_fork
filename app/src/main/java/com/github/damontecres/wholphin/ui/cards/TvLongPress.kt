package com.github.damontecres.wholphin.ui.cards

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

private val activationKeys = listOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/**
 * Android TV remotes often deliver long-press as repeated key events instead of [android.view.View.onLongClickListener].
 */
fun Modifier.tvLongPress(
    onLongPress: () -> Unit,
): Modifier =
    composed {
        var holdHandled by remember { mutableStateOf(false) }
        val currentOnLongPress by rememberUpdatedState(onLongPress)
        onPreviewKeyEvent { event ->
            when {
                event.type == KeyEventType.KeyDown &&
                    event.key in activationKeys &&
                    (event.nativeKeyEvent.isLongPress || event.nativeKeyEvent.repeatCount > 0) &&
                    !holdHandled -> {
                    holdHandled = true
                    currentOnLongPress.invoke()
                    true
                }

                event.type == KeyEventType.KeyUp &&
                    event.key in activationKeys -> {
                    val consume = holdHandled
                    holdHandled = false
                    consume
                }

                else -> false
            }
        }
    }
