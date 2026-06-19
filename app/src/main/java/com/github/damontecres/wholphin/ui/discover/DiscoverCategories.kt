package com.github.damontecres.wholphin.ui.discover

import android.content.Context
import androidx.annotation.StringRes
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.preferences.SeerrPreferences
import com.github.damontecres.wholphin.services.DiscoverGenre
import com.github.damontecres.wholphin.util.DiscoverRequestType

data class DiscoverCategory(
    val key: String,
    val type: SeerrItemType,
    val genre: DiscoverGenre? = null,
    @param:StringRes val titleRes: Int? = null,
    val requestType: DiscoverRequestType = DiscoverRequestType.UNKNOWN,
) {
    fun title(context: Context): String =
        titleRes?.let(context::getString)
            ?: genre?.let {
                val mediaType =
                    when (type) {
                        SeerrItemType.MOVIE -> context.getString(R.string.movies)
                        SeerrItemType.TV -> context.getString(R.string.tv_shows)
                        else -> ""
                    }
                "$mediaType · ${it.name}"
            }.orEmpty()
}

val CoreDiscoverCategories =
    listOf(
        DiscoverCategory(
            key = "trending",
            type = SeerrItemType.UNKNOWN,
            titleRes = R.string.trending,
            requestType = DiscoverRequestType.TRENDING,
        ),
        DiscoverCategory(
            key = "movies",
            type = SeerrItemType.MOVIE,
            titleRes = R.string.movies,
            requestType = DiscoverRequestType.DISCOVER_MOVIES,
        ),
        DiscoverCategory(
            key = "tv",
            type = SeerrItemType.TV,
            titleRes = R.string.tv_shows,
            requestType = DiscoverRequestType.DISCOVER_TV,
        ),
        DiscoverCategory(
            key = "upcoming_movies",
            type = SeerrItemType.MOVIE,
            titleRes = R.string.upcoming_movies,
            requestType = DiscoverRequestType.UPCOMING_MOVIES,
        ),
        DiscoverCategory(
            key = "upcoming_tv",
            type = SeerrItemType.TV,
            titleRes = R.string.upcoming_tv,
            requestType = DiscoverRequestType.UPCOMING_TV,
        ),
    )

fun discoverCategories(genres: List<DiscoverGenre>): List<DiscoverCategory> =
    CoreDiscoverCategories +
        genres.map { genre ->
            DiscoverCategory(
                key = genre.key,
                type = genre.type,
                genre = genre,
            )
        }

fun defaultDiscoverCategoryKeys(categories: List<DiscoverCategory>): List<String> =
    CoreDiscoverCategories.map { it.key } +
        categories.filter { it.genre != null }.take(DEFAULT_DISCOVER_GENRE_COUNT).map { it.key }

fun orderedDiscoverCategories(
    categories: List<DiscoverCategory>,
    preferences: SeerrPreferences,
): List<DiscoverCategory> {
    val savedOrder = preferences.discoverCategoryOrderList
    if (savedOrder.isEmpty()) return categories
    val savedIndex = savedOrder.withIndex().associate { it.value to it.index }
    return categories.sortedBy { savedIndex[it.key] ?: Int.MAX_VALUE }
}

fun enabledDiscoverCategories(
    categories: List<DiscoverCategory>,
    preferences: SeerrPreferences,
): List<DiscoverCategory> {
    val ordered = orderedDiscoverCategories(categories, preferences)
    if (!preferences.discoverCategoriesCustomized) {
        val defaultKeys = defaultDiscoverCategoryKeys(ordered).toSet()
        return ordered.filter { it.key in defaultKeys }
    }
    val savedKeys = preferences.discoverCategoryOrderList.toSet()
    val disabledKeys = preferences.disabledDiscoverCategoryKeysList.toSet()
    return ordered.filter { it.key in savedKeys && it.key !in disabledKeys }
}

private const val DEFAULT_DISCOVER_GENRE_COUNT = 8
