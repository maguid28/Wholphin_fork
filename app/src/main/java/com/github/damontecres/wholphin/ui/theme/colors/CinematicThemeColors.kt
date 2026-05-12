package com.github.damontecres.wholphin.ui.theme.colors

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme as MaterialColorScheme
import androidx.compose.material3.darkColorScheme as materialDarkColorScheme
import androidx.compose.material3.lightColorScheme as materialLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme
import androidx.tv.material3.lightColorScheme as tvLightColorScheme
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.theme.ThemeColors
import kotlin.math.abs

val RefinedBlueThemeColors = cinematicThemeColors(Color(0xFF4AA3FF))
val RefinedGreenThemeColors =
    cinematicThemeColors(
        seed = Color(0xFF39F6A5),
        secondarySeed = Color(0xFFA7FF4F),
        tertiarySeed = Color(0xFF5DEBFF),
        neutralSurfaces = true,
    )
val RefinedOrangeThemeColors = cinematicThemeColors(Color(0xFFFFA63D))
val RefinedBoldBlueThemeColors = cinematicThemeColors(Color(0xFF276BFF), bold = true)
val NeonPopThemeColors =
    cinematicThemeColors(
        seed = Color(0xFFFF2DAA),
        secondarySeed = Color(0xFFB7FF00),
        tertiarySeed = Color(0xFF00E5FF),
        bold = true,
    )
val AuroraThemeColors =
    cinematicThemeColors(
        seed = Color(0xFF25F0A2),
        secondarySeed = Color(0xFF8D6BFF),
        tertiarySeed = Color(0xFFFFD166),
        neutralSurfaces = true,
    )
val CyberpunkThemeColors =
    cinematicThemeColors(
        seed = Color(0xFFFF3DF2),
        secondarySeed = Color(0xFF00F5FF),
        tertiarySeed = Color(0xFFFFF45C),
        bold = true,
    )
val SunsetThemeColors =
    cinematicThemeColors(
        seed = Color(0xFFFF7A45),
        secondarySeed = Color(0xFFFF4F8B),
        tertiarySeed = Color(0xFFFFD36E),
    )
val DeepOceanThemeColors =
    cinematicThemeColors(
        seed = Color(0xFF00B8FF),
        secondarySeed = Color(0xFF3FFFD2),
        tertiarySeed = Color(0xFF6B7CFF),
    )
val SakuraThemeColors =
    cinematicThemeColors(
        seed = Color(0xFFFF7AB6),
        secondarySeed = Color(0xFF9DDCFF),
        tertiarySeed = Color(0xFFFFD6E8),
    )
val EmberThemeColors =
    cinematicThemeColors(
        seed = Color(0xFFFF4D3D),
        secondarySeed = Color(0xFFFFB000),
        tertiarySeed = Color(0xFFB765FF),
        bold = true,
    )
val MintThemeColors =
    cinematicThemeColors(
        seed = Color(0xFF8CF5C7),
        secondarySeed = Color(0xFF41D8FF),
        tertiarySeed = Color(0xFFFFA3D7),
        neutralSurfaces = true,
    )
val ElectricVioletThemeColors =
    cinematicThemeColors(
        seed = Color(0xFFB86BFF),
        secondarySeed = Color(0xFFFF6BD6),
        tertiarySeed = Color(0xFF6BFFB8),
        bold = true,
    )
val TealGlowThemeColors =
    cinematicThemeColors(
        seed = Color(0xFF00D5C7),
        secondarySeed = Color(0xFFFFC857),
        tertiarySeed = Color(0xFF5E8CFF),
        neutralSurfaces = true,
    )
val RoseThemeColors =
    cinematicThemeColors(
        seed = Color(0xFFFF5C8A),
        secondarySeed = Color(0xFFFFB86B),
        tertiarySeed = Color(0xFF9D7CFF),
    )
val GoldThemeColors =
    cinematicThemeColors(
        seed = Color(0xFFFFD166),
        secondarySeed = Color(0xFF35E6A8),
        tertiarySeed = Color(0xFF7D9CFF),
    )
