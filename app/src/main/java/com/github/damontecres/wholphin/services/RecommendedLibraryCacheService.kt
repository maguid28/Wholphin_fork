package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class RecommendedListUiState(
    val focus: RowColumn = RowColumn(-1, -1),
    val firstVisibleItemIndex: Int = 0,
    val firstVisibleItemScrollOffset: Int = 0,
)

@Singleton
class RecommendedLibraryCacheService
    @Inject
    constructor() {
        private data class CacheKey(
            val userId: UUID,
            val parentId: UUID,
        )

        private data class Entry(
            val rows: List<HomeRowLoadingState>,
            val cachedAtMs: Long,
        )

        private val cache = mutableMapOf<CacheKey, Entry>()
        private val listUiStates = mutableMapOf<CacheKey, RecommendedListUiState>()

        fun get(
            userId: UUID,
            parentId: UUID,
        ): List<HomeRowLoadingState>? {
            val key = CacheKey(userId, parentId)
            val entry = cache[key] ?: return null
            if (System.currentTimeMillis() - entry.cachedAtMs > CACHE_TTL_MS) {
                cache.remove(key)
                listUiStates.remove(key)
                return null
            }
            return entry.rows
        }

        fun put(
            userId: UUID,
            parentId: UUID,
            rows: List<HomeRowLoadingState>,
        ) {
            cache[CacheKey(userId, parentId)] =
                Entry(
                    rows = rows,
                    cachedAtMs = System.currentTimeMillis(),
                )
        }

        fun getListUiState(
            userId: UUID,
            parentId: UUID,
        ): RecommendedListUiState? = listUiStates[CacheKey(userId, parentId)]

        fun saveListUiState(
            userId: UUID,
            parentId: UUID,
            state: RecommendedListUiState,
        ) {
            if (state.focus.row < 0 && state.firstVisibleItemIndex <= 0 && state.firstVisibleItemScrollOffset <= 0) {
                return
            }
            listUiStates[CacheKey(userId, parentId)] = state
        }

        companion object {
            private const val CACHE_TTL_MS = 10 * 60 * 1000L
        }
    }
