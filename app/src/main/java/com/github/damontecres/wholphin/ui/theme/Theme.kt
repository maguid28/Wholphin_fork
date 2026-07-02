package com.github.damontecres.wholphin.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.wholphin.preferences.AppFont
import com.github.damontecres.wholphin.preferences.AppThemeColors
import com.github.damontecres.wholphin.preferences.InterfacePreferences
import com.github.damontecres.wholphin.ui.theme.colors.AuroraThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.AuroraGreenThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.ArcticBloomThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.ChartreuseThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.CosmicCandyThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.CustomThemeColorChoices
import com.github.damontecres.wholphin.ui.theme.colors.CyberpunkThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.DefaultCustomThemeColorChoices
import com.github.damontecres.wholphin.ui.theme.colors.DeepOceanThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.DragonfruitThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.ElectricIndigoThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.ElectricNeonThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.ElectricVioletThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.EmberThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.EmeraldThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.ForestThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.GlacierCyanThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.GoldThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.JadeThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.LaserLimeThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.LimeGlowThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.MalachiteThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.MatrixThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.MiamiNeonThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.MintThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.MoonlitPlumThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.MossThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.NeonPopThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.NeonMintThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.OledThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.PeacockThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.PineGlowThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.PurpleThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.RainbowGlassThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.RefinedBlueThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.RefinedBoldBlueThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.RefinedGreenThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.RefinedOrangeThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.RoseThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.SakuraThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.SeafoamThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.SolarFlareThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.SunsetThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.TealGlowThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.TropicalPunchThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.VolcanicTealThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.buildCustomThemeColors

val LocalTheme =
    compositionLocalOf<AppThemeColors> { AppThemeColors.ELECTRIC_INDIGO }

val AppThemeColorOptions =
    listOf(
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
    )

fun InterfacePreferences.customThemeColorChoices() =
    if (customThemeSecondary == 0 && customThemeTertiary == 0) {
        DefaultCustomThemeColorChoices
    } else {
        CustomThemeColorChoices(
            primary = customThemePrimary,
            secondary = customThemeSecondary,
            tertiary = customThemeTertiary,
        )
    }

fun getThemeColors(
    appThemeColors: AppThemeColors,
    customThemeColorChoices: CustomThemeColorChoices = DefaultCustomThemeColorChoices,
): ThemeColors =
    when (appThemeColors) {
        AppThemeColors.PURPLE -> PurpleThemeColors
        AppThemeColors.BLUE -> RefinedBlueThemeColors
        AppThemeColors.GREEN -> RefinedGreenThemeColors
        AppThemeColors.ORANGE -> RefinedOrangeThemeColors
        AppThemeColors.OLED_BLACK -> OledThemeColors
        AppThemeColors.BOLD_BLUE -> RefinedBoldBlueThemeColors
        AppThemeColors.NEON_POP -> NeonPopThemeColors
        AppThemeColors.AURORA -> AuroraThemeColors
        AppThemeColors.CYBERPUNK -> CyberpunkThemeColors
        AppThemeColors.SUNSET -> SunsetThemeColors
        AppThemeColors.DEEP_OCEAN -> DeepOceanThemeColors
        AppThemeColors.SAKURA -> SakuraThemeColors
        AppThemeColors.EMBER -> EmberThemeColors
        AppThemeColors.MINT -> MintThemeColors
        AppThemeColors.ELECTRIC_VIOLET -> ElectricVioletThemeColors
        AppThemeColors.TEAL_GLOW -> TealGlowThemeColors
        AppThemeColors.ROSE -> RoseThemeColors
        AppThemeColors.GOLD -> GoldThemeColors
        AppThemeColors.EMERALD -> EmeraldThemeColors
        AppThemeColors.LIME_GLOW -> LimeGlowThemeColors
        AppThemeColors.FOREST -> ForestThemeColors
        AppThemeColors.JADE -> JadeThemeColors
        AppThemeColors.MOSS -> MossThemeColors
        AppThemeColors.MATRIX -> MatrixThemeColors
        AppThemeColors.AURORA_GREEN -> AuroraGreenThemeColors
        AppThemeColors.SEAFOAM -> SeafoamThemeColors
        AppThemeColors.CHARTREUSE -> ChartreuseThemeColors
        AppThemeColors.PINE_GLOW -> PineGlowThemeColors
        AppThemeColors.NEON_MINT -> NeonMintThemeColors
        AppThemeColors.MALACHITE -> MalachiteThemeColors
        AppThemeColors.TROPICAL_PUNCH -> TropicalPunchThemeColors
        AppThemeColors.ARCTIC_BLOOM -> ArcticBloomThemeColors
        AppThemeColors.LASER_LIME -> LaserLimeThemeColors
        AppThemeColors.MIAMI_NEON -> MiamiNeonThemeColors
        AppThemeColors.COSMIC_CANDY -> CosmicCandyThemeColors
        AppThemeColors.GLACIER_CYAN -> GlacierCyanThemeColors
        AppThemeColors.DRAGONFRUIT -> DragonfruitThemeColors
        AppThemeColors.VOLCANIC_TEAL -> VolcanicTealThemeColors
        AppThemeColors.MOONLIT_PLUM -> MoonlitPlumThemeColors
        AppThemeColors.SOLAR_FLARE -> SolarFlareThemeColors
        AppThemeColors.RAINBOW_GLASS -> RainbowGlassThemeColors
        AppThemeColors.PEACOCK -> PeacockThemeColors
        AppThemeColors.ELECTRIC_NEON -> ElectricNeonThemeColors
        AppThemeColors.ELECTRIC_INDIGO -> ElectricIndigoThemeColors
        AppThemeColors.CUSTOM -> buildCustomThemeColors(customThemeColorChoices)
        AppThemeColors.UNRECOGNIZED -> PurpleThemeColors
    }

@Composable
fun NdorfinTheme(
    darkTheme: Boolean = true,
    appThemeColors: AppThemeColors = AppThemeColors.ELECTRIC_INDIGO,
    customThemeColorChoices: CustomThemeColorChoices = DefaultCustomThemeColorChoices,
    appFont: AppFont = AppFont.OUTFIT,
    content: @Composable () -> Unit,
) {
    val themeColors = getThemeColors(appThemeColors, customThemeColorChoices)
    val fontFamily = rememberAppFontFamily(appFont)
    val typography = rememberAppTypography(fontFamily)
    val material3Typography = rememberMaterial3Typography(fontFamily)

    val colorScheme =
        when {
            darkTheme -> themeColors.darkScheme
            else -> themeColors.lightScheme
        }
    CompositionLocalProvider(
        LocalTheme provides appThemeColors,
        LocalAppFontFamily provides fontFamily,
    ) {
        androidx.compose.material3.MaterialTheme(
            colorScheme = if (darkTheme) themeColors.darkSchemeMaterial else themeColors.lightSchemeMaterial,
            typography = material3Typography,
        ) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = typography,
            ) {
                AppFontScope(fontFamily = fontFamily, content = content)
            }
        }
    }
}
