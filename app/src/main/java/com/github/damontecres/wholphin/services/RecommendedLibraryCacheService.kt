package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

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
        private val focusPositions = mutableMapOf<CacheKey, RowColumn>()

        fun get(
            userId: UUID,
            parentId: UUID,
        ): List<HomeRowLoadingState>? {
            val key = CacheKey(userId, parentId)
            val entry = cache[key] ?: return null
            if (System.currentTimeMillis() - entry.cachedAtMs > CACHE_TTL_MS) {
                cache.remove(key)
                focusPositions.remove(key)
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

        fun getFocusPosition(
            userId: UUID,
            parentId: UUID,
        ): RowColumn? = focusPositions[CacheKey(userId, parentId)]

        fun saveFocusPosition(
            userId: UUID,
            parentId: UUID,
            position: RowColumn,
        ) {
            if (position.row < 0) {
                return
            }
            focusPositions[CacheKey(userId, parentId)] = position
        }

        companion object {
            private const val CACHE_TTL_MS = 10 * 60 * 1000L
        }
    }
