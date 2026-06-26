package com.github.damontecres.wholphin.ui.detail.librarytv

import android.os.SystemClock
import android.text.format.DateUtils
import androidx.annotation.OptIn
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.contentColorFor
import androidx.tv.material3.surfaceColorAtElevation
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.LibraryTvWatchedEpisodeDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.preferences.updateLiveTvPreferences
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.hilt.DefaultDispatcher
import com.github.damontecres.wholphin.services.hilt.IoCoroutineScope
import com.github.damontecres.wholphin.services.hilt.IoDispatcher
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.Network
import com.github.damontecres.wholphin.ui.components.PopularNetworks
import com.github.damontecres.wholphin.ui.components.networkLogoAsset
import com.github.damontecres.wholphin.ui.components.networkLogoAssetForKey
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.playback.LibraryTvPlaybackInfo
import com.github.damontecres.wholphin.ui.playback.PlaybackViewModel
import com.github.damontecres.wholphin.ui.playback.PlayerState
import com.github.damontecres.wholphin.ui.preferences.SwitchPreference
import com.github.damontecres.wholphin.ui.toBaseItems
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.GetEpisodesRequestHandler
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.extensions.ticks
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.minutes

private const val LibraryTvEpisodePageSize = 300
private const val LibraryTvMaxEpisodePages = 2
private const val LibraryTvMoviePageSize = 240
private const val LibraryTvMaxMoviePages = 2
private const val LibraryTvSeriesPageSize = 240
private const val LibraryTvMaxSeriesPages = 1
private const val LibraryTvTargetShowsPerChannel = 5
private const val LibraryTvMaxFallbackSeriesNetworkLookups = 200
private const val LibraryTvMaxSupplementalSeries = 48
private const val LibraryTvSupplementalEpisodesPerShow = 10
private const val LibraryTvSupplementalFetchConcurrency = 6
private const val LibraryTvMinEpisodesPerShowInCycle = 4
private const val LibraryTvMaxChannels = 96
private const val LibraryTvMaxEpisodesPerShowInCycle = 24
private const val LibraryTvMaxShortEpisodeBlock = 2
private const val LibraryTvMaxQuickFallbackSeriesNetworkLookups = 90
private const val LibraryTvMinEpisodesPerGenreChannel = 3
private const val LibraryTvMinMoviesPerGenreChannel = 3
private const val LibraryTvGuideHours = 3L
private const val LibraryTvVisibleHours = 2f
private const val LibraryTvVisibleMinutes = 120L
private const val LibraryTvNowInsetMinutes = 15L
private const val LibraryTvTimeBucketMinutes = 10L
private const val LibraryTvStaleRepeatInputMs = 120L
private val LibraryTvGuideRefreshInterval = 5.minutes
private val LibraryTvDefaultDurationMs = 30.minutes.inWholeMilliseconds
private val LibraryTvMinimumDurationMs = 5.minutes.inWholeMilliseconds
private val LibraryTvShortEpisodeThresholdMs = 28.minutes.inWholeMilliseconds
private val LibraryTvChannelOffsetMs = 13.minutes.inWholeMilliseconds
private val LibraryTvScheduleAnchor =
    ZonedDateTime
        .of(2024, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault())
        .toInstant()

private val LibraryTvItemFields =
    listOf(
        ItemFields.GENRES,
        ItemFields.OVERVIEW,
        ItemFields.SORT_NAME,
        ItemFields.STUDIOS,
        ItemFields.SERIES_STUDIO,
    )

private data class LibraryTvGuideCacheKey(
    val userId: UUID,
)

object LibraryTvGuideMemoryCache {
    private var key: LibraryTvGuideCacheKey? = null
    private var guide: LibraryTvGuideState? = null

    @Synchronized
    fun get(
        userId: UUID,
        now: Instant,
    ): LibraryTvGuideState? =
        guide?.takeIf {
            key == LibraryTvGuideCacheKey(userId) &&
                !now.isBefore(it.windowStart) &&
                !now.plusSeconds(LibraryTvVisibleMinutes * 60).isAfter(it.windowEnd)
        }

    @Synchronized
    fun put(
        userId: UUID,
        guide: LibraryTvGuideState,
    ) {
        key = LibraryTvGuideCacheKey(userId)
        this.guide = guide
    }

    @Synchronized
    fun channelOptions(): List<LibraryTvChannelOption> =
        guide
            ?.channels
            ?.map { LibraryTvChannelOption(it.key, it.name) }
            .orEmpty()

    @Synchronized
    fun currentGuide(): LibraryTvGuideState? = guide

    @Synchronized
    fun currentGuide(userId: UUID): LibraryTvGuideState? =
        guide?.takeIf {
            key == LibraryTvGuideCacheKey(userId)
        }

    @Synchronized
    fun clear() {
        key = null
        guide = null
    }
}

