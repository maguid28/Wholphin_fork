package com.github.damontecres.wholphin.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class TabRowTest {
    @Test
    fun tabRowItemKeys_usesUniqueKeysForBlankTabs() {
        val keys = tabRowItemKeys(listOf("", "", ""), listOf("", "", ""))

        assertEquals(listOf("tab:0", "tab:1", "tab:2"), keys)
    }

    @Test
    fun tabRowItemKeys_deduplicatesProvidedKeys() {
        val keys =
            tabRowItemKeys(
                listOf("Season", "Season", "Season"),
                listOf("season", "season", "season"),
            )

        assertEquals(keys.size, keys.toSet().size)
        assertEquals("season", keys.first())
    }
}
