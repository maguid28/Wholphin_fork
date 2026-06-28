package com.github.damontecres.wholphin.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.tv.material3.LocalTextStyle
import com.github.damontecres.wholphin.preferences.AppFont

val LocalAppFontFamily = compositionLocalOf<FontFamily> { FontFamily.Default }

@Composable
fun rememberAppFontFamily(appFont: AppFont): FontFamily =
    remember(appFont) { appFontFamily(appFont) }

fun TextStyle.withAppFontFamily(fontFamily: FontFamily): TextStyle =
    copy(fontFamily = fontFamily)

@Composable
fun TextStyle.withAppFontFamily(): TextStyle = withAppFontFamily(LocalAppFontFamily.current)

@Composable
fun AppFontScope(
    fontFamily: FontFamily,
    content: @Composable () -> Unit,
) {
    val parentStyle = LocalTextStyle.current
    CompositionLocalProvider(
        LocalTextStyle provides parentStyle.merge(TextStyle(fontFamily = fontFamily)),
        content = content,
    )
}
