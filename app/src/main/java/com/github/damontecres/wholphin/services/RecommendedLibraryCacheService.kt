package com.github.damontecres.wholphin.services

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

        fun get(
            userId: UUID,
            parentId: UUID,
        ): List<HomeRowLoadingState>? {
            val entry = cache[CacheKey(userId, parentId)] ?: return null
            if (System.currentTimeMillis() - entry.cachedAtMs > CACHE_TTL_MS) {
                cache.remove(CacheKey(userId, parentId))
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

        companion object {
            private const val CACHE_TTL_MS = 10 * 60 * 1000L
        }
    }