val EmeraldThemeColors =
    cinematicThemeColors(
        seed = Color(0xFF00E676),
        secondarySeed = Color(0xFFA7FF83),
        tertiarySeed = Color(0xFF5AD7FF),
        neutralSurfaces = true,
    )
val LimeGlowThemeColors =
    cinematicThemeColors(
        seed = Color(0xFFB6FF00),
        secondarySeed = Color(0xFF45FF70),
        tertiarySeed = Color(0xFF00E5FF),
        bold = true,
        neutralSurfaces = true,
    )
val ForestThemeColors =
    cinematicThemeColors(
        seed = Color(0xFF4ADE80),
        secondarySeed = Color(0xFFA3E635),
        tertiarySeed = Color(0xFF38BDF8),
        neutralSurfaces = true,
    )
val JadeThemeColors =
    cinematicThemeColors(
        seed = Color(0xFF00D084),
        secondarySeed = Color(0xFF7CFFCB),
        tertiarySeed = Color(0xFF7AA7FF),
        neutralSurfaces = true,
    )
val MossThemeColors =
    cinematicThemeColors(
        seed = Color(0xFF9BCB56),
        secondarySeed = Color(0xFFD4FF74),
        tertiarySeed = Color(0xFF6EE7B7),
        neutralSurfaces = true,
    )
val MatrixThemeColors =
    cinematicThemeColors(
        seed = Color(0xFF39FF14),
        secondarySeed = Color(0xFF00FF88),
        tertiarySeed = Color(0xFFC8FF00),
        bold = true,
        neutralSurfaces = true,
    )
val AuroraGreenThemeColors =
    cinematicThemeColors(
        seed = Color(0xFF32F6A3),
        secondarySeed = Color(0xFFB8FF4D),
        tertiarySeed = Color(0xFF58E7FF),
        neutralSurfaces = true,
    )
val SeafoamThemeColors =
    cinematicThemeColors(
        seed = Color(0xFF76FFD6),
        secondarySeed = Color(0xFF8BFF72),
        tertiarySeed = Color(0xFF8AD8FF),
        neutralSurfaces = true,
    )
val ChartreuseThemeColors =
    cinematicThemeColors(
        seed = Color(0xFFD7FF2F),
        secondarySeed = Color(0xFF66FF55),
        tertiarySeed = Color(0xFF00F0FF),
        bold = true,
        neutralSurfaces = true,
    )
val PineGlowThemeColors =
    cinematicThemeColors(
        seed = Color(0xFF35D982),
        secondarySeed = Color(0xFF2FFFC3),
        tertiarySeed = Color(0xFFA3FF57),
        neutralSurfaces = true,
    )
val NeonMintThemeColors =
    cinematicThemeColors(
        seed = Color(0xFF64FFDA),
        secondarySeed = Color(0xFF39FF14),
        tertiarySeed = Color(0xFFFF5CDB),
        bold = true,
        neutralSurfaces = true,
    )
val MalachiteThemeColors =
    cinematicThemeColors(
        seed = Color(0xFF00F060),
        secondarySeed = Color(0xFF00FFC2),
        tertiarySeed = Color(0xFFB8FF2D),
        bold = true,
        neutralSurfaces = true,
    )
val TropicalPunchThemeColors =
    cinematicThemeColors(
        seed = Color(0xFFFF2DAA),
        secondarySeed = Color(0xFF76FF03),
        tertiarySeed = Color(0xFF00F5FF),
        bold = true,
        neutralSurfaces = true,
    )
val ArcticBloomThemeColors =
    cinematicThemeColors(
        seed = Color(0xFFA7F3FF),
        secondarySeed = Color(0xFFD8B4FE),
        tertiarySeed = Color(0xFFFF7AB6),
        neutralSurfaces = true,
    )
val LaserLimeThemeColors =
    cinematicThemeColors(
        seed = Color(0xFFCCFF00),
        secondarySeed = Color(0xFF00FF7F),
        tertiarySeed = Color(0xFF00E5FF),
        bold = true,
        neutralSurfaces = true,
    )
