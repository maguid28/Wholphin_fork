package com.github.damontecres.wholphin.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Month

class SeasonalCategoryTest {
    @Test
    fun `halloween is only active in october`() {
        assertTrue(SeasonalCategory.HALLOWEEN.isActive(Month.OCTOBER))
        assertFalse(SeasonalCategory.HALLOWEEN.isActive(Month.SEPTEMBER))
        assertFalse(SeasonalCategory.HALLOWEEN.isActive(Month.NOVEMBER))
    }

    @Test
    fun `christmas is only active in december`() {
        assertTrue(SeasonalCategory.CHRISTMAS.isActive(Month.DECEMBER))
        assertFalse(SeasonalCategory.CHRISTMAS.isActive(Month.NOVEMBER))
        assertFalse(SeasonalCategory.CHRISTMAS.isActive(Month.JANUARY))
    }

    @Test
    fun `built in and seasonal categories round trip through home settings`() {
        val settings =
            HomePageSettings(
                rows =
                    listOf(
                        HomeRowConfig.Category(HomeCategory.TOP_RATED_MOVIES),
                        HomeRowConfig.Seasonal(SeasonalCategory.CHRISTMAS),
                    ),
                version = SUPPORTED_HOME_PAGE_SETTINGS_VERSION,
            )

        val encoded = Json.encodeToString(settings)
        val decoded = Json.decodeFromString<HomePageSettings>(encoded)

        assertEquals(settings, decoded)
    }
}
