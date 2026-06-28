package com.github.damontecres.wholphin.ui.preferences

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.preferences.AppFont
import com.github.damontecres.wholphin.ui.theme.AppFontOptions
import com.github.damontecres.wholphin.ui.theme.appFontDisplayName
import com.github.damontecres.wholphin.ui.theme.appFontFamily

@Composable
fun FontPreference(
    title: String,
    selectedFont: AppFont,
    onValueChange: (AppFont) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val fonts = AppFontOptions
    val selectedIndex = fonts.indexOf(selectedFont).takeIf { it >= 0 } ?: 0
    ChoicePreference(
        title = title,
        summary = null,
        summaryContent = {
            FontPreferenceLabel(
                font = selectedFont,
                style = PreferenceSummaryStyle,
            )
        },
        possibleValues = fonts,
        selectedIndex = selectedIndex,
        onValueChange = { index ->
            fonts.getOrNull(index)?.let(onValueChange)
        },
        modifier = modifier,
        interactionSource = interactionSource,
        valueDisplay = { _, font ->
            FontPreferenceLabel(font = font)
        },
    )
}

@Composable
private fun FontPreferenceLabel(
    font: AppFont,
    style: androidx.compose.ui.text.TextStyle = PreferenceTitleStyle,
) {
    Text(
        text = appFontDisplayName(font),
        style = style.copy(fontFamily = appFontFamily(font)),
    )
}
