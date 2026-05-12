package com.github.damontecres.wholphin.ui.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.AppThemeColors
import com.github.damontecres.wholphin.ui.theme.AppThemeColorOptions
import com.github.damontecres.wholphin.ui.theme.colors.CustomThemeColorChoices
import com.github.damontecres.wholphin.ui.theme.colors.CustomThemeTone
import com.github.damontecres.wholphin.ui.theme.colors.CustomThemeToneOptions
import com.github.damontecres.wholphin.ui.theme.colors.DefaultCustomThemeColorChoices
import com.github.damontecres.wholphin.ui.theme.getThemeColors

@Composable
fun ThemeColorPreference(
    title: String,
    selectedTheme: AppThemeColors,
    customThemeColorChoices: CustomThemeColorChoices,
    onValueChange: (AppThemeColors) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val themes = AppThemeColorOptions
    val labels = stringArrayResource(R.array.app_theme_colors).toList()
    val selectedIndex = themes.indexOf(selectedTheme).takeIf { it >= 0 } ?: 0
    ChoicePreference(
        title = title,
        summary = labels.getOrElse(selectedIndex) { labels.firstOrNull().orEmpty() },
        possibleValues = themes,
        selectedIndex = selectedIndex,
        onValueChange = { index ->
            themes.getOrNull(index)?.let(onValueChange)
        },
        modifier = modifier,
        interactionSource = interactionSource,
        valueDisplay = { index, theme ->
            ThemeColorRow(
                label = labels.getOrElse(index) { theme.name },
                theme = theme,
                customThemeColorChoices = customThemeColorChoices,
            )
        },
    )
}

@Composable
private fun ThemeColorRow(
    label: String,
    theme: AppThemeColors,
    customThemeColorChoices: CustomThemeColorChoices = DefaultCustomThemeColorChoices,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemeColorSwatch(theme, customThemeColorChoices)
        Text(label)
    }
}

@Composable
private fun ThemeColorSwatch(
    theme: AppThemeColors,
    customThemeColorChoices: CustomThemeColorChoices = DefaultCustomThemeColorChoices,
) {
    val scheme = getThemeColors(theme, customThemeColorChoices).darkScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorDot(
            color = scheme.primary,
            size = 24,
        )
        ColorDot(
            color = scheme.secondary,
            size = 16,
        )
        ColorDot(
            color = scheme.tertiary,
            size = 16,
        )
        ColorDot(
            color = scheme.surfaceVariant,
            size = 12,
        )
    }
}

@Composable
fun CustomThemeTonePreference(
    title: String,
    selectedIndex: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val safeIndex = selectedIndex.coerceIn(CustomThemeToneOptions.indices)
    val selectedTone = CustomThemeToneOptions[safeIndex]
    ChoicePreference(
        title = title,
        summary = stringResource(selectedTone.label),
        possibleValues = CustomThemeToneOptions,
        selectedIndex = safeIndex,
        onValueChange = onValueChange,
        modifier = modifier,
        interactionSource = interactionSource,
        valueDisplay = { _, tone ->
            CustomThemeToneRow(tone)
        },
    )
}

@Composable
private fun CustomThemeToneRow(tone: CustomThemeTone) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorDot(
            color = tone.color,
            size = 24,
        )
        Text(stringResource(tone.label))
    }
}

@Composable
private fun ColorDot(
    color: androidx.compose.ui.graphics.Color,
    size: Int,
) {
    Box(
        modifier =
            Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .22f),
                    shape = CircleShape,
                ),
    )
}
