package com.github.damontecres.wholphin.ui.discover

import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.preferences.SeerrPreferences
import com.github.damontecres.wholphin.services.DiscoverGenre
import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoverCategoriesTest {
    private val genres =
        (1..6).flatMap { id ->
            listOf(
                DiscoverGenre(id, "Movie $id", SeerrItemType.MOVIE),
                DiscoverGenre(id, "TV $id", SeerrItemType.TV),
            )
        }
    private val categories = discoverCategories(genres)

    @Test
    fun `defaults include core rows and eight genres`() {
        val enabled = enabledDiscoverCategories(categories, SeerrPreferences.getDefaultInstance())

        assertEquals(CoreDiscoverCategories.size + 8, enabled.size)
        assertEquals(CoreDiscoverCategories.map { it.key }, enabled.take(5).map { it.key })
    }

    @Test
    fun `custom order and disabled categories are honored`() {
        val preferences =
            SeerrPreferences
                .newBuilder()
                .setDiscoverCategoriesCustomized(true)
                .addAllDiscoverCategoryOrder(
                    listOf("tv_genre_2", "trending", "movies", "movie_genre_1"),
                ).addDisabledDiscoverCategoryKeys("movies")
                .build()

        val enabled = enabledDiscoverCategories(categories, preferences)

        assertEquals(
            listOf("tv_genre_2", "trending", "movie_genre_1"),
            enabled.map { it.key },
        )
    }
}