val MiamiNeonThemeColors =
    cinematicThemeColors(
        seed = Color(0xFF00F5FF),
        secondarySeed = Color(0xFFFF3DBE),
        tertiarySeed = Color(0xFFFFB86B),
        bold = true,
    )
val CosmicCandyThemeColors =
    cinematicThemeColors(
        seed = Color(0xFFB026FF),
        secondarySeed = Color(0xFFFF3DBE),
        tertiarySeed = Color(0xFF64FFDA),
        bold = true,
    )
val GlacierCyanThemeColors =
    cinematicThemeColors(
        seed = Color(0xFF5DEBFF),
        secondarySeed = Color(0xFFB8FFF2),
        tertiarySeed = Color(0xFF8EA7FF),
        neutralSurfaces = true,
    )
val DragonfruitThemeColors =
    cinematicThemeColors(
        seed = Color(0xFFFF2F6D),
        secondarySeed = Color(0xFFFF8A1F),
        tertiarySeed = Color(0xFF6C63FF),
        bold = true,
    )
val VolcanicTealThemeColors =
    cinematicThemeColors(
        seed = Color(0xFF00C2A8),
        secondarySeed = Color(0xFFFF6B35),
        tertiarySeed = Color(0xFFFFD166),
        neutralSurfaces = true,
    )
val MoonlitPlumThemeColors =
    cinematicThemeColors(
        seed = Color(0xFF8B5CF6),
        secondarySeed = Color(0xFFDA70D6),
        tertiarySeed = Color(0xFF2FFFC3),
        neutralSurfaces = true,
    )
val SolarFlareThemeColors =
    cinematicThemeColors(
        seed = Color(0xFFFFE45E),
        secondarySeed = Color(0xFFFF8A1F),
        tertiarySeed = Color(0xFF00D6C9),
        bold = true,
    )
val RainbowGlassThemeColors =
    cinematicThemeColors(
        seed = Color(0xFF35E8FF),
        secondarySeed = Color(0xFFFF5CDB),
        tertiarySeed = Color(0xFFD7FF2F),
        bold = true,
        neutralSurfaces = true,
    )
val PeacockThemeColors =
    cinematicThemeColors(
        seed = Color(0xFF00A3A3),
        secondarySeed = Color(0xFF3D5AFE),
        tertiarySeed = Color(0xFFFFD166),
        neutralSurfaces = true,
    )
val ElectricNeonThemeColors =
    cinematicThemeColors(
        seed = Color(0xFF76FF03),
        secondarySeed = Color(0xFF76FFD6),
        tertiarySeed = Color(0xFF76FF03),
        bold = true,
        neutralSurfaces = true,
    )

data class CustomThemeTone(
    @param:StringRes val label: Int,
    val color: Color,
)

data class CustomThemeColorChoices(
    val primary: Int,
    val secondary: Int,
    val tertiary: Int,
)

val DefaultCustomThemeColorChoices =
    CustomThemeColorChoices(
        primary = 0,
        secondary = 1,
        tertiary = 6,
    )

