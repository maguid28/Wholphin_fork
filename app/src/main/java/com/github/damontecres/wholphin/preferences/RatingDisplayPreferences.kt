package com.github.damontecres.wholphin.preferences

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.dot
import java.util.Locale
import kotlin.math.roundToInt

val ALL_DISPLAYED_RATING_TYPES =
    listOf(
        DisplayedRatingType.COMMUNITY_RATING,
        DisplayedRatingType.RT_AUDIENCE,
        DisplayedRatingType.RT_CRITIC,
    )

val DEFAULT_DISPLAYED_RATING_TYPES =
    listOf(
        DisplayedRatingType.COMMUNITY_RATING,
        DisplayedRatingType.RT_AUDIENCE,
    )

fun InterfacePreferences.enabledDisplayedRatings(): Set<DisplayedRatingType> {
    val stored = displayedRatingsList.filter { it != DisplayedRatingType.UNRECOGNIZED }
    return if (stored.isEmpty()) DEFAULT_DISPLAYED_RATING_TYPES.toSet() else stored.toSet()
}

fun InterfacePreferences.showsRating(type: DisplayedRatingType): Boolean = type in enabledDisplayedRatings()

fun InterfacePreferences.shouldFetchRtAudience(): Boolean = showsRating(DisplayedRatingType.RT_AUDIENCE)

fun InterfacePreferences.shouldFetchRtCritic(): Boolean = showsRating(DisplayedRatingType.RT_CRITIC)

fun AnnotatedString.Builder.appendCommunityRating(
    rating: Float?,
    interfacePreferences: InterfacePreferences,
) {
    if (!interfacePreferences.showsRating(DisplayedRatingType.COMMUNITY_RATING)) return
    rating?.let {
        dot()
        append(String.format(Locale.getDefault(), "%.1f", it))
        appendInlineContent(id = "star")
    }
}

fun AnnotatedString.Builder.appendRtAudienceScore(
    score: Float?,
    interfacePreferences: InterfacePreferences,
) {
    if (!interfacePreferences.showsRating(DisplayedRatingType.RT_AUDIENCE)) return
    score?.takeIf { it > 0f }?.let {
        dot()
        append("RT Audience ${it.roundToInt()}%")
    }
}

fun AnnotatedString.Builder.appendRtCriticScore(
    score: Float?,
    interfacePreferences: InterfacePreferences,
) {
    if (!interfacePreferences.showsRating(DisplayedRatingType.RT_CRITIC)) return
    score?.takeIf { it > 0f }?.let {
        dot()
        append("${it.roundToInt()}%")
        appendInlineContent(id = if (it >= 60f) "fresh" else "rotten")
    }
}

fun BaseItem.displayQuickDetails(
    interfacePreferences: InterfacePreferences,
    rottenTomatoesAudienceScore: Float? = null,
    rottenTomatoesCriticScore: Float? = null,
): AnnotatedString =
    buildAnnotatedString {
        append(ui.quickDetails)
        appendCommunityRating(data.communityRating, interfacePreferences)
        appendRtAudienceScore(rottenTomatoesAudienceScore, interfacePreferences)
        appendRtCriticScore(rottenTomatoesCriticScore, interfacePreferences)
    }
