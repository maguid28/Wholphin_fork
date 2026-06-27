@file:UseSerializers(UUIDSerializer::class)

package com.github.damontecres.wholphin.data.model

import com.github.damontecres.wholphin.preferences.PrefContentScale
import com.github.damontecres.wholphin.ui.AspectRatio
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.components.ViewOptionImageType
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import java.time.LocalDate
import java.time.Month
import java.util.UUID

@Serializable
sealed interface HomeRowConfig {
    val viewOptions: HomeRowViewOptions

    fun updateViewOptions(viewOptions: HomeRowViewOptions): HomeRowConfig

    /**
     * Continue watching media that the user has started but not finished
     */
    @Serializable
    @SerialName("ContinueWatching")
    data class ContinueWatching(
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): ContinueWatching = this.copy(viewOptions = viewOptions)
    }

    /**
     * Next up row for next episodes in a series the user has started
     */
    @Serializable
    @SerialName("NextUp")
    data class NextUp(
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): NextUp = this.copy(viewOptions = viewOptions)
    }

    /**
     * Combined [ContinueWatching] and [NextUp]
     */
    @Serializable
    @SerialName("ContinueWatchingCombined")
    data class ContinueWatchingCombined(
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): ContinueWatchingCombined = this.copy(viewOptions = viewOptions)
    }

    /**
     * Media recently added to a library
     */
    @Serializable
    @SerialName("RecentlyAdded")
    data class RecentlyAdded(
        val parentId: UUID,
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): RecentlyAdded = this.copy(viewOptions = viewOptions)
    }

    /**
     * Media recently released (premiere date) in a library
     */
    @Serializable
    @SerialName("RecentlyReleased")
    data class RecentlyReleased(
        val parentId: UUID,
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): RecentlyReleased = this.copy(viewOptions = viewOptions)
    }

    /**
     * Row of a genres in a library
     */
    @Serializable
    @SerialName("Genres")
    data class Genres(
        val parentId: UUID,
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions.genreDefault,
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): Genres = this.copy(viewOptions = viewOptions)
    }

    /**
     * Row of a studios in a library
     */
    @Serializable
    @SerialName("Studios")
    data class Studios(
        val parentId: UUID,
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions.genreDefault,
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): Studios = this.copy(viewOptions = viewOptions)
    }

    /**
     * Favorites for a specific type
     */
    @Serializable
    @SerialName("Favorite")
    data class Favorite(
        val kind: BaseItemKind,
        override val viewOptions: HomeRowViewOptions =
            if (kind == BaseItemKind.EPISODE) {
                HomeRowViewOptions(
                    heightDp = Cards.HEIGHT_EPISODE,
                    aspectRatio = AspectRatio.WIDE,
                )
            } else {
                HomeRowViewOptions()
            },
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): Favorite = this.copy(viewOptions = viewOptions)
    }

    /**
     * Currently recording
     */
    @Serializable
    @SerialName("Recordings")
    data class Recordings(
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): Recordings = this.copy(viewOptions = viewOptions)
    }

    /**
     * Programs on now/recommended
     */
    @Serializable
    @SerialName("TvPrograms")
    data class TvPrograms(
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions.liveTvDefault,
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): TvPrograms = this.copy(viewOptions = viewOptions)
    }

    /**
     * Live TV channels
     */
    @Serializable
    @SerialName("TvChannels")
    data class TvChannels(
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions.liveTvDefault,
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): TvChannels = this.copy(viewOptions = viewOptions)
    }

    /**
     * Fetch suggestions from [com.github.damontecres.wholphin.services.SuggestionService] for the given parent ID
     */
    @Serializable
    @SerialName("Suggestions")
    data class Suggestions(
        val parentId: UUID,
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): Suggestions = this.copy(viewOptions = viewOptions)
    }

    /**
     * A category that is only shown on the home page during its configured season.
     */
    @Serializable
    @SerialName("Seasonal")
    data class Seasonal(
        val category: SeasonalCategory,
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): Seasonal = this.copy(viewOptions = viewOptions)
    }

    /**
     * A built-in media category spanning all compatible user libraries.
     */
    @Serializable
    @SerialName("Category")
    data class Category(
        val category: HomeCategory,
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): Category = this.copy(viewOptions = viewOptions)
    }

    /**
     * Fetch by parent ID such as a library, collection, or playlist with optional simple sorting
     */
    @Serializable
    @SerialName("ByParent")
    data class ByParent(
        val parentId: UUID,
        val recursive: Boolean = false,
        val sort: SortAndDirection? = null,
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): ByParent = this.copy(viewOptions = viewOptions)
    }

    /**
     * An arbitrary [GetItemsRequest] allowing to query for anything
     */
    @Serializable
    @SerialName("GetItems")
    data class GetItems(
        val name: String,
        val getItems: GetItemsRequest,
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): GetItems = this.copy(viewOptions = viewOptions)
    }
}

