package com.github.damontecres.wholphin.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.wholphin.preferences.AppThemeColors
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.theme.LocalTheme
import com.github.damontecres.wholphin.ui.theme.WholphinTheme

@Composable
fun WatchedIcon(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Default.Check,
        contentDescription = null,
        tint = WatchedIconColor(),
        modifier =
            modifier
                .background(WatchedIconBackground(), shape = CircleShape)
                .border(.5.dp, Color.Black, CircleShape)
                .padding(2.dp),
    )
}

@Composable
fun WatchedIconBackground(): Color =
    when (LocalTheme.current) {
        AppThemeColors.UNRECOGNIZED,
        AppThemeColors.PURPLE,
        AppThemeColors.BLUE,
        AppThemeColors.GREEN,
        AppThemeColors.ORANGE,
        AppThemeColors.BOLD_BLUE,
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
        AppThemeColors.ELECTRIC_INDIGO,
        AppThemeColors.CUSTOM,
        -> MaterialTheme.colorScheme.border.copy(alpha = 1f)

        AppThemeColors.OLED_BLACK -> MaterialTheme.colorScheme.secondaryContainer
    }

@Composable
fun WatchedIconColor(): Color =
    when (LocalTheme.current) {
        AppThemeColors.UNRECOGNIZED,
        AppThemeColors.PURPLE,
        AppThemeColors.BLUE,
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
        AppThemeColors.ELECTRIC_INDIGO,
        AppThemeColors.CUSTOM,
        -> Color.White // MaterialTheme.colorScheme.onSurface
    }

@PreviewTvSpec
@Composable
private fun WatchedIconPreview() {
    WholphinTheme {
        WatchedIcon(Modifier.size(64.dp))
    }
}
