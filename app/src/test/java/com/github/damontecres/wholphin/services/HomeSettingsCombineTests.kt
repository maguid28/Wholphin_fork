package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.model.HomeCategory
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSettingsCombineTests {
    @Test
    fun `Combined row is kept when combine setting is off`() {
        val rows =
            listOf(
                HomeRowConfig.ContinueWatchingCombined(),
                HomeRowConfig.Category(HomeCategory.RECENTLY_ADDED),
            )

        val result = HomeSettingsService.applyCombineContinueNextRows(rows, combine = false)

        assertEquals(rows, result)
        assertTrue(result.first() is HomeRowConfig.ContinueWatchingCombined)
    }

    @Test
    fun `Continue watching and next up merge when combine setting is on`() {
        val rows =
            listOf(
                HomeRowConfig.ContinueWatching(),
                HomeRowConfig.NextUp(),
                HomeRowConfig.Category(HomeCategory.RECENTLY_ADDED),
            )

        val result = HomeSettingsService.applyCombineContinueNextRows(rows, combine = true)

        assertEquals(2, result.size)
        assertTrue(result.first() is HomeRowConfig.ContinueWatchingCombined)
        assertTrue(result[1] is HomeRowConfig.Category)
    }
}