@Serializable
enum class HomeCategory(
    val itemKinds: List<BaseItemKind>,
    val sortBy: ItemSortBy,
    val sortOrder: SortOrder = SortOrder.DESCENDING,
    val isPlayed: Boolean? = null,
    val minCommunityRating: Double? = null,
    val rotatesSelection: Boolean = false,
) {
    TOP_RATED_MOVIES(
        itemKinds = listOf(BaseItemKind.MOVIE),
        sortBy = ItemSortBy.COMMUNITY_RATING,
        minCommunityRating = 1.0,
        rotatesSelection = true,
    ),
    TOP_RATED_TV(
        itemKinds = listOf(BaseItemKind.SERIES),
        sortBy = ItemSortBy.COMMUNITY_RATING,
        minCommunityRating = 1.0,
        rotatesSelection = true,
    ),
    POPULAR_MOVIES(
        itemKinds = listOf(BaseItemKind.MOVIE),
        sortBy = ItemSortBy.PLAY_COUNT,
    ),
    POPULAR_TV(
        itemKinds = listOf(BaseItemKind.SERIES),
        sortBy = ItemSortBy.PLAY_COUNT,
    ),
    RECENTLY_ADDED(
        itemKinds = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
        sortBy = ItemSortBy.DATE_CREATED,
    ),
    RECENTLY_RELEASED(
        itemKinds = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
        sortBy = ItemSortBy.PREMIERE_DATE,
    ),
    RECENTLY_RELEASED_MOVIES(
        itemKinds = listOf(BaseItemKind.MOVIE),
        sortBy = ItemSortBy.PREMIERE_DATE,
    ),
    RECENTLY_RELEASED_TV(
        itemKinds = listOf(BaseItemKind.SERIES),
        sortBy = ItemSortBy.PREMIERE_DATE,
    ),
    UNWATCHED_MOVIES(
        itemKinds = listOf(BaseItemKind.MOVIE),
        sortBy = ItemSortBy.RANDOM,
        sortOrder = SortOrder.ASCENDING,
        isPlayed = false,
    ),
    UNWATCHED_TV(
        itemKinds = listOf(BaseItemKind.SERIES),
        sortBy = ItemSortBy.RANDOM,
        sortOrder = SortOrder.ASCENDING,
        isPlayed = false,
    ),
}

@Serializable
enum class SeasonalCategory(
    val activeMonth: Month,
    val genres: List<String>,
    val searchTerms: List<String>,
) {
    HALLOWEEN(
        activeMonth = Month.OCTOBER,
        genres = listOf("Horror"),
        searchTerms = listOf("Halloween"),
    ),
    CHRISTMAS(
        activeMonth = Month.DECEMBER,
        genres = listOf("Holiday"),
        searchTerms = listOf("Christmas", "Holiday", "Santa"),
    ),
    ;

    fun isActive(month: Month = LocalDate.now().month): Boolean = month == activeMonth
}

/**
 * Root class for home page settings
 *
 * Contains the list of rows and a version
 */
@Serializable
@SerialName("HomePageSettings")
data class HomePageSettings(
    val rows: List<HomeRowConfig>,
    val version: Int,
) {
    companion object {
        val EMPTY = HomePageSettings(listOf(), SUPPORTED_HOME_PAGE_SETTINGS_VERSION)
    }
}

/**
 * This is the max version supported by this version of the app
 */
const val SUPPORTED_HOME_PAGE_SETTINGS_VERSION = 1

/**
 * View options for displaying a row
 *
 * Allows for changing things like height or aspect ratio
 */
@Serializable
data class HomeRowViewOptions(
    val heightDp: Int = Cards.HEIGHT_2X3_DP,
    val spacing: Int = 16,
    val contentScale: PrefContentScale = PrefContentScale.FILL,
    val aspectRatio: AspectRatio = AspectRatio.TALL,
    val imageType: ViewOptionImageType = ViewOptionImageType.PRIMARY,
    val showTitles: Boolean = false,
    val useSeries: Boolean = true,
    val episodeContentScale: PrefContentScale = PrefContentScale.FILL,
    val episodeAspectRatio: AspectRatio = AspectRatio.TALL,
    val episodeImageType: ViewOptionImageType = ViewOptionImageType.PRIMARY,
) {
    companion object {
        val genreDefault =
            HomeRowViewOptions(
                heightDp = Cards.HEIGHT_EPISODE,
                aspectRatio = AspectRatio.WIDE,
            )

        val liveTvDefault =
            HomeRowViewOptions(
                heightDp = 96,
                aspectRatio = AspectRatio.WIDE,
                contentScale = PrefContentScale.FIT,
            )
    }
}