val CustomThemeToneOptions =
    listOf(
        CustomThemeTone(R.string.tone_aurora_green, Color(0xFF32F6A3)),
        CustomThemeTone(R.string.tone_turquoise, Color(0xFF00D6C9)),
        CustomThemeTone(R.string.tone_blue_turquoise, Color(0xFF35E8FF)),
        CustomThemeTone(R.string.tone_seafoam, Color(0xFF76FFD6)),
        CustomThemeTone(R.string.tone_lagoon, Color(0xFF00B8A9)),
        CustomThemeTone(R.string.tone_mint_glow, Color(0xFFA7FFCB)),
        CustomThemeTone(R.string.tone_lime_spark, Color(0xFFB8FF4D)),
        CustomThemeTone(R.string.tone_chartreuse, Color(0xFFD7FF2F)),
        CustomThemeTone(R.string.tone_emerald, Color(0xFF00E676)),
        CustomThemeTone(R.string.tone_jade, Color(0xFF00D084)),
        CustomThemeTone(R.string.tone_malachite, Color(0xFF00F060)),
        CustomThemeTone(R.string.tone_aqua, Color(0xFF00E5FF)),
        CustomThemeTone(R.string.tone_sky_glass, Color(0xFF8AD8FF)),
        CustomThemeTone(R.string.tone_violet, Color(0xFF9D7CFF)),
        CustomThemeTone(R.string.tone_hot_pink, Color(0xFFFF2DAA)),
        CustomThemeTone(R.string.tone_coral, Color(0xFFFF7A45)),
        CustomThemeTone(R.string.tone_gold, Color(0xFFFFD166)),
        CustomThemeTone(R.string.tone_rose, Color(0xFFFF5C8A)),
        CustomThemeTone(R.string.tone_ice_mint, Color(0xFFC7FFE8)),
        CustomThemeTone(R.string.tone_deep_teal, Color(0xFF12B89A)),
        CustomThemeTone(R.string.tone_neon_cyan, Color(0xFF00F5FF)),
        CustomThemeTone(R.string.tone_plasma_blue, Color(0xFF3D5AFE)),
        CustomThemeTone(R.string.tone_electric_indigo, Color(0xFF6C63FF)),
        CustomThemeTone(R.string.tone_ultraviolet, Color(0xFFB026FF)),
        CustomThemeTone(R.string.tone_magenta_bloom, Color(0xFFFF3DBE)),
        CustomThemeTone(R.string.tone_dragonfruit, Color(0xFFFF2F6D)),
        CustomThemeTone(R.string.tone_raspberry, Color(0xFFE11D74)),
        CustomThemeTone(R.string.tone_watermelon, Color(0xFFFF4F6D)),
        CustomThemeTone(R.string.tone_tangerine, Color(0xFFFF8A1F)),
        CustomThemeTone(R.string.tone_apricot, Color(0xFFFFB86B)),
        CustomThemeTone(R.string.tone_solar_yellow, Color(0xFFFFE45E)),
        CustomThemeTone(R.string.tone_acid_lime, Color(0xFFCCFF00)),
        CustomThemeTone(R.string.tone_neon_lime, Color(0xFF76FF03)),
        CustomThemeTone(R.string.tone_spring_green, Color(0xFF00FF7F)),
        CustomThemeTone(R.string.tone_basil, Color(0xFF47D16C)),
        CustomThemeTone(R.string.tone_peacock, Color(0xFF00A3A3)),
        CustomThemeTone(R.string.tone_caribbean, Color(0xFF00C2FF)),
        CustomThemeTone(R.string.tone_glacier, Color(0xFFA7F3FF)),
        CustomThemeTone(R.string.tone_periwinkle, Color(0xFF8EA7FF)),
        CustomThemeTone(R.string.tone_orchid, Color(0xFFDA70D6)),
        CustomThemeTone(R.string.tone_lavender_ice, Color(0xFFD8B4FE)),
        CustomThemeTone(R.string.tone_champagne, Color(0xFFFFE8A3)),
        CustomThemeTone(R.string.tone_copper, Color(0xFFD97745)),
        CustomThemeTone(R.string.tone_moonstone, Color(0xFFB8FFF2)),
        CustomThemeTone(R.string.tone_ruby, Color(0xFFE11D48)),
        CustomThemeTone(R.string.tone_sapphire, Color(0xFF2563EB)),
        CustomThemeTone(R.string.tone_amethyst, Color(0xFF9333EA)),
        CustomThemeTone(R.string.tone_fresh_kelp, Color(0xFF2DD4BF)),
    )

fun buildCustomThemeColors(choices: CustomThemeColorChoices): ThemeColors =
    cinematicThemeColors(
        seed = CustomThemeToneOptions.toneAt(choices.primary).color,
        secondarySeed = CustomThemeToneOptions.toneAt(choices.secondary).color,
        tertiarySeed = CustomThemeToneOptions.toneAt(choices.tertiary).color,
        bold = true,
        neutralSurfaces = true,
    )

private fun List<CustomThemeTone>.toneAt(index: Int): CustomThemeTone = getOrElse(index) { first() }

