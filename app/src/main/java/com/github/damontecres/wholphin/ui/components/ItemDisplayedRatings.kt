package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.DisplayedRatingType
import com.github.damontecres.wholphin.preferences.InterfacePreferences
import com.github.damontecres.wholphin.preferences.showsRating
import org.jellyfin.sdk.model.api.BaseItemKind
import kotlin.math.roundToInt

@Composable
fun ItemDisplayedRatings(
    item: BaseItem?,
    interfacePreferences: InterfacePreferences,
    rtAudienceScore: Float? = null,
    rtCriticScore: Float? = null,
    modifier: Modifier = Modifier,
) {
    if (item == null) return
    if (item.type != BaseItemKind.MOVIE && item.type != BaseItemKind.SERIES && item.type != BaseItemKind.EPISODE) {
        return
    }

    val showCommunity =
        interfacePreferences.showsRating(DisplayedRatingType.COMMUNITY_RATING) &&
            item.data.communityRating != null
    val showAudience =
        interfacePreferences.showsRating(DisplayedRatingType.RT_AUDIENCE) &&
            rtAudienceScore != null &&
            rtAudienceScore > 0f
    val showCritic =
        interfacePreferences.showsRating(DisplayedRatingType.RT_CRITIC) &&
            rtCriticScore != null &&
            rtCriticScore > 0f

    if (!showCommunity && !showAudience && !showCritic) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        if (showCommunity) {
            SimpleStarRating(item.data.communityRating)
        }
        if (showAudience) {
            SimpleStarRating(
                text = "RT ${rtAudienceScore!!.roundToInt()}%",
            )
        }
        if (showCritic) {
            TomatoRating(rtCriticScore)
        }
    }
}
