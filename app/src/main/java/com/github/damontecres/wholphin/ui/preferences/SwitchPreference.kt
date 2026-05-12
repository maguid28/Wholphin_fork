package com.github.damontecres.wholphin.ui.preferences

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.SwitchColors
import androidx.tv.material3.SwitchDefaults
import com.github.damontecres.wholphin.preferences.AppThemeColors
import com.github.damontecres.wholphin.ui.theme.LocalTheme

@Composable
fun SwitchPreference(
    title: String,
    value: Boolean,
    onClick: () -> Unit,
    summaryOn: String?,
    summaryOff: String?,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) = SwitchPreference(
    title = title,
    value = value,
    onClick = onClick,
    modifier = modifier,
    summary = if (value) summaryOn else summaryOff,
    onLongClick = onLongClick,
    interactionSource = interactionSource,
)

@Composable
fun SwitchPreference(
    title: String,
    value: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    ListItem(
        selected = false,
        onClick = onClick,
        onLongClick = onLongClick,
        headlineContent = {
            PreferenceTitle(title)
        },
        supportingContent = {
            PreferenceSummary(summary)
        },
        trailingContent = {
            Switch(
                checked = value,
                onCheckedChange = { onClick.invoke() },
                colors = SwitchColors(),
            )
        },
        interactionSource = interactionSource,
        modifier = modifier,
    )
}

@Composable
fun SwitchColors(): SwitchColors {
    val theme = LocalTheme.current
    return when (theme) {
        AppThemeColors.UNRECOGNIZED,
        AppThemeColors.PURPLE,
        AppThemeColors.BLUE,
        -> {
            SwitchDefaults.colors()
        }

        AppThemeColors.GREEN,
        AppThemeColors.ORANGE,
        AppThemeColors.BOLD_BLUE,
        AppThemeColors.OLED_BLACK,
        AppThemeColors.NEON_POP,
        AppThemeColors.AURORA,
        AppThemeColors.CYBERPUNK,
        AppThemeColors.SUNSET,
        AppThemeColors.DEEP_OCEAN,
        AppThemeColors.SAKURA,
        AppThemeColors.EMBER,
        AppThemeColors.MINT,
        AppThemeColors.ELECTRIC_VIOLET,
        AppThemeColors.TEAL_GLOW,
        AppThemeColors.ROSE,
        AppThemeColors.GOLD,
        AppThemeColors.EMERALD,
        AppThemeColors.LIME_GLOW,
        AppThemeColors.FOREST,
        AppThemeColors.JADE,
        AppThemeColors.MOSS,
        AppThemeColors.MATRIX,
        AppThemeColors.AURORA_GREEN,
        AppThemeColors.SEAFOAM,
        AppThemeColors.CHARTREUSE,
        AppThemeColors.PINE_GLOW,
        AppThemeColors.NEON_MINT,
        AppThemeColors.MALACHITE,
        AppThemeColors.TROPICAL_PUNCH,
        AppThemeColors.ARCTIC_BLOOM,
        AppThemeColors.LASER_LIME,
        AppThemeColors.MIAMI_NEON,
        AppThemeColors.COSMIC_CANDY,
        AppThemeColors.GLACIER_CYAN,
        AppThemeColors.DRAGONFRUIT,
        AppThemeColors.VOLCANIC_TEAL,
        AppThemeColors.MOONLIT_PLUM,
        AppThemeColors.SOLAR_FLARE,
        AppThemeColors.RAINBOW_GLASS,
        AppThemeColors.PEACOCK,
        AppThemeColors.ELECTRIC_NEON,
        AppThemeColors.CUSTOM,
        -> {
            SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
