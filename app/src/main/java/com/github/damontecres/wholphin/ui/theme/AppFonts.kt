package com.github.damontecres.wholphin.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.AppFont

val AppFontOptions =
    listOf(
        AppFont.SYSTEM_DEFAULT,
        AppFont.INTER,
        AppFont.DM_SANS,
        AppFont.OUTFIT,
        AppFont.PLUS_JAKARTA_SANS,
        AppFont.MANROPE,
        AppFont.POPPINS,
        AppFont.NUNITO_SANS,
        AppFont.PLAYFAIR_DISPLAY,
    )

private fun bundledFontFamily(
    regular: Int,
    semibold: Int,
): FontFamily =
    FontFamily(
        Font(regular, FontWeight.Normal),
        Font(semibold, FontWeight.Medium),
        Font(semibold, FontWeight.SemiBold),
        Font(semibold, FontWeight.Bold),
    )

private val InterFontFamily = bundledFontFamily(R.font.inter_regular, R.font.inter_semibold)
private val DmSansFontFamily = bundledFontFamily(R.font.dm_sans_regular, R.font.dm_sans_semibold)
private val OutfitFontFamily = bundledFontFamily(R.font.outfit_regular, R.font.outfit_semibold)
private val PlusJakartaSansFontFamily =
    bundledFontFamily(R.font.plus_jakarta_sans_regular, R.font.plus_jakarta_sans_semibold)
private val ManropeFontFamily = bundledFontFamily(R.font.manrope_regular, R.font.manrope_semibold)
private val PoppinsFontFamily = bundledFontFamily(R.font.poppins_regular, R.font.poppins_semibold)
private val NunitoSansFontFamily =
    bundledFontFamily(R.font.nunito_sans_regular, R.font.nunito_sans_semibold)
private val PlayfairDisplayFontFamily =
    bundledFontFamily(R.font.playfair_display_regular, R.font.playfair_display_semibold)

fun appFontFamily(appFont: AppFont): FontFamily =
    when (appFont) {
        AppFont.SYSTEM_DEFAULT,
        AppFont.UNRECOGNIZED,
        -> FontFamily.Default

        AppFont.INTER -> InterFontFamily
        AppFont.DM_SANS -> DmSansFontFamily
        AppFont.OUTFIT -> OutfitFontFamily
        AppFont.PLUS_JAKARTA_SANS -> PlusJakartaSansFontFamily
        AppFont.MANROPE -> ManropeFontFamily
        AppFont.POPPINS -> PoppinsFontFamily
        AppFont.NUNITO_SANS -> NunitoSansFontFamily
        AppFont.PLAYFAIR_DISPLAY -> PlayfairDisplayFontFamily
    }

fun appFontDisplayName(appFont: AppFont): String =
    when (appFont) {
        AppFont.SYSTEM_DEFAULT,
        AppFont.UNRECOGNIZED,
        -> "System Default"

        AppFont.INTER -> "Inter"
        AppFont.DM_SANS -> "DM Sans"
        AppFont.OUTFIT -> "Outfit"
        AppFont.PLUS_JAKARTA_SANS -> "Plus Jakarta Sans"
        AppFont.MANROPE -> "Manrope"
        AppFont.POPPINS -> "Poppins"
        AppFont.NUNITO_SANS -> "Nunito Sans"
        AppFont.PLAYFAIR_DISPLAY -> "Playfair Display"
    }