@Singleton
class LibraryTvGuideService
    @Inject
    constructor(
        @param:IoCoroutineScope private val ioScope: CoroutineScope,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
        private val api: ApiClient,
        private val imageUrlService: ImageUrlService,
        private val watchedEpisodeDao: LibraryTvWatchedEpisodeDao,
    ) {
        private val loadingMutex = Mutex()
        private var inFlightUserId: UUID? = null
        private var inFlight: Deferred<LibraryTvGuideState>? = null

        fun cachedGuide(
            user: JellyfinUser,
            now: Instant = Instant.now(),
        ): LibraryTvGuideState? = cachedGuide(user.id, now)

        fun cachedGuide(
            userId: UUID,
            now: Instant = Instant.now(),
        ): LibraryTvGuideState? = LibraryTvGuideMemoryCache.get(userId, now)

        fun warm(user: JellyfinUser) {
            if (cachedGuide(user) != null) return
            ioScope.launch {
                try {
                    load(user)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not warm Library TV guide")
                }
            }
        }

        suspend fun load(user: JellyfinUser): LibraryTvGuideState {
            cachedGuide(user)?.let { return it }
            val deferred =
                loadingMutex.withLock {
                    cachedGuide(user)?.let { cachedGuide ->
                        return@withLock CompletableDeferred(cachedGuide)
                    }
                    inFlight
                        ?.takeIf { inFlightUserId == user.id && it.isActive }
                        ?: ioScope
                            .async {
                                buildOrExtendGuide(user)
                            }.also {
                                inFlightUserId = user.id
                                inFlight = it
                            }
                }
            return try {
                deferred.await()
            } finally {
                loadingMutex.withLock {
                    if (inFlight === deferred && deferred.isCompleted) {
                        inFlight = null
                        inFlightUserId = null
                    }
                }
            }
        }

        private suspend fun buildOrExtendGuide(user: JellyfinUser): LibraryTvGuideState {
            val now = Instant.now().roundDownToMinutes(LibraryTvTimeBucketMinutes)
            cachedGuide(user, now)?.let { return it }

            val currentGuide = LibraryTvGuideMemoryCache.currentGuide(user.id)
            if (currentGuide != null && currentGuide.channels.isNotEmpty()) {
                val extensionStart =
                    if (now.isAfter(currentGuide.windowEnd)) {
                        now.minusSeconds(LibraryTvNowInsetMinutes * 60)
                    } else {
                        currentGuide.windowEnd
                    }
                val extensionEnd =
                    maxInstant(
                        now.plusSeconds(LibraryTvGuideHours * 60 * 60),
                        extensionStart.plusSeconds(LibraryTvGuideHours * 60 * 60),
                    )
                val extension =
                    buildGuideWindow(
                        user = user,
                        windowStart = extensionStart,
                        windowEnd = extensionEnd,
                        extraAvoidedEpisodeIds = currentGuide.scheduledEpisodeIds(),
                    )
                val mergedGuide =
                    currentGuide.append(
                        extension = extension,
                        pruneBefore = now.minusSeconds(LibraryTvNowInsetMinutes * 60),
                    )
                if (mergedGuide.channels.isNotEmpty()) {
                    LibraryTvGuideMemoryCache.put(user.id, mergedGuide)
                    return mergedGuide
                }
            }

            return buildFreshGuide(user, now)
        }

        private suspend fun buildFreshGuide(
            user: JellyfinUser,
            now: Instant = Instant.now().roundDownToMinutes(LibraryTvTimeBucketMinutes),
        ): LibraryTvGuideState {
            val windowStart = now.minusSeconds(LibraryTvNowInsetMinutes * 60)
            val windowEnd = now.plusSeconds(LibraryTvGuideHours * 60 * 60)
            val guide =
                buildGuideWindow(
                    user = user,
                    windowStart = windowStart,
                    windowEnd = windowEnd,
                )
            if (guide.channels.isNotEmpty()) {
                LibraryTvGuideMemoryCache.put(user.id, guide)
            }
            return guide
        }

        private suspend fun buildGuideWindow(
            user: JellyfinUser,
            windowStart: Instant,
            windowEnd: Instant,
            extraAvoidedEpisodeIds: Set<UUID> = emptySet(),
        ): LibraryTvGuideState {
            val (episodes, movies, seriesNetworkNames) =
                withContext(ioDispatcher) {
                    val views =
                        api.userViewsApi
                            .getUserViews(userId = user.id)
                            .content
                            .items
                    val tvLibraries =
                        views.filter {
                            (it.collectionType ?: CollectionType.UNKNOWN) == CollectionType.TVSHOWS
                        }
                    val movieLibraries =
                        views.filter {
                            (it.collectionType ?: CollectionType.UNKNOWN) == CollectionType.MOVIES
                        }
                    val folderLibraries =
                        views.filter {
                            (it.collectionType ?: CollectionType.UNKNOWN) == CollectionType.FOLDERS
                        }

                    val tvLibraryIds = tvLibraries.ifEmpty { folderLibraries }.mapNotNull { it.id }
                    val movieLibraryIds = movieLibraries.ifEmpty { folderLibraries }.mapNotNull { it.id }
                    val (episodes, movies, series) =
                        coroutineScope {
                            val episodesDeferred = async { fetchLibraryEpisodes(user.id, tvLibraryIds) }
                            val moviesDeferred = async { fetchLibraryMovies(user.id, movieLibraryIds) }
                            val seriesDeferred = async { fetchLibrarySeries(user.id, tvLibraryIds) }
                            Triple(episodesDeferred.await(), moviesDeferred.await(), seriesDeferred.await())
                        }
                    val sampledSeriesNetworkNames = episodes.inferredSeriesNetworkNames()
                    val fetchedSeriesNetworkNames =
                        series
                            .associate { it.id to it.networkNames() }
                            .filterValues { it.isNotEmpty() }
                    val fallbackSeriesNetworkNames =
                        fetchFallbackSeriesNetworkNames(
                            userId = user.id,
                            episodes = episodes,
                            maxLookups = LibraryTvMaxQuickFallbackSeriesNetworkLookups,
                        )
                    val seriesNetworkNames =
                        mergeSeriesNetworkNames(
                            sampledSeriesNetworkNames,
                            fetchedSeriesNetworkNames,
                            fallbackSeriesNetworkNames,
                        )
                    val supplementalEpisodes =
                        fetchSupplementalNetworkEpisodes(
                            series = series,
                            existingEpisodes = episodes,
                            seriesNetworkNames = seriesNetworkNames,
                            windowStart = windowStart,
                        )
                    Triple((episodes + supplementalEpisodes).distinctBy { it.id }, movies, seriesNetworkNames)
                }
            val guide =
                withContext(defaultDispatcher) {
                    buildGuideState(
                        windowStart = windowStart,
                        windowEnd = windowEnd,
                        episodes = episodes,
                        movies = movies,
                        seriesNetworkNames = seriesNetworkNames,
                        avoidedEpisodeIds =
                            watchedEpisodeDao
                                .getWatchedEpisodeIds(user.rowId)
                                .toSet() + extraAvoidedEpisodeIds,
                    )
                }
            return guide
        }

        private suspend fun fetchLibraryEpisodes(
            userId: UUID,
            libraryIds: List<UUID>,
        ): List<BaseItem> =
            coroutineScope {
                libraryIds
                    .map { libraryId ->
                        async {
                            val libraryEpisodes = mutableListOf<BaseItem>()
                            repeat(LibraryTvMaxEpisodePages) { page ->
                                val fetched =
                                    GetItemsRequestHandler
                                        .execute(
                                            api,
                                            GetItemsRequest(
                                                userId = userId,
                                                parentId = libraryId,
                                                recursive = true,
                                                includeItemTypes = listOf(BaseItemKind.EPISODE),
                                                fields = LibraryTvItemFields,
                                                sortBy = listOf(ItemSortBy.RANDOM),
                                                sortOrder = listOf(SortOrder.ASCENDING),
                                                startIndex = page * LibraryTvEpisodePageSize,
                                                limit = LibraryTvEpisodePageSize,
                                                enableUserData = false,
                                                enableTotalRecordCount = false,
                                                enableImages = false,
                                            ),
                                        ).toBaseItems(api, useSeriesForPrimary = true)
                                        .filter { it.playable }
                                libraryEpisodes.addAll(fetched)
                                if (fetched.size < LibraryTvEpisodePageSize) {
                                    return@async libraryEpisodes
                                }
                            }
                            libraryEpisodes
                        }
                    }.awaitAll()
                    .flatten()
                    .distinctBy { it.id }
            }

        private suspend fun fetchLibrarySeries(
            userId: UUID,
            libraryIds: List<UUID>,
        ): List<BaseItem> =
            coroutineScope {
                libraryIds
                    .map { libraryId ->
                        async {
                            val librarySeries = mutableListOf<BaseItem>()
                            repeat(LibraryTvMaxSeriesPages) { page ->
                                val fetched =
                                    GetItemsRequestHandler
                                        .execute(
                                            api,
                                            GetItemsRequest(
                                                userId = userId,
                                                parentId = libraryId,
                                                recursive = true,
                                                includeItemTypes = listOf(BaseItemKind.SERIES),
                                                fields = LibraryTvItemFields,
                                                sortBy = listOf(ItemSortBy.RANDOM),
                                                sortOrder = listOf(SortOrder.ASCENDING),
                                                startIndex = page * LibraryTvSeriesPageSize,
                                                limit = LibraryTvSeriesPageSize,
                                                enableUserData = false,
                                                enableTotalRecordCount = false,
                                                enableImages = false,
                                            ),
                                        ).toBaseItems(api, useSeriesForPrimary = false)
                                librarySeries.addAll(fetched)
                                if (fetched.size < LibraryTvSeriesPageSize) {
                                    return@async librarySeries
                                }
                            }
                            librarySeries
                        }
                    }.awaitAll()
                    .flatten()
                    .distinctBy { it.id }
            }

        private suspend fun fetchLibraryMovies(
            userId: UUID,
            libraryIds: List<UUID>,
        ): List<BaseItem> =
            coroutineScope {
                libraryIds
                    .map { libraryId ->
                        async {
                            val libraryMovies = mutableListOf<BaseItem>()
                            repeat(LibraryTvMaxMoviePages) { page ->
                                val fetched =
                                    GetItemsRequestHandler
                                        .execute(
                                            api,
                                            GetItemsRequest(
                                                userId = userId,
                                                parentId = libraryId,
                                                recursive = true,
                                                includeItemTypes = listOf(BaseItemKind.MOVIE),
                                                fields = LibraryTvItemFields,
                                                sortBy = listOf(ItemSortBy.RANDOM),
                                                sortOrder = listOf(SortOrder.ASCENDING),
                                                startIndex = page * LibraryTvMoviePageSize,
                                                limit = LibraryTvMoviePageSize,
                                                enableUserData = false,
                                                enableTotalRecordCount = false,
                                                enableImages = false,
                                            ),
                                        ).toBaseItems(api, useSeriesForPrimary = false)
                                        .filter { it.playable }
                                libraryMovies.addAll(fetched)
                                if (fetched.size < LibraryTvMoviePageSize) {
                                    return@async libraryMovies
                                }
                            }
                            libraryMovies
                        }
                    }.awaitAll()
                    .flatten()
                    .distinctBy { it.id }
            }

        private suspend fun fetchFallbackSeriesNetworkNames(
            userId: UUID,
            episodes: List<BaseItem>,
            maxLookups: Int = LibraryTvMaxFallbackSeriesNetworkLookups,
        ): Map<UUID, List<String>> {
            val seriesIds =
                episodes
                    .filter { it.networkNames().isEmpty() }
                    .mapNotNull { it.data.seriesId }
                    .distinct()
                    .take(maxLookups)
            if (seriesIds.isEmpty()) return emptyMap()

            return coroutineScope {
                seriesIds
                    .chunked(100)
                    .map { ids ->
                        async {
                            api.itemsApi
                                .getItems(
                                    userId = userId,
                                    ids = ids,
                                    fields = LibraryTvItemFields,
                                    enableImages = false,
                                ).toBaseItems(api, useSeriesForPrimary = false)
                        }
                    }.awaitAll()
                    .flatten()
                    .associate { it.id to it.networkNames() }
            }
        }

        private suspend fun fetchSupplementalNetworkEpisodes(
            series: List<BaseItem>,
            existingEpisodes: List<BaseItem>,
            seriesNetworkNames: Map<UUID, List<String>>,
            windowStart: Instant,
        ): List<BaseItem> {
            val candidateSeriesIds =
                supplementalLibraryTvSeriesIds(
                    series = series,
                    existingEpisodes = existingEpisodes,
                    seriesNetworkNames = seriesNetworkNames,
                    windowStart = windowStart,
                    maxCandidates = LibraryTvMaxSupplementalSeries,
                )
            if (candidateSeriesIds.isEmpty()) return emptyList()

            val episodes = mutableListOf<BaseItem>()
            candidateSeriesIds.chunked(LibraryTvSupplementalFetchConcurrency).forEach { batch ->
                episodes +=
                    coroutineScope {
                        batch
                            .map { seriesId ->
                                async {
                                    fetchSeriesEpisodes(seriesId)
                                }
                            }.awaitAll()
                            .flatten()
                    }
            }
            return episodes.distinctBy { it.id }
        }

        private suspend fun fetchSeriesEpisodes(seriesId: UUID): List<BaseItem> =
            GetEpisodesRequestHandler
                .execute(
                    api,
                    GetEpisodesRequest(
                        seriesId = seriesId,
                        fields = LibraryTvItemFields,
                        sortBy = ItemSortBy.INDEX_NUMBER,
                        limit = LibraryTvSupplementalEpisodesPerShow,
                    ),
                ).toBaseItems(api, useSeriesForPrimary = true)
                .filter { it.playable }

        private fun buildGuideState(
            windowStart: Instant,
            windowEnd: Instant,
            episodes: List<BaseItem>,
            movies: List<BaseItem>,
            seriesNetworkNames: Map<UUID, List<String>>,
            avoidedEpisodeIds: Set<UUID> = emptySet(),
        ): LibraryTvGuideState {
            val networkGroups =
                groupEpisodesByNetwork(
                    episodes = episodes,
                    seriesNetworkNames = seriesNetworkNames,
                )
            val networkKeys = networkGroups.map { it.network.key }.toSet()
            val episodeGenreGroups =
                groupEpisodesByGenre(episodes)
                    .filterNot { it.network.key in networkKeys }
            val movieGenreGroups = groupMoviesByGenre(movies)
            val channelPlans =
                (networkGroups + episodeGenreGroups + movieGenreGroups)
                    .asSequence()
                    .mapNotNull { group ->
                        val items =
                            group.items.buildNaturalScheduleCycle(
                                channelKey = group.network.key,
                                windowStart = windowStart,
                                avoidedEpisodeIds = avoidedEpisodeIds,
                            )
                        if (items.isEmpty()) null else LibraryTvChannelPlan(group, items)
                    }.take(LibraryTvMaxChannels)
                    .toList()
            val channels =
                channelPlans
                    .mapIndexed { index, plan ->
                        val group = plan.group
                        val showImageUrls = mutableMapOf<String, String?>()
                        val scheduledItems =
                            plan.items.map {
                                LibraryTvScheduledItem(
                                    item = it,
                                    imageUrl =
                                        showImageUrls.getOrPut(it.seriesKey()) {
                                            imageUrlService.getItemImageUrl(
                                                item = it,
                                                imageType = ImageType.PRIMARY,
                                                fillWidth = 96,
                                                fillHeight = 54,
                                                useSeriesForPrimary = it.type == BaseItemKind.EPISODE,
                                            )
                                        },
                                    durationMs = it.libraryTvDurationMs(),
                                )
                            }
                        LibraryTvChannel(
                            key = group.network.key,
                            id = group.network.id,
                            number = index + 1,
                            name = group.network.name,
                            imageUrl = group.network.imageUrl,
                            programs =
                                buildPrograms(
                                    channelKey = group.network.key,
                                    channelId = group.network.id,
                                    channelName = group.network.name,
                                    channelImageUrl = group.network.imageUrl,
                                    items = scheduledItems,
                                    windowStart = windowStart,
                                    windowEnd = windowEnd,
                                    channelIndex = index,
                                ),
                        )
                    }
            return LibraryTvGuideState(
                channels = channels,
                windowStart = windowStart,
                windowEnd = windowEnd,
            )
        }
    }

