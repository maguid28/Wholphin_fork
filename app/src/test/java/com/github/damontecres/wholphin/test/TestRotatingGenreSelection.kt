package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.data.model.selectRotatingGenre
import org.junit.Assert.assertEquals
import org.junit.Test

class TestRotatingGenreSelection {
    @Test
    fun `selectRotatingGenre cycles through sorted genres by time slot`() {
        val genres = listOf("Sci-Fi", "Comedy", "Drama")
        val intervalHours = 6
        val intervalMs = intervalHours * 3_600_000L

        assertEquals("Comedy", selectRotatingGenre(genres, intervalHours, 0))
        assertEquals("Comedy", selectRotatingGenre(genres, intervalHours, intervalMs - 1))
        assertEquals("Drama", selectRotatingGenre(genres, intervalHours, intervalMs))
        assertEquals("Sci-Fi", selectRotatingGenre(genres, intervalHours, intervalMs * 2))
        assertEquals("Comedy", selectRotatingGenre(genres, intervalHours, intervalMs * 3))
    }

    @Test
    fun `selectRotatingGenre returns null for empty list`() {
        assertEquals(null, selectRotatingGenre(emptyList()))
    }
}