fun cinematicThemeColors(
    seed: Color,
    secondarySeed: Color? = null,
    tertiarySeed: Color? = null,
    bold: Boolean = false,
    neutralSurfaces: Boolean = false,
): ThemeColors {
    val hsl = seed.toHsl()
    val hue = hsl.hue
    val accentSaturation = (hsl.saturation + if (bold) .18f else .08f).coerceIn(.68f, .96f)
    val secondaryHsl = secondarySeed?.toHsl()
    val tertiaryHsl = tertiarySeed?.toHsl()
    val secondarySaturation =
        secondaryHsl
            ?.let { (it.saturation + if (bold) .12f else .04f).coerceIn(.42f, .96f) }
            ?: .34f
    val tertiarySaturation =
        tertiaryHsl
            ?.let { (it.saturation + if (bold) .12f else .04f).coerceIn(.46f, .96f) }
            ?: .66f
    val tertiaryShift = if (hue in 25f..70f) -34f else 38f

    fun accent(
        lightness: Float,
        saturation: Float = accentSaturation,
        hueShift: Float = 0f,
    ) = hslColor(
        hue = hue + hueShift,
        saturation = saturation.coerceIn(.08f, .98f),
        lightness = lightness.coerceIn(.02f, .98f),
    )

    fun secondaryAccent(lightness: Float) =
        secondaryHsl?.let {
            hslColor(
                hue = it.hue,
                saturation = secondarySaturation,
                lightness = lightness.coerceIn(.02f, .98f),
            )
        } ?: accent(lightness, secondarySaturation)

    fun tertiaryAccent(lightness: Float) =
        tertiaryHsl?.let {
            hslColor(
                hue = it.hue,
                saturation = tertiarySaturation,
                lightness = lightness.coerceIn(.02f, .98f),
            )
        } ?: accent(lightness, tertiarySaturation, tertiaryShift)

    val darkBackgroundBase = Color(0xFF07080D)
    val darkSurfaceBase = Color(0xFF0D1017)
    val darkSurfaceVariantBase = Color(0xFF252A34)
    val lightBackgroundBase = Color(0xFFFBFAFF)
    val lightSurfaceBase = Color(0xFFFFFBFF)
    val lightSurfaceVariantBase = Color(0xFFE1E4EC)

    val darkBackground =
        if (neutralSurfaces) darkBackgroundBase else darkBackgroundBase.blend(accent(.42f), .035f)
    val darkSurface =
        if (neutralSurfaces) {
            darkSurfaceBase
        } else {
            darkSurfaceBase
                .blend(accent(.40f), .045f)
                .blend(secondaryAccent(.40f), .025f)
        }
    val darkSurfaceVariant =
        if (neutralSurfaces) {
            darkSurfaceVariantBase
        } else {
            darkSurfaceVariantBase
                .blend(accent(.44f), .08f)
                .blend(secondaryAccent(.42f), .06f)
        }
    val darkPrimary = accent(if (bold) .70f else .68f)
    val darkPrimaryContainer = accent(if (bold) .35f else .31f, accentSaturation * .82f)
    val darkSecondary = secondaryAccent(.72f)
    val darkSecondaryContainer = secondaryAccent(.27f)
    val darkTertiary = tertiaryAccent(.70f)
    val darkTertiaryContainer = tertiaryAccent(.30f)

    val lightBackground =
        if (neutralSurfaces) {
            lightBackgroundBase
        } else {
            lightBackgroundBase
                .blend(accent(.78f), .035f)
                .blend(secondaryAccent(.78f), .018f)
        }
    val lightSurface =
        if (neutralSurfaces) {
            lightSurfaceBase
        } else {
            lightSurfaceBase
                .blend(accent(.78f), .028f)
                .blend(secondaryAccent(.78f), .014f)
        }
    val lightSurfaceVariant =
        if (neutralSurfaces) {
            lightSurfaceVariantBase
        } else {
            lightSurfaceVariantBase
                .blend(accent(.72f), .08f)
                .blend(secondaryAccent(.76f), .05f)
        }
    val lightPrimary = accent(.42f, accentSaturation * .92f)
    val lightPrimaryContainer = accent(.84f, accentSaturation * .55f)
    val lightSecondary = secondaryAccent(.40f)
    val lightSecondaryContainer = secondaryAccent(.86f)
    val lightTertiary = tertiaryAccent(.42f)
    val lightTertiaryContainer = tertiaryAccent(.86f)

    return object : ThemeColors {
        override val lightSchemeMaterial: MaterialColorScheme =
            materialLightColorScheme(
                primary = lightPrimary,
                onPrimary = contentColorFor(lightPrimary),
                primaryContainer = lightPrimaryContainer,
                onPrimaryContainer = contentColorFor(lightPrimaryContainer),
                secondary = lightSecondary,
                onSecondary = contentColorFor(lightSecondary),
                secondaryContainer = lightSecondaryContainer,
                onSecondaryContainer = contentColorFor(lightSecondaryContainer),
                tertiary = lightTertiary,
                onTertiary = contentColorFor(lightTertiary),
                tertiaryContainer = lightTertiaryContainer,
                onTertiaryContainer = contentColorFor(lightTertiaryContainer),
                error = Color(0xFFBA1A1A),
                onError = Color.White,
                errorContainer = Color(0xFFFFDAD6),
                onErrorContainer = Color(0xFF93000A),
                background = lightBackground,
                onBackground = Color(0xFF181A20),
                surface = lightSurface,
                onSurface = Color(0xFF181A20),
                surfaceVariant = lightSurfaceVariant,
                onSurfaceVariant = Color(0xFF424650),
                surfaceTint = lightSurface,
                scrim = Color.Black,
                inverseSurface = Color(0xFF2D3038),
                inverseOnSurface = Color(0xFFF0F1F8),
                inversePrimary = darkPrimary,
            )

        override val darkSchemeMaterial: MaterialColorScheme =
            materialDarkColorScheme(
                primary = darkPrimary,
                onPrimary = contentColorFor(darkPrimary),
                primaryContainer = darkPrimaryContainer,
                onPrimaryContainer = contentColorFor(darkPrimaryContainer),
                secondary = darkSecondary,
                onSecondary = contentColorFor(darkSecondary),
                secondaryContainer = darkSecondaryContainer,
                onSecondaryContainer = contentColorFor(darkSecondaryContainer),
                tertiary = darkTertiary,
                onTertiary = contentColorFor(darkTertiary),
                tertiaryContainer = darkTertiaryContainer,
                onTertiaryContainer = contentColorFor(darkTertiaryContainer),
                error = Color(0xFFFFB4AB),
                onError = Color(0xFF690005),
                errorContainer = Color(0xFF93000A),
                onErrorContainer = Color(0xFFFFDAD6),
                background = darkBackground,
                onBackground = Color(0xFFE8EAF2),
                surface = darkSurface,
                onSurface = Color(0xFFE8EAF2),
                surfaceVariant = darkSurfaceVariant,
                onSurfaceVariant = Color(0xFFC7CBD7),
                surfaceTint = darkSurface,
                scrim = Color.Black,
                inverseSurface = Color(0xFFE8EAF2),
                inverseOnSurface = Color(0xFF2C3038),
                inversePrimary = lightPrimary,
            )

        override val lightScheme =
            tvLightColorScheme(
                primary = lightPrimary,
                onPrimary = contentColorFor(lightPrimary),
                primaryContainer = lightPrimaryContainer,
                onPrimaryContainer = contentColorFor(lightPrimaryContainer),
                secondary = lightSecondary,
                onSecondary = contentColorFor(lightSecondary),
                secondaryContainer = lightSecondaryContainer,
                onSecondaryContainer = contentColorFor(lightSecondaryContainer),
                tertiary = lightTertiary,
                onTertiary = contentColorFor(lightTertiary),
                tertiaryContainer = lightTertiaryContainer,
                onTertiaryContainer = contentColorFor(lightTertiaryContainer),
                error = Color(0xFFBA1A1A),
                onError = Color.White,
                errorContainer = Color(0xFFFFDAD6),
                onErrorContainer = Color(0xFF93000A),
                background = lightBackground,
                onBackground = Color(0xFF181A20),
                surface = lightSurface,
                onSurface = Color(0xFF181A20),
                surfaceVariant = lightSurfaceVariant,
                onSurfaceVariant = Color(0xFF424650),
                surfaceTint = lightSurface,
                scrim = Color.Black,
                inverseSurface = Color(0xFF2D3038),
                inverseOnSurface = Color(0xFFF0F1F8),
                inversePrimary = darkPrimary,
                border = darkPrimary,
            )

        override val darkScheme =
            tvDarkColorScheme(
                primary = darkPrimary,
                onPrimary = contentColorFor(darkPrimary),
                primaryContainer = darkPrimaryContainer,
                onPrimaryContainer = contentColorFor(darkPrimaryContainer),
                secondary = darkSecondary,
                onSecondary = contentColorFor(darkSecondary),
                secondaryContainer = darkSecondaryContainer,
                onSecondaryContainer = contentColorFor(darkSecondaryContainer),
                tertiary = darkTertiary,
                onTertiary = contentColorFor(darkTertiary),
                tertiaryContainer = darkTertiaryContainer,
                onTertiaryContainer = contentColorFor(darkTertiaryContainer),
                error = Color(0xFFFFB4AB),
                onError = Color(0xFF690005),
                errorContainer = Color(0xFF93000A),
                onErrorContainer = Color(0xFFFFDAD6),
                background = darkBackground,
                onBackground = Color(0xFFE8EAF2),
                surface = darkSurface,
                onSurface = Color(0xFFE8EAF2),
                surfaceVariant = darkSurfaceVariant,
                onSurfaceVariant = Color(0xFFC7CBD7),
                surfaceTint = darkSurface,
                scrim = Color.Black,
                inverseSurface = Color(0xFFE8EAF2),
                inverseOnSurface = Color(0xFF2C3038),
                inversePrimary = lightPrimary,
                border = darkPrimary.copy(alpha = .92f),
            )
    }
}