@HiltViewModel
class LibraryTvViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val navigationManager: NavigationManager,
        private val imageUrlService: ImageUrlService,
        private val libraryTvGuideService: LibraryTvGuideService,
    ) : ViewModel() {
        var lastFocusedProgramKey: LibraryTvProgramFocusKey? = null
            private set
        var lastFocusedChannelKey: String? = null
            private set

        private val _loading =
            MutableStateFlow<DataLoadingState<LibraryTvGuideState>>(DataLoadingState.Pending)
        val loading = _loading.asStateFlow()

        init {
            load()
            startGuideRefresh()
        }

        fun load() {
            viewModelScope.launchIO {
                try {
                    val user =
                        serverRepository.currentUser.value
                            ?: throw IllegalStateException("No active Jellyfin user")
                    libraryTvGuideService.cachedGuide(user)?.let { cachedGuide ->
                        _loading.update { DataLoadingState.Success(cachedGuide) }
                        return@launchIO
                    }
                    _loading.update { DataLoadingState.Loading }

                    val guide = libraryTvGuideService.load(user)
                    if (guide.channels.isNotEmpty()) {
                        _loading.update { DataLoadingState.Success(guide) }
                    } else {
                        _loading.update {
                            DataLoadingState.Error(
                                message = "Could not build Library TV",
                                exception = null,
                            )
                        }
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Could not build Library TV")
                    _loading.update {
                        DataLoadingState.Error(
                            message = "Could not build Library TV",
                            exception = ex,
                        )
                    }
                }
            }
        }

        private fun startGuideRefresh() {
            viewModelScope.launchIO {
                while (isActive) {
                    delay(LibraryTvGuideRefreshInterval)
                    try {
                        val user = serverRepository.currentUser.value ?: continue
                        val guide = libraryTvGuideService.load(user)
                        if (guide.channels.isNotEmpty()) {
                            _loading.update { DataLoadingState.Success(guide) }
                        }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Could not refresh Library TV guide")
                    }
                }
            }
        }

        fun rememberFocusedProgram(program: LibraryTvProgram) {
            lastFocusedProgramKey = program.focusKey()
            lastFocusedChannelKey = null
        }

        fun rememberFocusedChannel(
            channelKey: String,
            program: LibraryTvProgram?,
        ) {
            lastFocusedChannelKey = channelKey
            program?.let { lastFocusedProgramKey = it.focusKey() }
        }

        fun play(program: LibraryTvProgram) {
            lastFocusedProgramKey = program.focusKey()
            lastFocusedChannelKey = program.channelKey
            val now = Instant.now()
            val positionMs =
                if (!now.isBefore(program.start) && now.isBefore(program.end)) {
                    (now.toEpochMilli() - program.start.toEpochMilli()).coerceAtLeast(0L)
                } else {
                    0L
                }
            navigationManager.navigateTo(
                Destination.Playback(
                    itemId = program.item.id,
                    positionMs = positionMs,
                    trackPlayback = false,
                    libraryTvChannelKey = program.channelKey,
                ),
            )
        }

        private suspend fun fetchLibraryEpisodes(
            userId: UUID,
            libraryIds: List<UUID>,
        ): List<BaseItem> =
            coroutineScope {
                libraryIds
                    .map { libraryId ->
                        async {
                            val libraryEpisodes = mutableListOf<BaseItem>()
                            repeat(LibraryTvMaxEpisodePages) { page ->
                                val fetched =
                                    GetItemsRequestHandler
                                        .execute(
                                            api,
                                            GetItemsRequest(
                                                userId = userId,
                                                parentId = libraryId,
                                                recursive = true,
                                                includeItemTypes = listOf(BaseItemKind.EPISODE),
                                                fields = LibraryTvItemFields,
                                                sortBy = listOf(ItemSortBy.RANDOM),
                                                sortOrder = listOf(SortOrder.ASCENDING),
                                                startIndex = page * LibraryTvEpisodePageSize,
                                                limit = LibraryTvEpisodePageSize,
                                                enableUserData = false,
                                                enableTotalRecordCount = false,
                                                enableImages = false,
                                            ),
                                        ).toBaseItems(api, useSeriesForPrimary = true)
                                        .filter { it.playable }
                                libraryEpisodes.addAll(fetched)
                                if (fetched.size < LibraryTvEpisodePageSize) {
                                    return@async libraryEpisodes
                                }
                            }
                            libraryEpisodes
                        }
                    }.awaitAll()
                    .flatten()
                    .distinctBy { it.id }
            }

        private suspend fun fetchLibraryMovies(
            userId: UUID,
            libraryIds: List<UUID>,
        ): List<BaseItem> =
            coroutineScope {
                libraryIds
                    .map { libraryId ->
                        async {
                            val libraryMovies = mutableListOf<BaseItem>()
                            repeat(LibraryTvMaxMoviePages) { page ->
                                val fetched =
                                    GetItemsRequestHandler
                                        .execute(
                                            api,
                                            GetItemsRequest(
                                                userId = userId,
                                                parentId = libraryId,
                                                recursive = true,
                                                includeItemTypes = listOf(BaseItemKind.MOVIE),
                                                fields = LibraryTvItemFields,
                                                sortBy = listOf(ItemSortBy.RANDOM),
                                                sortOrder = listOf(SortOrder.ASCENDING),
                                                startIndex = page * LibraryTvMoviePageSize,
                                                limit = LibraryTvMoviePageSize,
                                                enableUserData = false,
                                                enableTotalRecordCount = false,
                                                enableImages = false,
                                            ),
                                        ).toBaseItems(api, useSeriesForPrimary = false)
                                        .filter { it.playable }
                                libraryMovies.addAll(fetched)
                                if (fetched.size < LibraryTvMoviePageSize) {
                                    return@async libraryMovies
                                }
                            }
                            libraryMovies
                        }
                    }.awaitAll()
                    .flatten()
                    .distinctBy { it.id }
            }

        private suspend fun fetchLibrarySeries(
            userId: UUID,
            libraryIds: List<UUID>,
        ): List<BaseItem> =
            coroutineScope {
                libraryIds
                    .map { libraryId ->
                        async {
                            val librarySeries = mutableListOf<BaseItem>()
                            repeat(LibraryTvMaxSeriesPages) { page ->
                                val fetched =
                                    GetItemsRequestHandler
                                        .execute(
                                            api,
                                            GetItemsRequest(
                                                userId = userId,
                                                parentId = libraryId,
                                                recursive = true,
                                                includeItemTypes = listOf(BaseItemKind.SERIES),
                                                fields = LibraryTvItemFields,
                                                sortBy = listOf(ItemSortBy.RANDOM),
                                                sortOrder = listOf(SortOrder.ASCENDING),
                                                startIndex = page * LibraryTvSeriesPageSize,
                                                limit = LibraryTvSeriesPageSize,
                                                enableUserData = false,
                                                enableTotalRecordCount = false,
                                                enableImages = false,
                                            ),
                                        ).toBaseItems(api, useSeriesForPrimary = false)
                                librarySeries.addAll(fetched)
                                if (fetched.size < LibraryTvSeriesPageSize) {
                                    return@async librarySeries
                                }
                            }
                            librarySeries
                        }
                    }.awaitAll()
                    .flatten()
                    .distinctBy { it.id }
            }

        private suspend fun fetchFallbackSeriesNetworkNames(
            userId: UUID,
            episodes: List<BaseItem>,
            maxLookups: Int = LibraryTvMaxFallbackSeriesNetworkLookups,
        ): Map<UUID, List<String>> {
            val seriesIds =
                episodes
                    .filter { it.networkNames().isEmpty() }
                    .mapNotNull { it.data.seriesId }
                    .distinct()
                    .take(maxLookups)
            if (seriesIds.isEmpty()) return emptyMap()

            return coroutineScope {
                seriesIds
                    .chunked(100)
                    .map { ids ->
                        async {
                            api.itemsApi
                                .getItems(
                                    userId = userId,
                                    ids = ids,
                                    fields = LibraryTvItemFields,
                                    enableImages = false,
                                ).toBaseItems(api, useSeriesForPrimary = false)
                        }
                    }.awaitAll()
                    .flatten()
                    .associate { it.id to it.networkNames() }
            }
        }

        private suspend fun fetchSupplementalNetworkEpisodes(
            series: List<BaseItem>,
            existingEpisodes: List<BaseItem>,
            seriesNetworkNames: Map<UUID, List<String>>,
            windowStart: Instant,
        ): List<BaseItem> {
            val candidateSeriesIds =
                supplementalLibraryTvSeriesIds(
                    series = series,
                    existingEpisodes = existingEpisodes,
                    seriesNetworkNames = seriesNetworkNames,
                    windowStart = windowStart,
                    maxCandidates = LibraryTvMaxSupplementalSeries,
                )
            if (candidateSeriesIds.isEmpty()) return emptyList()

            val episodes = mutableListOf<BaseItem>()
            candidateSeriesIds.chunked(LibraryTvSupplementalFetchConcurrency).forEach { batch ->
                episodes +=
                    coroutineScope {
                        batch
                            .map { seriesId ->
                                async {
                                    fetchSeriesEpisodes(seriesId)
                                }
                            }.awaitAll()
                            .flatten()
                    }
            }
            return episodes.distinctBy { it.id }
        }

        private suspend fun fetchSeriesEpisodes(seriesId: UUID): List<BaseItem> =
            GetEpisodesRequestHandler
                .execute(
                    api,
                    GetEpisodesRequest(
                        seriesId = seriesId,
                        fields = LibraryTvItemFields,
                        sortBy = ItemSortBy.INDEX_NUMBER,
                        limit = LibraryTvSupplementalEpisodesPerShow,
                    ),
                ).toBaseItems(api, useSeriesForPrimary = true)
                .filter { it.playable }

        private fun buildGuideState(
            windowStart: Instant,
            windowEnd: Instant,
            episodes: List<BaseItem>,
            movies: List<BaseItem>,
            seriesNetworkNames: Map<UUID, List<String>>,
            avoidedEpisodeIds: Set<UUID> = emptySet(),
        ): LibraryTvGuideState {
            val networkGroups =
                groupEpisodesByNetwork(
                    episodes = episodes,
                    seriesNetworkNames = seriesNetworkNames,
                )
            val networkKeys = networkGroups.map { it.network.key }.toSet()
            val episodeGenreGroups =
                groupEpisodesByGenre(episodes)
                    .filterNot { it.network.key in networkKeys }
            val movieGenreGroups = groupMoviesByGenre(movies)
            val channelPlans =
                (networkGroups + episodeGenreGroups + movieGenreGroups)
                    .asSequence()
                    .mapNotNull { group ->
                        val items =
                            group.items.buildNaturalScheduleCycle(
                                channelKey = group.network.key,
                                windowStart = windowStart,
                                avoidedEpisodeIds = avoidedEpisodeIds,
                            )
                        if (items.isEmpty()) null else LibraryTvChannelPlan(group, items)
                    }.take(LibraryTvMaxChannels)
                    .toList()
            val channels =
                channelPlans
                    .mapIndexed { index, plan ->
                        val group = plan.group
                        val showImageUrls = mutableMapOf<String, String?>()
                        val scheduledItems =
                            plan.items.map {
                                LibraryTvScheduledItem(
                                    item = it,
                                    imageUrl =
                                        showImageUrls.getOrPut(it.seriesKey()) {
                                            imageUrlService.getItemImageUrl(
                                                item = it,
                                                imageType = ImageType.PRIMARY,
                                                fillWidth = 96,
                                                fillHeight = 54,
                                                useSeriesForPrimary = it.type == BaseItemKind.EPISODE,
                                            )
                                        },
                                    durationMs = it.libraryTvDurationMs(),
                                )
                            }
                        LibraryTvChannel(
                            key = group.network.key,
                            id = group.network.id,
                            number = index + 1,
                            name = group.network.name,
                            imageUrl = group.network.imageUrl,
                            programs =
                                buildPrograms(
                                    channelKey = group.network.key,
                                    channelId = group.network.id,
                                    channelName = group.network.name,
                                    channelImageUrl = group.network.imageUrl,
                                    items = scheduledItems,
                                    windowStart = windowStart,
                                    windowEnd = windowEnd,
                                    channelIndex = index,
                                ),
                        )
                    }
            return LibraryTvGuideState(
                channels = channels,
                windowStart = windowStart,
                windowEnd = windowEnd,
            )
        }

        private fun LibraryTvGuideState.publishIfUsable(
            userId: UUID,
        ) {
            if (channels.isNotEmpty()) {
                LibraryTvGuideMemoryCache.put(userId, this)
                _loading.update { DataLoadingState.Success(this) }
            }
        }

    }

@Composable
fun LibraryTvPage(
    preferences: UserPreferences,
    restoreFocusOnDrawerReturn: () -> Boolean = { false },
    modifier: Modifier = Modifier,
    pipPlaybackViewModel: PlaybackViewModel? = null,
    viewModel: LibraryTvViewModel = hiltViewModel(),
) {
    val loading by viewModel.loading.collectAsState()
    val showHeader = preferences.appPreferences.interfacePreferences.liveTvPreferences.showHeader
    val disabledChannelKeys =
        preferences.appPreferences.interfacePreferences.liveTvPreferences.disabledLibraryTvChannelKeysList.toSet()

    when (val state = loading) {
        DataLoadingState.Pending,
        DataLoadingState.Loading,
        -> LoadingPage(modifier)

        is DataLoadingState.Error -> ErrorMessage(state, modifier)
        is DataLoadingState.Success -> {
            val guideState =
                remember(state.data, disabledChannelKeys) {
                    state.data.withEnabledChannels(disabledChannelKeys)
                }
            if (guideState.channels.isEmpty()) {
                ErrorMessage(
                    message = stringResource(R.string.library_tv_empty),
                    exception = null,
                    modifier = modifier,
                )
            } else {
                Box(modifier = modifier) {
                    LibraryTvGuide(
                        state = guideState,
                        showHeader = showHeader,
                        showPictureInPicture = pipPlaybackViewModel != null,
                        onProgramClick = viewModel::play,
                        onProgramFocus = viewModel::rememberFocusedProgram,
                        onChannelFocus = viewModel::rememberFocusedChannel,
                        preferredFocusKey = viewModel.lastFocusedProgramKey,
                        preferredChannelKey = viewModel.lastFocusedChannelKey,
                        restoreFocusOnDrawerReturn = restoreFocusOnDrawerReturn,
                        modifier = Modifier.fillMaxSize(),
                    )
                    LibraryTvPictureInPicture(
                        viewModel = pipPlaybackViewModel,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun LibraryTvPictureInPicture(
    viewModel: PlaybackViewModel?,
    modifier: Modifier = Modifier,
) {
    if (viewModel == null) return
    val loading by viewModel.loading.observeAsState()
    val playerState by viewModel.currentPlayer.collectAsState()
    val info by viewModel.libraryTvPlayback.collectAsState()
    if (loading != LoadingState.Success || playerState == null) return

    BoxWithConstraints(modifier = modifier) {
        val width = minOf(maxWidth * 0.3f, 360.dp).coerceAtLeast(240.dp)
        LibraryTvPictureInPictureSurface(
            playerState = playerState!!,
            info = info,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 20.dp, end = 28.dp)
                    .width(width)
                    .aspectRatio(16f / 9f),
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun LibraryTvPictureInPictureSurface(
    playerState: PlayerState,
    info: LibraryTvPlaybackInfo?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(Color.Black, shape)
                .border(1.dp, Color.White.copy(alpha = 0.28f), shape),
    ) {
        PlayerSurface(
            player = playerState.player,
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            modifier = Modifier.fillMaxSize(),
        )
        info?.let {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.78f),
                                ),
                            ),
                        ).padding(start = 10.dp, end = 10.dp, top = 22.dp, bottom = 8.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = "${it.channelNumber}  ${it.channelName}",
                        color = Color.White.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = it.title,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@HiltViewModel
class LibraryTvChannelSettingsViewModel
    @Inject
    constructor(
        private val preferenceDataStore: DataStore<AppPreferences>,
    ) : ViewModel() {
        fun setChannelEnabled(
            key: String,
            enabled: Boolean,
        ) {
            viewModelScope.launchIO {
                preferenceDataStore.updateData { preferences ->
                    preferences.updateLiveTvPreferences {
                        val disabledKeys = disabledLibraryTvChannelKeysList.toMutableSet()
                        if (enabled) {
                            disabledKeys.remove(key)
                        } else {
                            disabledKeys.add(key)
                        }
                        clearDisabledLibraryTvChannelKeys()
                        addAllDisabledLibraryTvChannelKeys(disabledKeys.sorted())
                    }
                }
            }
        }
    }

@Composable
fun LibraryTvChannelSettingsPage(
    preferences: AppPreferences,
    modifier: Modifier = Modifier,
    viewModel: LibraryTvChannelSettingsViewModel = hiltViewModel(),
) {
    val persistedDisabledChannelKeys =
        preferences.interfacePreferences.liveTvPreferences.disabledLibraryTvChannelKeysList.toSet()
    var disabledChannelKeys by remember { mutableStateOf(persistedDisabledChannelKeys) }
    LaunchedEffect(persistedDisabledChannelKeys) {
        disabledChannelKeys = persistedDisabledChannelKeys
    }
    val channelOptions =
        remember {
            (
                PopularNetworks.mapIndexed { index, network ->
                    network
                        .toLibraryTvNetwork(sortIndex = index)
                        .let { LibraryTvChannelOption(it.key, it.name) }
                } + LibraryTvGuideMemoryCache.channelOptions()
            ).distinctBy { it.key }
                .sortedBy { it.name }
        }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        Text(
            text = stringResource(R.string.library_tv_channels),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (channelOptions.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.library_tv_channels_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(
                items = channelOptions,
                key = { it.key },
            ) { channel ->
                val enabled = channel.key !in disabledChannelKeys
                SwitchPreference(
                    title = channel.name,
                    value = enabled,
                    onClick = {
                        val nextEnabled = !enabled
                        disabledChannelKeys =
                            if (nextEnabled) {
                                disabledChannelKeys - channel.key
                            } else {
                                disabledChannelKeys + channel.key
                            }
                        viewModel.setChannelEnabled(channel.key, nextEnabled)
                    },
                    summary = stringResource(if (enabled) R.string.enabled else R.string.disabled),
                )
            }
        }
    }
}

@Composable
private fun LibraryTvGuide(
    state: LibraryTvGuideState,
    showHeader: Boolean,
    showPictureInPicture: Boolean,
    onProgramClick: (LibraryTvProgram) -> Unit,
    onProgramFocus: (LibraryTvProgram) -> Unit,
    onChannelFocus: (String, LibraryTvProgram?) -> Unit,
    preferredFocusKey: LibraryTvProgramFocusKey?,
    preferredChannelKey: String?,
    restoreFocusOnDrawerReturn: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val now = remember(state) { Instant.now() }
    val initialProgram =
        remember(state, preferredFocusKey) {
            state.programForFocusKey(preferredFocusKey)
                ?: state.channels.firstNotNullOfOrNull { it.programAt(now) }
                ?: state.channels.firstOrNull()?.programs?.firstOrNull()
        }
    var focusedProgram by remember(state, initialProgram) { mutableStateOf(initialProgram) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        if (showHeader) {
            LibraryTvHeader(
                program = focusedProgram,
                reservePictureInPictureSpace = showPictureInPicture,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(176.dp),
            )
        }
        LibraryTvGrid(
            state = state,
            focusProgramKey = initialProgram?.focusKey().takeIf { preferredChannelKey == null },
            focusChannelKey = preferredChannelKey,
            restoreFocusOnDrawerReturn = restoreFocusOnDrawerReturn,
            onProgramFocus = {
                focusedProgram = it
                onProgramFocus(it)
            },
            onChannelFocus = { channel, program ->
                focusedProgram = program
                onChannelFocus(channel.key, program)
            },
            onProgramClick = onProgramClick,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
        )
    }
}

@Composable
private fun LibraryTvHeader(
    program: LibraryTvProgram?,
    reservePictureInPictureSpace: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val bannerAccent =
        lerp(
            colorScheme.surfaceColorAtElevation(4.dp),
            colorScheme.primaryContainer,
            0.42f,
        )
    val logoRequest =
        remember(context, program?.channelImageUrl) {
            program
                ?.channelImageUrl
                ?.let {
                    ImageRequest
                        .Builder(context)
                        .data(it)
                        .crossfade(false)
                        .build()
                }
        }
    val meta =
        program?.let {
            listOfNotNull(
                it.channelName.takeIf { _ -> it.channelImageUrl == null },
                it.item.subtitle?.takeIf { subtitle -> subtitle.isNotBlank() },
                "${it.start.timeText(context)} - ${it.end.timeText(context)}",
            ).joinToString("  |  ")
        } ?: stringResource(R.string.tv_guide)

    Box(
        modifier =
            modifier
                .background(
                    Brush.horizontalGradient(
                        colors =
                            listOf(
                                bannerAccent,
                                colorScheme.surfaceColorAtElevation(2.dp),
                                colorScheme.background,
                            ),
                    ),
                ).padding(start = 28.dp, top = 18.dp, end = 32.dp, bottom = 12.dp),
    ) {
        val textWidthFraction = if (reservePictureInPictureSpace) 0.62f else 0.84f
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(textWidthFraction),
        ) {
            logoRequest?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(width = 104.dp, height = 34.dp),
                )
            }
            Text(
                text = program?.title ?: stringResource(R.string.tv_guide),
                color = colorScheme.onBackground,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = meta,
                color = colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            program?.item?.data?.overview?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    color = colorScheme.onBackground.copy(alpha = .72f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LibraryTvGrid(
    state: LibraryTvGuideState,
    focusProgramKey: LibraryTvProgramFocusKey?,
    focusChannelKey: String?,
    restoreFocusOnDrawerReturn: () -> Boolean,
    onProgramFocus: (LibraryTvProgram) -> Unit,
    onChannelFocus: (LibraryTvChannel, LibraryTvProgram?) -> Unit,
    onProgramClick: (LibraryTvProgram) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val scrollScope = rememberCoroutineScope()
    var rowScrollJob by remember { mutableStateOf<Job?>(null) }
    val programFocusRequesters =
        remember(state.channels) {
            state.channels
                .flatMap { channel -> channel.programs.map { it.focusKey() to FocusRequester() } }
                .toMap()
        }
    val channelFocusRequesters =
        remember(state.channels) {
            state.channels.associate { it.key to FocusRequester() }
        }
    val initialProgramFocusRequester = focusProgramKey?.let(programFocusRequesters::get)
    val initialChannelFocusRequester = focusChannelKey?.let(channelFocusRequesters::get)
    val focusChannelIndex =
        remember(state.channels, focusProgramKey, focusChannelKey) {
            focusChannelKey
                ?.let { key ->
                    state.channels.indexOfFirst { it.key == key }.takeIf { it >= 0 }
                }
                ?: state.channels.indexOfFirst { channel ->
                    channel.programs.any { it.focusKey() == focusProgramKey }
                }.takeIf { it >= 0 }
        }
    val listState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = focusChannelIndex?.coerceAtLeast(0) ?: 0,
        )
    val initialPreferProgramFocus = focusProgramKey != null && focusChannelKey == null
    // keepChannelRowReady scrolls to show one row above/below when focus is near the viewport edge.
    // That fights programmatic scrollToItem on entry and drawer return (scroll then jump up). Hold it
    // until the user presses a key, matching Home's contentScrollSuppressed pattern.
    var allowRowScrollAdjustment by remember { mutableStateOf(false) }
    var liveFocusedProgramKey by remember(focusProgramKey) { mutableStateOf(focusProgramKey) }
    var liveFocusedChannelKey by remember(focusChannelKey) { mutableStateOf(focusChannelKey) }
    var liveFocusedProgramCell by
        remember(focusProgramKey, focusChannelKey) { mutableStateOf(initialPreferProgramFocus) }
    val channelRailWidth = 180.dp
    val timelineGap = 4.dp
    val rowHeight = 54.dp
    suspend fun scrollToChannelIfNeeded(channelIndex: Int) {
        val visibleRows = listState.layoutInfo.visibleItemsInfo
        if (
            visibleRows.isEmpty() ||
                channelIndex !in visibleRows.first().index..visibleRows.last().index
        ) {
            listState.scrollToItem(channelIndex)
        }
    }

    fun keepChannelRowReady(channelIndex: Int) {
        if (!allowRowScrollAdjustment) return
        val visibleRows = listState.layoutInfo.visibleItemsInfo
        if (visibleRows.isEmpty()) return

        val firstVisibleRow = visibleRows.first().index
        val lastVisibleRow = visibleRows.last().index
        val visibleRowCount = (lastVisibleRow - firstVisibleRow + 1).coerceAtLeast(1)
        val targetFirstRow =
            when {
                channelIndex <= firstVisibleRow + 1 -> (channelIndex - 1).coerceAtLeast(0)
                channelIndex >= lastVisibleRow - 1 ->
                    (channelIndex - visibleRowCount + 2)
                        .coerceIn(0, state.channels.lastIndex.coerceAtLeast(0))
                else -> return
            }
        if (targetFirstRow == firstVisibleRow) return

        rowScrollJob?.cancel()
        rowScrollJob =
            scrollScope.launch {
                listState.scrollToItem(targetFirstRow)
            }
    }

    suspend fun restoreFocusedGuideEntry() {
        val targetChannelKey = liveFocusedChannelKey ?: liveFocusedProgramKey?.channelKey
        val targetChannelIndex =
            targetChannelKey
                ?.let { channelKey ->
                    state.channels.indexOfFirst { it.key == channelKey }.takeIf { it >= 0 }
                } ?: focusChannelIndex
        targetChannelIndex?.let { scrollToChannelIfNeeded(it) }

        val targetProgramRequester = liveFocusedProgramKey?.let(programFocusRequesters::get)
        val targetChannelRequester = liveFocusedChannelKey?.let(channelFocusRequesters::get)
        if (liveFocusedProgramCell) {
            targetProgramRequester?.tryRequestFocus("library_tv_drawer_return_program")
                ?: targetChannelRequester?.tryRequestFocus("library_tv_drawer_return_channel")
                ?: initialProgramFocusRequester?.tryRequestFocus("library_tv_guide")
                ?: initialChannelFocusRequester?.tryRequestFocus("library_tv_channel")
        } else {
            targetChannelRequester?.tryRequestFocus("library_tv_drawer_return_channel")
                ?: targetProgramRequester?.tryRequestFocus("library_tv_drawer_return_program")
                ?: initialChannelFocusRequester?.tryRequestFocus("library_tv_channel")
                ?: initialProgramFocusRequester?.tryRequestFocus("library_tv_guide")
        }
    }

    LaunchedEffect(focusChannelIndex, initialChannelFocusRequester, initialProgramFocusRequester) {
        focusChannelIndex?.let { scrollToChannelIfNeeded(it) }
        initialChannelFocusRequester?.tryRequestFocus("library_tv_channel")
            ?: initialProgramFocusRequester?.tryRequestFocus("library_tv_guide")
    }

    LaunchedEffect(Unit) {
        snapshotFlow { restoreFocusOnDrawerReturn() }
            .distinctUntilChanged()
            .collect { returningFromDrawer ->
                if (returningFromDrawer) {
                    restoreFocusedGuideEntry()
                }
            }
    }

    BoxWithConstraints(
        modifier =
            modifier
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        allowRowScrollAdjustment = true
                    }
                    false
                }
                .fillMaxSize()
                .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
    ) {
        val visibleWindowStart =
            remember(state) {
                val now = Instant.now().roundDownToMinutes(LibraryTvTimeBucketMinutes)
                maxOf(
                    now.minusSeconds(LibraryTvNowInsetMinutes * 60),
                    state.windowStart,
                )
            }
        val viewportWidth = (maxWidth - channelRailWidth - timelineGap).coerceAtLeast(640.dp)
        val totalDurationMs = (state.windowEnd.toEpochMilli() - visibleWindowStart.toEpochMilli()).coerceAtLeast(1L)
        val visibleDurationMs = (LibraryTvVisibleHours * 60 * 60 * 1000).toLong()
        val calculatedTimelineWidth = viewportWidth * (totalDurationMs.toFloat() / visibleDurationMs.toFloat())
        val totalTimelineWidth = calculatedTimelineWidth.coerceAtLeast(viewportWidth)
        LaunchedEffect(visibleWindowStart, state.windowEnd, totalTimelineWidth, viewportWidth) {
            scrollState.scrollTo(0)
        }

        Column(modifier = Modifier.fillMaxSize()) {
            LibraryTvTimelineHeader(
                windowStart = visibleWindowStart,
                windowEnd = state.windowEnd,
                channelRailWidth = channelRailWidth,
                timelineGap = timelineGap,
                totalTimelineWidth = totalTimelineWidth,
                scrollState = scrollState,
            )
            Spacer(modifier = Modifier.height(4.dp))
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(
                    items = state.channels,
                    key = { _, channel -> channel.id },
                ) { channelIndex, channel ->
                    LibraryTvChannelRow(
                        channel = channel,
                        previousChannel = state.channels.getOrNull(channelIndex - 1),
                        nextChannel = state.channels.getOrNull(channelIndex + 1),
                        windowStart = visibleWindowStart,
                        windowEnd = state.windowEnd,
                        channelRailWidth = channelRailWidth,
                        timelineGap = timelineGap,
                        viewportWidth = viewportWidth,
                        totalTimelineWidth = totalTimelineWidth,
                        rowHeight = rowHeight,
                        scrollState = scrollState,
                        channelFocusRequester = channelFocusRequesters[channel.key],
                        programFocusRequesters = programFocusRequesters,
                        onChannelFocus = { focusedChannel, program ->
                            liveFocusedChannelKey = focusedChannel.key
                            liveFocusedProgramKey = program?.focusKey()
                            liveFocusedProgramCell = false
                            keepChannelRowReady(channelIndex)
                            onChannelFocus(focusedChannel, program)
                        },
                        onProgramFocus = { program ->
                            liveFocusedProgramKey = program.focusKey()
                            liveFocusedChannelKey = program.channelKey
                            liveFocusedProgramCell = true
                            keepChannelRowReady(channelIndex)
                            onProgramFocus(program)
                        },
                        onProgramClick = onProgramClick,
                        modifier = Modifier.height(rowHeight),
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryTvTimelineHeader(
    windowStart: Instant,
    windowEnd: Instant,
    channelRailWidth: Dp,
    timelineGap: Dp,
    totalTimelineWidth: Dp,
    scrollState: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val totalDurationMs = (windowEnd.toEpochMilli() - windowStart.toEpochMilli()).coerceAtLeast(1L)
    val markers = remember(windowStart, windowEnd) { buildTimelineMarkers(windowStart, windowEnd) }
    val now = Instant.now()
    val nowRatio =
        ((now.toEpochMilli() - windowStart.toEpochMilli()).toFloat() / totalDurationMs.toFloat())
            .coerceIn(0f, 1f)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(32.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = stringResource(R.string.channels),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier =
                Modifier
                    .width(channelRailWidth)
                    .padding(start = 12.dp, bottom = 6.dp),
        )
        Spacer(modifier = Modifier.width(timelineGap))
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(scrollState),
        ) {
            Box(
                modifier =
                    Modifier
                        .width(totalTimelineWidth)
                        .fillMaxHeight(),
            ) {
                markers.forEach { marker ->
                    val ratio =
                        ((marker.toEpochMilli() - windowStart.toEpochMilli()).toFloat() / totalDurationMs.toFloat())
                            .coerceIn(0f, 1f)
                    Text(
                        text = marker.timeText(context),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                        modifier =
                            Modifier
                                .offset(x = totalTimelineWidth * ratio)
                                .padding(bottom = 6.dp),
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .offset(x = totalTimelineWidth * nowRatio)
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.border),
                )
            }
        }
    }
}

@Composable
private fun LibraryTvChannelRow(
    channel: LibraryTvChannel,
    previousChannel: LibraryTvChannel?,
    nextChannel: LibraryTvChannel?,
    windowStart: Instant,
    windowEnd: Instant,
    channelRailWidth: Dp,
    timelineGap: Dp,
    viewportWidth: Dp,
    totalTimelineWidth: Dp,
    rowHeight: Dp,
    scrollState: androidx.compose.foundation.ScrollState,
    channelFocusRequester: FocusRequester?,
    programFocusRequesters: Map<LibraryTvProgramFocusKey, FocusRequester>,
    onChannelFocus: (LibraryTvChannel, LibraryTvProgram?) -> Unit,
    onProgramFocus: (LibraryTvProgram) -> Unit,
    onProgramClick: (LibraryTvProgram) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LibraryTvChannelCell(
            channel = channel,
            onFocus = {
                val program = channel.programAt(Instant.now()) ?: channel.programs.firstOrNull()
                onChannelFocus(channel, program)
            },
            onClick = {
                channel.programAt(Instant.now())?.let(onProgramClick)
            },
            focusRequester = channelFocusRequester,
            modifier =
                Modifier
                    .width(channelRailWidth)
                    .fillMaxHeight(),
        )
        Spacer(modifier = Modifier.width(timelineGap))
        Box(
            modifier =
                Modifier
                    .width(viewportWidth)
                    .fillMaxHeight()
                    .horizontalScroll(scrollState),
        ) {
            Box(
                modifier =
                    Modifier
                        .width(totalTimelineWidth)
                        .fillMaxHeight(),
            ) {
                val visiblePrograms = channel.visiblePrograms(windowStart, windowEnd)
                val firstProgramFocusRequester =
                    visiblePrograms
                        .firstOrNull()
                        ?.focusKey()
                        ?.let(programFocusRequesters::get)
                visiblePrograms.forEachIndexed { programIndex, program ->
                    val focusInstant = program.verticalFocusInstant()
                    val nextProgramFocusRequester =
                        visiblePrograms
                            .getOrNull(programIndex + 1)
                            ?.focusKey()
                            ?.let(programFocusRequesters::get)
                    val rightFocusRequester =
                        nextProgramFocusRequester
                            ?: firstProgramFocusRequester.takeIf { visiblePrograms.size > 1 }
                    LibraryTvProgramCell(
                        program = program,
                        windowStart = windowStart,
                        windowEnd = windowEnd,
                        totalTimelineWidth = totalTimelineWidth,
                        rowHeight = rowHeight,
                        viewportWidth = viewportWidth,
                        scrollState = scrollState,
                        focusRequester = programFocusRequesters[program.focusKey()],
                        upFocusRequester =
                            previousChannel
                                ?.programNear(focusInstant, windowStart, windowEnd)
                                ?.focusKey()
                                ?.let(programFocusRequesters::get),
                        downFocusRequester =
                            nextChannel
                                ?.programNear(focusInstant, windowStart, windowEnd)
                                ?.focusKey()
                                ?.let(programFocusRequesters::get),
                        rightFocusRequester = rightFocusRequester,
                        onFocus = { onProgramFocus(program) },
                        onClick = {
                            channel.programAt(Instant.now())?.let(onProgramClick)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryTvChannelCell(
    channel: LibraryTvChannel,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme
    val channelShape = RoundedCornerShape(8.dp)
    val backgroundColor = colorScheme.surfaceColorAtElevation(1.dp)
    val textColor = colorScheme.contentColorFor(backgroundColor)
    val borderColor = colorScheme.onSurface.copy(alpha = .08f)
    val focusBorderColor by animateColorAsState(
        targetValue = if (focused) colorScheme.border else borderColor,
        animationSpec = tween(durationMillis = 180),
        label = "libraryTvChannelBorder",
    )
    val focusShadowElevation by animateDpAsState(
        targetValue = if (focused) 12.dp else 0.dp,
        animationSpec = tween(durationMillis = 180),
        label = "libraryTvChannelShadow",
    )
    val channelImage =
        remember(context, channel.imageUrl) {
            channel.imageUrl?.let {
                ImageRequest
                    .Builder(context)
                    .data(it)
                    .crossfade(false)
                    .build()
            }
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            modifier
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .shadow(
                    elevation = focusShadowElevation,
                    shape = channelShape,
                    ambientColor = colorScheme.border.copy(alpha = 0.45f),
                    spotColor = colorScheme.border.copy(alpha = 0.75f),
                )
                .clip(channelShape)
                .background(backgroundColor)
                .border(2.dp, focusBorderColor, channelShape)
                .onPreviewKeyEvent(::isStaleLibraryTvVerticalRepeat)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocus()
                }.clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(colorScheme.border.copy(alpha = .18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = channel.number.toString().padStart(2, '0'),
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        channelImage?.let {
            SubcomposeAsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                loading = {
                    LibraryTvChannelName(
                        name = channel.name,
                        textColor = textColor,
                    )
                },
                error = {
                    LibraryTvChannelName(
                        name = channel.name,
                        textColor = textColor,
                    )
                },
                modifier =
                    Modifier
                        .weight(1f)
                        .height(30.dp)
                        .padding(end = 6.dp),
            )
        } ?: run {
            LibraryTvChannelName(
                name = channel.name,
                textColor = textColor,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LibraryTvChannelName(
    name: String,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = name,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LibraryTvProgramCell(
    program: LibraryTvProgram,
    windowStart: Instant,
    windowEnd: Instant,
    totalTimelineWidth: Dp,
    rowHeight: Dp,
    viewportWidth: Dp,
    scrollState: androidx.compose.foundation.ScrollState,
    focusRequester: FocusRequester?,
    upFocusRequester: FocusRequester?,
    downFocusRequester: FocusRequester?,
    rightFocusRequester: FocusRequester?,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val windowStartMs = windowStart.toEpochMilli()
    val windowEndMs = windowEnd.toEpochMilli()
    val totalDurationMs = (windowEndMs - windowStartMs).coerceAtLeast(1L)
    val clippedStart = max(program.start.toEpochMilli(), windowStartMs)
    val clippedEnd = min(program.end.toEpochMilli(), windowEndMs)
    val startRatio = ((clippedStart - windowStartMs).toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    val widthRatio = ((clippedEnd - clippedStart).toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    val cellStart = totalTimelineWidth * startRatio
    val cellWidth = (totalTimelineWidth * widthRatio).coerceAtLeast(1.dp)
    val showImage = cellWidth >= 42.dp
    val showText = cellWidth >= 72.dp
    val currentlyPlaying = program.contains(Instant.now())
    val context = LocalContext.current
    val density = LocalDensity.current
    val loadImage =
        if (showImage) {
            val preloadBufferPx = with(density) { 180.dp.toPx() }
            val cellStartPx = with(density) { cellStart.toPx() }
            val cellEndPx = with(density) { (cellStart + cellWidth).toPx() }
            val viewportEndPx = scrollState.value + with(density) { viewportWidth.toPx() }
            cellEndPx >= scrollState.value - preloadBufferPx &&
                cellStartPx <= viewportEndPx + preloadBufferPx
        } else {
            false
        }
    val programImage =
        remember(context, program.imageUrl, loadImage) {
            program.imageUrl?.takeIf { loadImage }?.let {
                ImageRequest
                    .Builder(context)
                    .data(it)
                    .crossfade(false)
                    .build()
            }
        }
    var focused by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme
    val backgroundColor =
        when {
            focused -> colorScheme.inverseSurface
            currentlyPlaying -> colorScheme.border.copy(alpha = .24f)
            else -> colorScheme.surfaceColorAtElevation(1.dp)
        }
    val contentColor =
        when {
            focused -> colorScheme.inverseOnSurface
            currentlyPlaying -> Color.White
            else -> colorScheme.contentColorFor(backgroundColor)
        }
    val borderColor =
        if (focused) {
            colorScheme.inverseOnSurface.copy(alpha = .65f)
        } else {
            colorScheme.onSurface.copy(alpha = .10f)
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .offset(x = cellStart)
                .width(cellWidth)
                .height(rowHeight)
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .focusProperties {
                    up = upFocusRequester ?: FocusRequester.Default
                    down = downFocusRequester ?: FocusRequester.Default
                    right = rightFocusRequester ?: FocusRequester.Default
                }
                .onPreviewKeyEvent(::isStaleLibraryTvVerticalRepeat)
                .padding(horizontal = 2.dp, vertical = 3.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(backgroundColor)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(8.dp),
                ).onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocus()
                }.clickable(onClick = onClick),
    ) {
        if (showImage) programImage?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .width(54.dp),
            )
        }
        if (showText) {
            Column(
                verticalArrangement = Arrangement.Center,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(start = 10.dp, end = 10.dp),
            ) {
                Text(
                    text = program.title,
                    color = contentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = program.item.subtitle ?: "",
                    color = contentColor.copy(alpha = .68f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun groupEpisodesByNetwork(
    episodes: List<BaseItem>,
    seriesNetworkNames: Map<UUID, List<String>>,
): List<LibraryTvNetworkGroup> {
    val matcher = LibraryTvNetworkMatcher()
    val groups = linkedMapOf<String, LibraryTvNetworkGroup>()
    episodes.forEach { episode ->
        val network =
            matcher.networkFor(
                episode.networkNames() +
                    episode.data.seriesId
                        ?.let { seriesNetworkNames[it] }
                        .orEmpty(),
            ) ?: return@forEach
        groups
            .getOrPut(network.key) { LibraryTvNetworkGroup(network) }
            .add(episode)
    }
    return groups
        .values
        .filter { it.items.isNotEmpty() }
        .sortedWith(
            compareBy<LibraryTvNetworkGroup> { it.network.sortIndex }
                .thenBy { it.network.name },
        )
}

private fun groupEpisodesByGenre(episodes: List<BaseItem>): List<LibraryTvNetworkGroup> {
    if (episodes.isEmpty()) return emptyList()

    val groups = linkedMapOf<String, LibraryTvNetworkGroup>()
    episodes.forEach { episode ->
        val genreKeys = episode.data.genres.orEmpty().map { it.normalizedNetworkKey() }.toSet()
        if (genreKeys.isEmpty()) return@forEach

        LibraryTvEpisodeGenreChannels.forEachIndexed { index, genre ->
            if (genre.aliasKeys.any { it in genreKeys }) {
                val channel = genre.toLibraryTvNetwork(index)
                groups
                    .getOrPut(channel.key) { LibraryTvNetworkGroup(channel) }
                    .add(episode)
            }
        }
    }

    return groups
        .values
        .filter { it.items.size >= LibraryTvMinEpisodesPerGenreChannel }
        .sortedWith(
            compareBy<LibraryTvNetworkGroup> { it.network.sortIndex }
                .thenBy { it.network.name },
        )
}

private fun groupMoviesByGenre(movies: List<BaseItem>): List<LibraryTvNetworkGroup> {
    if (movies.isEmpty()) return emptyList()

    val groups = linkedMapOf<String, LibraryTvNetworkGroup>()
    movies.forEach { movie ->
        val genreKeys = movie.data.genres.orEmpty().map { it.normalizedNetworkKey() }.toSet()
        if (genreKeys.isEmpty()) return@forEach

        LibraryTvMovieGenreChannels.forEachIndexed { index, genre ->
            if (genre.aliasKeys.any { it in genreKeys }) {
                val channel = genre.toLibraryTvNetwork(index)
                groups
                    .getOrPut(channel.key) { LibraryTvNetworkGroup(channel) }
                    .add(movie)
            }
        }
    }

    return groups
        .values
        .filter { it.items.size >= LibraryTvMinMoviesPerGenreChannel }
        .sortedWith(
            compareBy<LibraryTvNetworkGroup> { it.network.sortIndex }
                .thenBy { it.network.name },
        )
}

private data class LibraryTvEpisodeGenreChannel(
    val key: String,
    val name: String,
    val aliases: List<String> = listOf(name),
) {
    val aliasKeys: Set<String> = aliases.map { it.normalizedNetworkKey() }.toSet()
}

private val LibraryTvEpisodeGenreChannels =
    listOf(
        LibraryTvEpisodeGenreChannel("anime", "Anime", listOf("Anime", "Japanimation")),
    )

private fun LibraryTvEpisodeGenreChannel.toLibraryTvNetwork(index: Int): LibraryTvNetwork =
    LibraryTvNetwork(
        key = key,
        id = UUID.nameUUIDFromBytes("library-tv-episode-genre:$key".toByteArray()),
        name = name,
        aliases = aliases,
        imageUrl = networkLogoAsset("$key.svg"),
        sortIndex = PopularNetworks.size + 400 + index,
    )

private data class LibraryTvMovieGenreChannel(
    val key: String,
    val name: String,
    val aliases: List<String> = listOf(name),
) {
    val aliasKeys: Set<String> = aliases.map { it.normalizedNetworkKey() }.toSet()
}

private val LibraryTvMovieGenreChannels =
    listOf(
        LibraryTvMovieGenreChannel("movieaction", "Action Movies", listOf("Action", "Action & Adventure")),
        LibraryTvMovieGenreChannel("movieadventure", "Adventure Movies", listOf("Adventure", "Action & Adventure")),
        LibraryTvMovieGenreChannel("movieanimation", "Animation Movies", listOf("Animation", "Animated")),
        LibraryTvMovieGenreChannel("moviecomedy", "Comedy Movies", listOf("Comedy")),
        LibraryTvMovieGenreChannel("moviecrime", "Crime Movies", listOf("Crime")),
        LibraryTvMovieGenreChannel("moviedocumentary", "Documentary Movies", listOf("Documentary", "Documentaries")),
        LibraryTvMovieGenreChannel("moviedrama", "Drama Movies", listOf("Drama")),
        LibraryTvMovieGenreChannel("moviefamily", "Family Movies", listOf("Family", "Kids")),
        LibraryTvMovieGenreChannel("moviefantasy", "Fantasy Movies", listOf("Fantasy", "Sci-Fi & Fantasy")),
        LibraryTvMovieGenreChannel("moviehorror", "Horror Movies", listOf("Horror")),
        LibraryTvMovieGenreChannel("moviemystery", "Mystery Movies", listOf("Mystery")),
        LibraryTvMovieGenreChannel("movieromance", "Romance Movies", listOf("Romance")),
        LibraryTvMovieGenreChannel(
            "moviescifi",
            "Sci-Fi Movies",
            listOf("Sci-Fi", "Science Fiction", "Sci Fi", "Sci-Fi & Fantasy", "Science Fiction & Fantasy"),
        ),
        LibraryTvMovieGenreChannel("moviethriller", "Thriller Movies", listOf("Thriller", "Suspense")),
        LibraryTvMovieGenreChannel("moviewar", "War Movies", listOf("War", "War & Politics")),
        LibraryTvMovieGenreChannel("moviewestern", "Western Movies", listOf("Western")),
    )

private fun LibraryTvMovieGenreChannel.toLibraryTvNetwork(index: Int): LibraryTvNetwork =
    LibraryTvNetwork(
        key = key,
        id = UUID.nameUUIDFromBytes("library-tv-movie-genre:$key".toByteArray()),
        name = name,
        aliases = aliases,
        imageUrl = networkLogoAsset("$key.svg"),
        sortIndex = PopularNetworks.size + 500 + index,
    )

private class LibraryTvNetworkMatcher {
    private val knownNetworks =
        PopularNetworks.mapIndexed { index, network ->
            network.toLibraryTvNetwork(sortIndex = index)
        }
    private val knownByAlias =
        knownNetworks
            .flatMap { network ->
                network.aliases.map { alias -> alias.normalizedNetworkKey() to network }
            }.toMap()

    fun networkFor(names: List<String>): LibraryTvNetwork? {
        names.forEach { name ->
            knownByAlias[name.normalizedNetworkKey()]?.let { return it }
        }
        val fallbackName = names.firstOrNull { it.isNotBlank() } ?: return null
        return fallbackName.toFallbackNetwork()
    }
}

private class LibraryTvNetworkGroup(
    val network: LibraryTvNetwork,
    val items: MutableList<BaseItem> = mutableListOf(),
) {
    private val episodeIds = mutableSetOf<UUID>()

    fun add(episode: BaseItem) {
        if (episodeIds.add(episode.id)) {
            items.add(episode)
        }
    }
}

private data class LibraryTvChannelPlan(
    val group: LibraryTvNetworkGroup,
    val items: List<BaseItem>,
)

private data class LibraryTvNetwork(
    val key: String,
    val id: UUID,
    val name: String,
    val aliases: List<String>,
    val imageUrl: String?,
    val sortIndex: Int,
)

private fun Network.toLibraryTvNetwork(sortIndex: Int) =
    LibraryTvNetwork(
        key = name.normalizedNetworkKey(),
        id = id,
        name = name,
        aliases = aliases,
        imageUrl = imageUrl,
        sortIndex = sortIndex,
    )

private fun String.toFallbackNetwork(): LibraryTvNetwork {
    val key = normalizedNetworkKey()
    return LibraryTvNetwork(
        key = key,
        id = UUID.nameUUIDFromBytes("library-tv-network:$key".toByteArray()),
        name = trim(),
        aliases = listOf(trim()),
        imageUrl = networkLogoAssetForKey(key),
        sortIndex = PopularNetworks.size,
    )
}

private fun BaseItem.networkNames(): List<String> =
    buildList {
        data.seriesStudio
            ?.splitNetworkNames()
            ?.let(::addAll)
        data.studios
            ?.mapNotNull { it.name }
            ?.flatMap { it.splitNetworkNames() }
            ?.let(::addAll)
    }.distinctBy { it.normalizedNetworkKey() }

internal fun List<BaseItem>.buildNaturalScheduleCycle(
    channelKey: String,
    windowStart: Instant,
    avoidedEpisodeIds: Set<UUID> = emptySet(),
): List<BaseItem> {
    val scheduleItems = preferredLibraryTvItems(avoidedEpisodeIds)
    val scheduleDay = windowStart.atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
    val orderedShowQueues =
        scheduleItems.groupBy { it.seriesKey() }
            .mapNotNull { (seriesKey, seriesItems) ->
                val sortedEpisodes =
                    seriesItems
                        .distinctBy { it.id }
                        .sortedWith(
                            compareBy<BaseItem> { it.data.parentIndexNumber ?: Int.MAX_VALUE }
                                .thenBy { it.data.indexNumber ?: Int.MAX_VALUE }
                                .thenBy { it.sortName },
                        )
                val episodes =
                    sortedEpisodes
                        .rotatedBy(stableIndex("$channelKey:$seriesKey:$scheduleDay", sortedEpisodes.size))
                        .take(LibraryTvMaxEpisodesPerShowInCycle)
                if (episodes.isEmpty()) {
                    null
                } else {
                    val averageDurationMs =
                        episodes
                            .map { it.libraryTvDurationMs() }
                            .average()
                            .let { if (it.isNaN()) LibraryTvDefaultDurationMs.toDouble() else it }
                    LibraryTvShowQueue(
                        seriesKey = seriesKey,
                        title = episodes.first().title ?: episodes.first().name ?: "",
                        episodes = ArrayDeque(episodes),
                        blockSize =
                            if (averageDurationMs <= LibraryTvShortEpisodeThresholdMs) {
                                LibraryTvMaxShortEpisodeBlock
                            } else {
                                1
                            },
                    )
                }
            }.sortedWith(
                compareBy<LibraryTvShowQueue> {
                    stableSortKey("$channelKey:${it.seriesKey}:$scheduleDay")
                }.thenBy { it.title },
            )
    val showQueues =
        orderedShowQueues
            .rotatedBy(stableIndex("$channelKey:$scheduleDay:shows", orderedShowQueues.size))
            .toMutableList()

    if (showQueues.size <= 1) {
        return showQueues.firstOrNull()?.episodes?.toList().orEmpty()
    }

    val result = mutableListOf<BaseItem>()
    var cursor = 0
    var previousSeriesKey: String? = null

    while (showQueues.any { it.episodes.isNotEmpty() }) {
        val nextIndex =
            showQueues.indices.firstOrNull { offset ->
                val index = (cursor + offset) % showQueues.size
                val queue = showQueues[index]
                queue.episodes.isNotEmpty() && queue.seriesKey != previousSeriesKey
            }?.let { offset -> (cursor + offset) % showQueues.size }
                ?: showQueues.indexOfFirst { it.episodes.isNotEmpty() }
                    .takeIf { it >= 0 }
                ?: break
        val queue = showQueues[nextIndex]
        val blockSize = queue.blockSize.coerceIn(1, LibraryTvMaxShortEpisodeBlock).coerceAtMost(queue.episodes.size)
        repeat(blockSize) {
            result.add(queue.episodes.removeFirst())
        }
        previousSeriesKey = queue.seriesKey
        cursor = (nextIndex + 1) % showQueues.size
    }
    return result
}

private fun List<BaseItem>.preferredLibraryTvItems(avoidedEpisodeIds: Set<UUID>): List<BaseItem> {
    if (avoidedEpisodeIds.isEmpty()) return this
    val preferred =
        filterNot {
            it.type == BaseItemKind.EPISODE && it.id in avoidedEpisodeIds
        }
    return preferred.takeIf { it.size > 1 } ?: this
}

private data class LibraryTvSupplementalSeriesCandidate(
    val seriesId: UUID,
    val networkKey: String,
    val sortName: String,
    val priority: Int,
)

internal fun supplementalLibraryTvSeriesIds(
    series: List<BaseItem>,
    existingEpisodes: List<BaseItem>,
    seriesNetworkNames: Map<UUID, List<String>>,
    windowStart: Instant,
    maxCandidates: Int,
): List<UUID> {
    val matcher = LibraryTvNetworkMatcher()
    val scheduleDay = windowStart.atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
    val existingEpisodesBySeries =
        existingEpisodes
            .mapNotNull { episode -> episode.data.seriesId?.let { it to episode } }
            .groupBy({ it.first }, { it.second })
    val existingSeriesIdsByNetwork =
        existingEpisodesBySeries
            .mapNotNull { (seriesId, episodes) ->
                val network =
                    matcher.networkFor(
                        episodes.flatMap { it.networkNames() } +
                            seriesNetworkNames[seriesId].orEmpty(),
                    ) ?: return@mapNotNull null
                network.key to seriesId
            }.groupBy({ it.first }, { it.second })
            .mapValues { (_, ids) -> ids.toSet() }

    val underrepresentedExistingSeries =
        existingEpisodesBySeries
            .mapNotNull { (seriesId, episodes) ->
                if (episodes.size >= LibraryTvMinEpisodesPerShowInCycle) {
                    return@mapNotNull null
                }
                val network =
                    matcher.networkFor(
                        episodes.flatMap { it.networkNames() } +
                            seriesNetworkNames[seriesId].orEmpty(),
                    ) ?: return@mapNotNull null
                LibraryTvSupplementalSeriesCandidate(
                    seriesId = seriesId,
                    networkKey = network.key,
                    sortName = episodes.firstOrNull()?.sortName.orEmpty(),
                    priority = 0,
                )
            }

    val newSeries =
        series
            .mapNotNull { seriesItem ->
                val network =
                    matcher.networkFor(
                        seriesItem.networkNames() +
                            seriesNetworkNames[seriesItem.id].orEmpty(),
                    ) ?: return@mapNotNull null
                network to seriesItem
            }.groupBy({ it.first }, { it.second })
            .toList()
            .sortedWith(
                compareBy<Pair<LibraryTvNetwork, List<BaseItem>>> { it.first.sortIndex }
                    .thenBy { it.first.name },
            ).flatMap { (network, networkSeries) ->
                val existingSeriesIds = existingSeriesIdsByNetwork[network.key].orEmpty()
                val needed = (LibraryTvTargetShowsPerChannel - existingSeriesIds.size).coerceAtLeast(0)
                if (needed == 0) {
                    emptyList()
                } else {
                    networkSeries
                        .filterNot { it.id in existingSeriesIds }
                        .sortedWith(
                            compareBy<BaseItem> {
                                stableSortKey("${network.key}:${it.id}:$scheduleDay")
                            }.thenBy { it.sortName },
                        ).take(needed)
                        .map { seriesItem ->
                            LibraryTvSupplementalSeriesCandidate(
                                seriesId = seriesItem.id,
                                networkKey = network.key,
                                sortName = seriesItem.sortName,
                                priority = 1,
                            )
                        }
                }
            }

    return (underrepresentedExistingSeries + newSeries)
        .distinctBy { it.seriesId }
        .sortedWith(
            compareBy<LibraryTvSupplementalSeriesCandidate> { it.priority }
                .thenBy { stableSortKey("${it.networkKey}:${it.seriesId}:$scheduleDay") }
                .thenBy { it.sortName },
        ).take(maxCandidates)
        .map { it.seriesId }
}

private data class LibraryTvShowQueue(
    val seriesKey: String,
    val title: String,
    val episodes: ArrayDeque<BaseItem>,
    val blockSize: Int,
)

private fun BaseItem.libraryTvDurationMs(): Long =
    (
        data.runTimeTicks
            ?.ticks
            ?.inWholeMilliseconds
            ?.takeIf { it > 0 }
            ?: LibraryTvDefaultDurationMs
    ).coerceAtLeast(LibraryTvMinimumDurationMs)

private fun stableIndex(
    value: String,
    size: Int,
): Int = if (size <= 0) 0 else Math.floorMod(value.hashCode(), size)

private fun stableSortKey(value: String): Int = value.hashCode()

private fun maxInstant(
    first: Instant,
    second: Instant,
): Instant = if (first.isAfter(second)) first else second

private fun <T> List<T>.rotatedBy(offset: Int): List<T> {
    if (isEmpty()) return this
    val safeOffset = Math.floorMod(offset, size)
    if (safeOffset == 0) return this
    return drop(safeOffset) + take(safeOffset)
}

private fun BaseItem.seriesKey(): String =
    data.seriesId?.toString()
        ?: title
            ?.normalizedNetworkKey()
            ?.takeIf { it.isNotBlank() }
        ?: id.toString()

private fun List<BaseItem>.inferredSeriesNetworkNames(): Map<UUID, List<String>> =
    mapNotNull { item ->
        item.data.seriesId?.let { seriesId -> seriesId to item.networkNames() }
    }.filter { (_, names) -> names.isNotEmpty() }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, names) -> names.flatten().distinctBy { it.normalizedNetworkKey() } }

private fun mergeSeriesNetworkNames(vararg maps: Map<UUID, List<String>>): Map<UUID, List<String>> =
    maps
        .flatMap { it.entries }
        .groupBy({ it.key }, { it.value })
        .mapValues { (_, names) -> names.flatten().distinctBy { it.normalizedNetworkKey() } }

private fun String.splitNetworkNames(): List<String> =
    split(",", "/", ";")
        .map { it.trim() }
        .filter { it.isNotBlank() }

private fun String.normalizedNetworkKey(): String =
    trim()
        .lowercase()
        .replace("&", "and")
        .replace("+", "plus")
        .replace(Regex("[^a-z0-9]+"), "")

data class LibraryTvGuideState(
    val channels: List<LibraryTvChannel>,
    val windowStart: Instant,
    val windowEnd: Instant,
)

data class LibraryTvChannel(
    val key: String,
    val id: UUID,
    val number: Int,
    val name: String,
    val imageUrl: String?,
    val programs: List<LibraryTvProgram>,
) {
    fun programAt(now: Instant): LibraryTvProgram? = programs.firstOrNull { it.contains(now) }
}

private fun LibraryTvChannel.visiblePrograms(
    windowStart: Instant,
    windowEnd: Instant,
): List<LibraryTvProgram> =
    programs.filter { it.end.isAfter(windowStart) && it.start.isBefore(windowEnd) }

private fun LibraryTvChannel.programNear(
    instant: Instant,
    windowStart: Instant,
    windowEnd: Instant,
): LibraryTvProgram? {
    val visiblePrograms = visiblePrograms(windowStart, windowEnd)
    return visiblePrograms.firstOrNull { it.contains(instant) }
        ?: visiblePrograms.minByOrNull { it.distanceTo(instant) }
}

data class LibraryTvChannelOption(
    val key: String,
    val name: String,
)

fun LibraryTvGuideState.withEnabledChannels(disabledChannelKeys: Set<String>): LibraryTvGuideState =
    copy(
        channels =
            channels
                .filterNot { it.key in disabledChannelKeys }
                .mapIndexed { index, channel -> channel.copy(number = index + 1) },
    )

private fun LibraryTvGuideState.scheduledEpisodeIds(): Set<UUID> =
    channels
        .asSequence()
        .flatMap { channel -> channel.programs.asSequence() }
        .map { it.item }
        .filter { it.type == BaseItemKind.EPISODE }
        .map { it.id }
        .toSet()

internal fun LibraryTvGuideState.append(
    extension: LibraryTvGuideState,
    pruneBefore: Instant,
): LibraryTvGuideState {
    val extensionByKey = extension.channels.associateBy { it.key }
    val mergedChannels =
        (
            channels.mapNotNull { channel ->
                val retainedPrograms =
                    channel.programs
                        .filter { it.end.isAfter(pruneBefore) }
                val lastEnd = retainedPrograms.maxOfOrNull { it.end }
                val appendedPrograms =
                    extensionByKey[channel.key]
                        ?.programs
                        .orEmpty()
                        .mapNotNull { program ->
                            when {
                                !program.end.isAfter(pruneBefore) -> null
                                lastEnd == null -> program
                                !program.end.isAfter(lastEnd) -> null
                                program.start.isBefore(lastEnd) -> program.copy(start = lastEnd)
                                else -> program
                            }
                        }
                val programs =
                    (retainedPrograms + appendedPrograms)
                        .distinctBy { it.focusKey() }
                channel
                    .copy(programs = programs)
                    .takeIf { it.programs.isNotEmpty() }
            } +
                extension.channels
                    .filterNot { extensionChannel ->
                        channels.any { it.key == extensionChannel.key }
                    }.mapNotNull { extensionChannel ->
                        extensionChannel
                            .copy(
                                programs =
                                    extensionChannel.programs
                                        .filter { it.end.isAfter(pruneBefore) },
                            ).takeIf { it.programs.isNotEmpty() }
                    }
        ).mapIndexed { index, channel -> channel.copy(number = index + 1) }

    return copy(
        channels = mergedChannels,
        windowStart = maxInstant(windowStart, pruneBefore),
        windowEnd = maxInstant(windowEnd, extension.windowEnd),
    )
}

data class LibraryTvProgram(
    val channelKey: String,
    val channelId: UUID,
    val channelName: String,
    val channelImageUrl: String?,
    val item: BaseItem,
    val imageUrl: String?,
    val start: Instant,
    val end: Instant,
) {
    val title: String get() = item.title ?: item.name ?: "Untitled"

    fun contains(now: Instant): Boolean = !now.isBefore(start) && now.isBefore(end)
}

data class LibraryTvProgramFocusKey(
    val channelKey: String,
    val itemId: UUID,
    val start: Instant,
    val end: Instant,
)

private fun LibraryTvProgram.focusKey(): LibraryTvProgramFocusKey =
    LibraryTvProgramFocusKey(
        channelKey = channelKey,
        itemId = item.id,
        start = start,
        end = end,
    )

private fun LibraryTvProgram.verticalFocusInstant(): Instant {
    val startMs = start.toEpochMilli()
    val durationMs = (end.toEpochMilli() - startMs).coerceAtLeast(0L)
    return Instant.ofEpochMilli(startMs + durationMs / 2)
}

private fun LibraryTvProgram.distanceTo(instant: Instant): Long {
    if (contains(instant)) return 0L
    val instantMs = instant.toEpochMilli()
    return min(
        abs(start.toEpochMilli() - instantMs),
        abs(end.toEpochMilli() - instantMs),
    )
}

private fun isStaleLibraryTvVerticalRepeat(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (event.key != Key.DirectionUp && event.key != Key.DirectionDown) return false
    val nativeEvent = event.nativeKeyEvent
    return nativeEvent.repeatCount > 0 &&
        SystemClock.uptimeMillis() - nativeEvent.eventTime > LibraryTvStaleRepeatInputMs
}

private fun LibraryTvGuideState.programForFocusKey(key: LibraryTvProgramFocusKey?): LibraryTvProgram? =
    key?.let {
        channels
            .firstOrNull { channel -> channel.key == it.channelKey }
            ?.programs
            ?.firstOrNull { program -> program.focusKey() == key }
    }

private data class LibraryTvScheduledItem(
    val item: BaseItem,
    val imageUrl: String?,
    val durationMs: Long,
)

private fun buildPrograms(
    channelKey: String,
    channelId: UUID,
    channelName: String,
    channelImageUrl: String?,
    items: List<LibraryTvScheduledItem>,
    windowStart: Instant,
    windowEnd: Instant,
    channelIndex: Int,
): List<LibraryTvProgram> {
    if (items.isEmpty()) return emptyList()

    val windowStartMs = windowStart.toEpochMilli()
    val windowEndMs = windowEnd.toEpochMilli()
    val cycleDurationMs = items.sumOf { it.durationMs }.coerceAtLeast(1L)
    val anchorMs = LibraryTvScheduleAnchor.toEpochMilli() + (channelIndex * LibraryTvChannelOffsetMs)
    val windowOffsetMs = Math.floorMod(windowStartMs - anchorMs, cycleDurationMs)
    var itemIndex = 0
    var elapsedInCycleMs = 0L

    while (itemIndex < items.lastIndex && elapsedInCycleMs + items[itemIndex].durationMs <= windowOffsetMs) {
        elapsedInCycleMs += items[itemIndex].durationMs
        itemIndex++
    }

    var cursorMs = windowStartMs - (windowOffsetMs - elapsedInCycleMs)
    val programs = mutableListOf<LibraryTvProgram>()
    while (cursorMs < windowEndMs) {
        val scheduledItem = items[itemIndex]
        val programEndMs = cursorMs + scheduledItem.durationMs
        if (programEndMs > windowStartMs) {
            programs.add(
                LibraryTvProgram(
                    channelKey = channelKey,
                    channelId = channelId,
                    channelName = channelName,
                    channelImageUrl = channelImageUrl,
                    item = scheduledItem.item,
                    imageUrl = scheduledItem.imageUrl,
                    start = Instant.ofEpochMilli(cursorMs),
                    end = Instant.ofEpochMilli(programEndMs),
                ),
            )
        }
        cursorMs = programEndMs
        itemIndex = (itemIndex + 1) % items.size
    }
    return programs
}

private fun Instant.roundDownToMinutes(intervalMinutes: Long): Instant {
    val zoned = atZone(ZoneId.systemDefault())
    val minute = (zoned.minute / intervalMinutes * intervalMinutes).toInt()
    return zoned
        .withMinute(minute)
        .withSecond(0)
        .withNano(0)
        .toInstant()
}

private fun buildTimelineMarkers(
    windowStart: Instant,
    windowEnd: Instant,
): List<Instant> =
    buildList {
        var marker = windowStart
        while (!marker.isAfter(windowEnd)) {
            add(marker)
            marker = marker.plusSeconds(30 * 60)
        }
    }

private fun Instant.timeText(context: android.content.Context): String =
    DateUtils.formatDateTime(
        context,
        toEpochMilli(),
        DateUtils.FORMAT_SHOW_TIME,
    )
