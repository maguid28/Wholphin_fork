@file:UseSerializers(
    UUIDSerializer::class,
    LocalDateTimeSerializer::class,
)

package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.util.LocalDateTimeSerializer
import com.github.damontecres.wholphin.util.supportItemKinds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import timber.log.Timber
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/**
 * Get continue watching and next up items for users
 */
@Singleton
class LatestNextUpService
    @Inject
    constructor(
        private val api: ApiClient,
        private val datePlayedService: DatePlayedService,
        private val displayPreferencesService: DisplayPreferencesService,
        private val favoriteWatchManager: FavoriteWatchManager,
    ) {
        /**
         * Get resume (continue watching) items for a user
         */
        suspend fun getResume(
            userId: UUID,
            limit: Int,
            includeEpisodes: Boolean,
            useSeriesForPrimary: Boolean = true,
            parentId: UUID? = null,
        ): List<BaseItem> {
            val request =
                GetResumeItemsRequest(
                    userId = userId,
                    parentId = parentId,
                    fields = SlimItemFields,
                    limit = limit,
                    includeItemTypes = resumeItemTypes(includeEpisodes, parentId),
                )
            val items =
                api.itemsApi
                    .getResumeItems(request)
                    .content
                    .items
                    .map { BaseItem.from(it, api, useSeriesForPrimary) }
            return items
        }

        suspend fun getResumePage(
            userId: UUID,
            limit: Int,
            startIndex: Int,
            includeEpisodes: Boolean,
            useSeriesForPrimary: Boolean = true,
            parentId: UUID? = null,
        ): Pair<List<BaseItem>, Boolean> {
            val request =
                GetResumeItemsRequest(
                    userId = userId,
                    parentId = parentId,
                    fields = SlimItemFields,
                    limit = limit,
                    startIndex = startIndex,
                    enableTotalRecordCount = true,
                    includeItemTypes = resumeItemTypes(includeEpisodes, parentId),
                )
            val response = api.itemsApi.getResumeItems(request).content
            val items =
                response.items.map { BaseItem.from(it, api, useSeriesForPrimary) }
            return items to hasMoreItems(items.size, limit, startIndex, response.totalRecordCount)
        }

        /**
         * Get next up items for a user
         */
        suspend fun getNextUpPage(
            userId: UUID,
            limit: Int,
            startIndex: Int,
            enableRewatching: Boolean,
            enableResumable: Boolean,
            maxDays: Int,
            useSeriesForPrimary: Boolean = true,
            parentId: UUID? = null,
        ): Pair<List<BaseItem>, Boolean> {
            val removedSeries = getRemovedFromNextUp(userId)
            val nextUpDateCutoff =
                maxDays.takeIf { it > 0 }?.let { LocalDateTime.now().minusDays(it.toLong()) }
            val request =
                GetNextUpRequest(
                    userId = userId,
                    fields = SlimItemFields,
                    imageTypeLimit = 1,
                    parentId = parentId,
                    limit = limit,
                    startIndex = startIndex,
                    enableTotalRecordCount = true,
                    enableResumable = enableResumable,
                    enableUserData = true,
                    enableRewatching = enableRewatching,
                    nextUpDateCutoff = nextUpDateCutoff,
                )
            val response = api.tvShowsApi.getNextUp(request).content
            val nextUp =
                response.items
                    .map { BaseItem.from(it, api, useSeriesForPrimary) }
                    .filter {
                        val seriesId = it.data.seriesId
                        if (seriesId != null && seriesId in removedSeries) {
                            val lastPlayedDate = it.data.userData?.lastPlayedDate
                            if (lastPlayedDate != null) {
                                lastPlayedDate > removedSeries[seriesId]
                            } else {
                                false
                            }
                        } else {
                            true
                        }
                    }

            return nextUp to hasMoreItems(nextUp.size, limit, startIndex, response.totalRecordCount)
        }

        private fun hasMoreItems(
            itemCount: Int,
            limit: Int,
            startIndex: Int,
            totalRecordCount: Int?,
        ): Boolean =
            when {
                itemCount <= 0 -> false
                totalRecordCount != null -> totalRecordCount > startIndex + itemCount
                else -> itemCount >= limit
            }

        /**
         * Get next up items for a user
         */
        suspend fun getNextUp(
            userId: UUID,
            limit: Int,
            enableRewatching: Boolean,
            enableResumable: Boolean,
            maxDays: Int,
            useSeriesForPrimary: Boolean = true,
            parentId: UUID? = null,
        ): List<BaseItem> {
            val removedSeries = getRemovedFromNextUp(userId)
            val nextUpDateCutoff =
                maxDays.takeIf { it > 0 }?.let { LocalDateTime.now().minusDays(it.toLong()) }
            val request =
                GetNextUpRequest(
                    userId = userId,
                    fields = SlimItemFields,
                    imageTypeLimit = 1,
                    parentId = parentId,
                    limit = limit,
                    enableResumable = enableResumable,
                    enableUserData = true,
                    enableRewatching = enableRewatching,
                    nextUpDateCutoff = nextUpDateCutoff,
                )
            val nextUp =
                api.tvShowsApi
                    .getNextUp(request)
                    .content
                    .items
                    .map { BaseItem.from(it, api, useSeriesForPrimary) }
                    .filter {
                        val seriesId = it.data.seriesId
                        if (seriesId != null && seriesId in removedSeries) {
                            // User has previously removed the series
                            val lastPlayedDate = it.data.userData?.lastPlayedDate
                            if (lastPlayedDate != null) {
                                // If item played it after it was removed, should include it
                                lastPlayedDate > removedSeries[seriesId]
                            } else {
                                // If unknown last played, filter out
                                false
                            }
                        } else {
                            true
                        }
                    }

            return nextUp
        }

        /**
         * Merge resume and next up into one row.
         *
         * The first page includes the full merged set from both sources (up to [limit] each)
         * so Next Up is not buried under a full Continue Watching page.
         */
        suspend fun fetchCombinedContinueWatching(
            userId: UUID,
            limit: Int,
            startIndex: Int,
            includeEpisodes: Boolean,
            enableRewatching: Boolean,
            enableResumable: Boolean,
            maxDays: Int,
            useSeriesForPrimary: Boolean = true,
            parentId: UUID? = null,
        ): Pair<List<BaseItem>, Boolean> {
            val sourceLimit = if (startIndex == 0) limit else startIndex + limit
            val (resumePage, nextUpPage) =
                coroutineScope {
                    val resumeDeferred =
                        async {
                            getResumePage(
                                userId = userId,
                                limit = sourceLimit,
                                startIndex = 0,
                                includeEpisodes = includeEpisodes,
                                useSeriesForPrimary = useSeriesForPrimary,
                                parentId = parentId,
                            )
                        }
                    val nextUpDeferred =
                        async {
                            getNextUpPage(
                                userId = userId,
                                limit = sourceLimit,
                                startIndex = 0,
                                enableRewatching = enableRewatching,
                                enableResumable = enableResumable,
                                maxDays = maxDays,
                                useSeriesForPrimary = useSeriesForPrimary,
                                parentId = parentId,
                            )
                        }
                    resumeDeferred.await() to nextUpDeferred.await()
                }
            val (resume, resumeHasMore) = resumePage
            val (nextUp, nextUpHasMore) = nextUpPage
            val uniqueNextUp = nextUpItemsNotInResume(resume, nextUp)
            val combined =
                if (uniqueNextUp.isEmpty()) {
                    resume
                } else {
                    buildCombined(resume, uniqueNextUp)
                }
            val page =
                if (startIndex == 0) {
                    combined
                } else {
                    combined.drop(startIndex).take(limit)
                }
            val hasMore =
                combined.size > startIndex + page.size ||
                    resumeHasMore ||
                    nextUpHasMore
            return page to hasMore
        }

        /**
         * Create the combined Continue Watching & Next Up items
         *
         * @see [DatePlayedService]
         */
        suspend fun buildCombined(
            resume: List<BaseItem>,
            nextUp: List<BaseItem>,
        ): List<BaseItem> =
            withContext(Dispatchers.IO) {
                val start = System.currentTimeMillis()
                val semaphore = Semaphore(3)
                val nextUpTimestamps =
                    nextUp
                        .filter { it.data.seriesId != null }
                        .map { item ->
                            async(Dispatchers.IO) {
                                item.id to
                                    try {
                                        semaphore.withPermit {
                                            datePlayedService.getLastPlayed(item)
                                        }
                                    } catch (ex: Exception) {
                                        Timber.e(ex, "Error fetching %s", item.id)
                                        null
                                    }
                            }
                        }.awaitAll()
                        .toMap()
                val timestamps = mutableMapOf<UUID, LocalDateTime?>()
                timestamps.putAll(nextUpTimestamps)
                resume.forEach { timestamps[it.id] = it.data.userData?.lastPlayedDate }
                val result =
                    (resume + nextUp)
                        .distinctBy { it.id }
                        .sortedByDescending { timestamps[it.id] ?: it.data.userData?.lastPlayedDate }
                val duration = (System.currentTimeMillis() - start).milliseconds
                Timber.v("buildCombined took %s", duration)
                return@withContext result
            }

        /**
         * Remove a series from next up
         */
        suspend fun removeFromNextUp(
            userId: UUID,
            episode: BaseItem,
        ) {
            favoriteWatchManager.setWatched(episode.id, false)
            episode.data.seriesId?.let { seriesId ->
                displayPreferencesService.updateDisplayPreferences(userId) {
                    val removedIds =
                        get(REMOVED_KEY)
                            ?.let {
                                Json.decodeFromString<RemovedSeriesIds>(it).value
                            }.orEmpty()
                            .toMutableMap()
                    removedIds[seriesId] = LocalDateTime.now()
                    put(
                        REMOVED_KEY,
                        Json.encodeToString(RemovedSeriesIds(removedIds)),
                    )
                }
            }
        }

        /**
         * Get when series were removed from next up
         */
        suspend fun getRemovedFromNextUp(userId: UUID): Map<UUID, LocalDateTime> =
            displayPreferencesService
                .getDisplayPreferences(userId)
                .customPrefs[REMOVED_KEY]
                ?.let {
                    Json.decodeFromString<RemovedSeriesIds>(it).value
                }.orEmpty()

        suspend fun allowSeriesRemovedFromNextUp(
            userId: UUID,
            seriesId: UUID,
        ) {
            displayPreferencesService.updateDisplayPreferences(userId) {
                val ids =
                    get(REMOVED_KEY)
                        ?.let {
                            Json.decodeFromString<RemovedSeriesIds>(it).value
                        }.orEmpty()
                        .toMutableMap()
                ids.remove(seriesId)
                put(
                    REMOVED_KEY,
                    Json.encodeToString(RemovedSeriesIds(ids)),
                )
            }
        }

        /**
         * Check if user has watched a series since removing it
         */
        suspend fun updateRemovedFromNextUp(userId: UUID) {
            val removed = getRemovedFromNextUp(userId)
            val newRemoved = removed.toMutableMap()
            var changed = false
            removed.forEach { (seriesId, timestamp) ->
                val item =
                    api.itemsApi
                        .getItems(
                            userId = userId,
                            parentId = seriesId,
                            recursive = true,
                            includeItemTypes = listOf(BaseItemKind.EPISODE),
                            sortBy = listOf(ItemSortBy.DATE_PLAYED),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            limit = 1,
                        ).content.items
                        .firstOrNull()
                if (item != null) {
                    val lastPlayed = item.userData?.lastPlayedDate
                    if (lastPlayed != null && lastPlayed > timestamp) {
                        Timber.v("Updating removed next up for series %s", seriesId)
                        newRemoved.remove(seriesId)
                        changed = true
                    }
                } else {
                    // Series doesn't exist anymore
                    Timber.v("Updating removed next up for missing series %s", seriesId)
                    newRemoved.remove(seriesId)
                    changed = true
                }
            }
            if (changed) {
                displayPreferencesService.updateDisplayPreferences(userId) {
                    put(
                        REMOVED_KEY,
                        Json.encodeToString(RemovedSeriesIds(newRemoved)),
                    )
                }
            }
        }

        companion object {
            const val REMOVED_KEY = "removeNextUp"

            internal fun nextUpItemsNotInResume(
                resume: List<BaseItem>,
                nextUp: List<BaseItem>,
            ): List<BaseItem> {
                val resumeIds = resume.map { it.id }.toSet()
                val resumeSeriesIds = resume.mapNotNull { it.data.seriesId }.toSet()
                return nextUp.filter { item ->
                    item.id !in resumeIds &&
                        (item.data.seriesId == null || item.data.seriesId !in resumeSeriesIds)
                }
            }
        }

        private fun resumeItemTypes(
            includeEpisodes: Boolean,
            parentId: UUID?,
        ): List<BaseItemKind> {
            if (parentId != null) {
                return if (includeEpisodes) {
                    listOf(BaseItemKind.EPISODE)
                } else {
                    listOf(BaseItemKind.MOVIE)
                }
            }
            return if (includeEpisodes) {
                supportItemKinds.toList()
            } else {
                supportItemKinds
                    .toMutableSet()
                    .apply {
                        remove(BaseItemKind.EPISODE)
                    }.toList()
            }
        }
    }

@Serializable
data class RemovedSeriesIds(
    val value: Map<UUID, LocalDateTime>,
)