private data class Hsl(
    val hue: Float,
    val saturation: Float,
    val lightness: Float,
)

private fun Color.toHsl(): Hsl {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val delta = max - min
    val lightness = (max + min) / 2f
    val saturation =
        if (delta == 0f) {
            0f
        } else {
            delta / (1f - abs(2f * lightness - 1f))
        }
    val hue =
        when {
            delta == 0f -> 0f
            max == red -> 60f * (((green - blue) / delta).mod(6f))
            max == green -> 60f * (((blue - red) / delta) + 2f)
            else -> 60f * (((red - green) / delta) + 4f)
        }
    return Hsl(hue = hue.mod(360f), saturation = saturation, lightness = lightness)
}

private fun hslColor(
    hue: Float,
    saturation: Float,
    lightness: Float,
): Color {
    val normalizedHue = hue.mod(360f) / 60f
    val chroma = (1f - abs(2f * lightness - 1f)) * saturation
    val x = chroma * (1f - abs(normalizedHue.mod(2f) - 1f))
    val match = lightness - chroma / 2f
    val (red, green, blue) =
        when {
            normalizedHue < 1f -> Triple(chroma, x, 0f)
            normalizedHue < 2f -> Triple(x, chroma, 0f)
            normalizedHue < 3f -> Triple(0f, chroma, x)
            normalizedHue < 4f -> Triple(0f, x, chroma)
            normalizedHue < 5f -> Triple(x, 0f, chroma)
            else -> Triple(chroma, 0f, x)
        }
    return Color(
        red = (red + match).coerceIn(0f, 1f),
        green = (green + match).coerceIn(0f, 1f),
        blue = (blue + match).coerceIn(0f, 1f),
        alpha = 1f,
    )
}

private fun Color.blend(
    other: Color,
    amount: Float,
): Color {
    val ratio = amount.coerceIn(0f, 1f)
    return Color(
        red = red + (other.red - red) * ratio,
        green = green + (other.green - green) * ratio,
        blue = blue + (other.blue - blue) * ratio,
        alpha = alpha + (other.alpha - alpha) * ratio,
    )
}

private fun contentColorFor(color: Color): Color =
    if (color.luminance() > .42f) Color(0xFF101219) else Color.White
