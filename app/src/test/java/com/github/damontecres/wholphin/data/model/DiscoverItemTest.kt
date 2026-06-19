package com.github.damontecres.wholphin.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class DiscoverItemTest {
    @Test
    fun `item is in library when Jellyfin id is present`() {
        assertTrue(discoverItem(jellyfinItemId = UUID.randomUUID()).isInLibrary)
    }

    @Test
    fun `available and partially available items are in library`() {
        assertTrue(discoverItem(availability = SeerrAvailability.AVAILABLE).isInLibrary)
        assertTrue(discoverItem(availability = SeerrAvailability.PARTIALLY_AVAILABLE).isInLibrary)
    }

    @Test
    fun `pending and unknown items remain discoverable`() {
        assertFalse(discoverItem(availability = SeerrAvailability.UNKNOWN).isInLibrary)
        assertFalse(discoverItem(availability = SeerrAvailability.PENDING).isInLibrary)
        assertFalse(discoverItem(availability = SeerrAvailability.PROCESSING).isInLibrary)
    }

    private fun discoverItem(
        availability: SeerrAvailability = SeerrAvailability.UNKNOWN,
        jellyfinItemId: UUID? = null,
    ) = DiscoverItem(
        id = 1,
        type = SeerrItemType.MOVIE,
        title = "Movie",
        subtitle = null,
        overview = null,
        availability = availability,
        releaseDate = null,
        posterUrl = null,
        backDropUrl = null,
        jellyfinItemId = jellyfinItemId,
    )
}
