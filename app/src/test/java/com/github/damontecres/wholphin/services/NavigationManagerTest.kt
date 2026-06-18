package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.ui.nav.Destination
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationManagerTest {
    @Test
    fun navigateToFromDrawer_ignoresCurrentDestination() {
        val navigationManager = NavigationManager()
        val favorites = Destination.Favorites
        val initialBackStack = MutationTrackingList(mutableListOf<Destination>(Destination.Home(), favorites))
        navigationManager.backStack = initialBackStack

        navigationManager.navigateToFromDrawer(favorites)

        assertEquals(0, initialBackStack.mutationCount)
        assertEquals(listOf(Destination.Home(), favorites), navigationManager.backStack)
    }

    private class MutationTrackingList<T>(
        private val delegate: MutableList<T>,
    ) : MutableList<T> by delegate {
        var mutationCount = 0

        override fun add(element: T): Boolean {
            mutationCount++
            return delegate.add(element)
        }

        override fun removeAt(index: Int): T {
            mutationCount++
            return delegate.removeAt(index)
        }
    }
}
