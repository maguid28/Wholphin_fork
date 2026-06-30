package com.github.damontecres.wholphin.services

import android.content.Context
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeCategory
import com.github.damontecres.wholphin.data.model.HomePageSettings
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.data.model.SUPPORTED_HOME_PAGE_SETTINGS_VERSION
import com.github.damontecres.wholphin.data.model.SeasonalCategory
import com.github.damontecres.wholphin.data.model.selectRotatingGenre
import com.github.damontecres.wholphin.data.model.createGenreDestination
import com.github.damontecres.wholphin.data.model.createStudioDestination
import com.github.damontecres.wholphin.preferences.DefaultUserConfiguration
import com.github.damontecres.wholphin.preferences.HomePagePreferences
import com.github.damontecres.wholphin.ui.HOME_PAGE_ROW_SIZE
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.components.getGenreImageMap
import com.github.damontecres.wholphin.ui.main.settings.Library
import com.github.damontecres.wholphin.ui.main.settings.favoriteOptions
import com.github.damontecres.wholphin.ui.playback.getTypeFor
import com.github.damontecres.wholphin.ui.toBaseItems
import com.github.damontecres.wholphin.ui.toServerString
import com.github.damontecres.wholphin.util.GetGenresRequestHandler
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.GetPersonsHandler
import com.github.damontecres.wholphin.util.GetStudiosRequestHandler
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.HomeRowLoadingState.Error
import com.github.damontecres.wholphin.util.HomeRowLoadingState.Success
import com.github.damontecres.wholphin.util.supportedHomeCollectionTypes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.UserDto
import org.jellyfin.sdk.model.api.request.GetGenresRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetLatestMediaRequest
import org.jellyfin.sdk.model.api.request.GetPersonsRequest
import org.jellyfin.sdk.model.api.request.GetRecommendedProgramsRequest
import org.jellyfin.sdk.model.api.request.GetRecordingsRequest
import org.jellyfin.sdk.model.api.request.GetStudiosRequest
import timber.log.Timber
import java.io.File
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles getting home page settings and data
 */
@Singleton
class HomeSettingsService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val userPreferencesService: UserPreferencesService,
        private val navDrawerService: NavDrawerService,
        private val latestNextUpService: LatestNextUpService,
        private val imageUrlService: ImageUrlService,
        private val suggestionService: SuggestionService,
        private val displayPreferencesService: DisplayPreferencesService,
    ) {
        @OptIn(ExperimentalSerializationApi::class)
        val jsonParser =
            Json {
                isLenient = true
                ignoreUnknownKeys = true
                allowTrailingComma = true
            }

        /**
         * The current home page settings
         */
        val currentSettings = MutableStateFlow(HomePageResolvedSettings.EMPTY)

        private var mediaBannerUserId: UUID? = null
        private var mediaBannerItemsCache: List<BaseItem> = emptyList()

        private data class HomePageCacheEntry(
            val settings: HomePageResolvedSettings,
            val homeRows: List<HomeRowLoadingState>,
            val mediaBannerItems: List<BaseItem>,
            val cachedAtMs: Long,
        )

        private var homePageCacheUserId: UUID? = null
        private var homePageCache: HomePageCacheEntry? = null

        fun getHomePageCache(
            userId: UUID,
            settings: HomePageResolvedSettings,
        ): CachedHomePage? {
            val entry = homePageCache ?: return null
            if (homePageCacheUserId != userId) return null
            if (entry.settings != settings) return null
            if (System.currentTimeMillis() - entry.cachedAtMs > HOME_PAGE_CACHE_TTL_MS) return null
            return CachedHomePage(
                homeRows = entry.homeRows,
                mediaBannerItems = entry.mediaBannerItems,
            )
        }

        fun putHomePageCache(
            userId: UUID,
            settings: HomePageResolvedSettings,
            homeRows: List<HomeRowLoadingState>,
            mediaBannerItems: List<BaseItem>,
        ) {
            homePageCacheUserId = userId
            homePageCache =
                HomePageCacheEntry(
                    settings = settings,
                    homeRows = homeRows,
                    mediaBannerItems = mediaBannerItems,
                    cachedAtMs = System.currentTimeMillis(),
                )
        }

        fun clearHomePageCache() {
            homePageCacheUserId = null
            homePageCache = null
        }

        /**
         * Saves a [HomePageSettings] to the server for the user under the display preference ID
         *
         * @see loadFromServer
         */
        suspend fun saveToServer(
            userId: UUID,
            settings: HomePageSettings,
            displayPreferencesId: String = DisplayPreferencesService.DEFAULT_DISPLAY_PREF_ID,
        ) {
            displayPreferencesService.updateDisplayPreferences(userId, displayPreferencesId) {
                put(CUSTOM_PREF_ID, jsonParser.encodeToString(settings))
            }
            clearHomePageCache()
        }

        /**
         * Reads a [HomePageSettings] from the server for the user and display preference ID
         *
         * Returns null if there is none saved
         *
         * @see saveToServer
         */
        suspend fun loadFromServer(
            userId: UUID,
            displayPreferencesId: String = DisplayPreferencesService.DEFAULT_DISPLAY_PREF_ID,
        ): HomePageSettings? =
            displayPreferencesService
                .getDisplayPreferences(userId, displayPreferencesId)
                .customPrefs[CUSTOM_PREF_ID]
                ?.let {
                    val jsonElement = jsonParser.parseToJsonElement(it)
                    decode(jsonElement)
                }

        /**
         * Computes the filename for locally saved [HomePageSettings]
         */
        private fun filename(userId: UUID) = "${CUSTOM_PREF_ID}_${userId.toServerString()}.json"

        /**
         * Save the [HomePageSettings] for the user locally on the device
         *
         * @see loadFromLocal
         */
        @OptIn(ExperimentalSerializationApi::class)
        suspend fun saveToLocal(
            userId: UUID,
            settings: HomePageSettings,
        ) {
            val dir = File(context.filesDir, CUSTOM_PREF_ID)
            dir.mkdirs()
            File(dir, filename(userId)).outputStream().use {
                jsonParser.encodeToStream(settings, it)
            }
        }

        /**
         * Reads [HomePageSettings] for the user if it exists
         *
         * @see saveToLocal
         */
        @OptIn(ExperimentalSerializationApi::class)
        suspend fun loadFromLocal(userId: UUID): HomePageSettings? {
            val dir = File(context.filesDir, CUSTOM_PREF_ID)
            val file = File(dir, filename(userId))
            return if (file.exists()) {
                val fileContents = file.readText()
                val jsonElement = jsonParser.parseToJsonElement(fileContents)
                decode(jsonElement)
            } else {
                null
            }
        }

        /**
         * Decodes [HomePageSettings] from a [JsonElement] skipping any unknown/unparsable rows
         *
         * This is public only for testing
         */
        fun decode(element: JsonElement): HomePageSettings {
            val version = element.jsonObject["version"]?.jsonPrimitive?.intOrNull
            if (version == null || version > SUPPORTED_HOME_PAGE_SETTINGS_VERSION) {
                throw UnsupportedHomeSettingsVersionException(version)
            }
            val rowsElement = element.jsonObject["rows"]?.jsonArray
            val rows =
                rowsElement
                    ?.mapNotNull { row ->
                        try {
                            jsonParser.decodeFromJsonElement<HomeRowConfig>(row)
                        } catch (ex: Exception) {
                            Timber.w(ex, "Unknown row %s", row)
                            // TODO maybe use placeholder instead of null?
                            null
                        }
                    }.orEmpty()
            return HomePageSettings(rows, version)
        }

        /**
         * Loads [HomePageSettings] into [currentSettings]
         *
         * First checks locally, then on the server, and finally creates a default if needed
         *
         * Does not persist either the server nor default
         */
        suspend fun loadCurrentSettings(userId: UUID) {
            Timber.v("Getting setting for %s", userId)
            // User local then server/remote otherwise create a default
            val settings =
                try {
                    val local = loadFromLocal(userId)
                    Timber.v("Found local? %s", local != null)
                    local
                } catch (ex: Exception) {
                    Timber.w(ex, "Error loading local settings")
                    // TODO show toast?
                    null
                } ?: try {
                    val remote = loadFromServer(userId)
                    Timber.v("Found remote? %s", remote != null)
                    remote
                } catch (ex: Exception) {
                    Timber.w(ex, "Error loading remote settings")
                    null
                }
            val migratedSettings =
                settings?.let { migrateCombinedMediaRows(userId, it) }
            if (migratedSettings != null && migratedSettings != settings) {
                Timber.i("Migrated home settings to combined recently added/released rows")
                try {
                    saveToLocal(userId, migratedSettings)
                } catch (ex: Exception) {
                    Timber.w(ex, "Error saving migrated home settings")
                }
            }
            val resolvedSettings =
                if (migratedSettings != null) {
                    Timber.v("Found settings")
                    // Resolve
                    val resolvedRows =
                        migratedSettings.rows.mapIndexed { index, config ->
                            resolve(index, config)
                        }
                    HomePageResolvedSettings(resolvedRows)
                } else {
                    createDefault(userId)
                }

            currentSettings.update { resolvedSettings }
        }

        /**
         * Replaces split movie/TV recently added and recently released rows with combined rows.
         */
        suspend fun migrateCombinedMediaRows(
            userId: UUID,
            settings: HomePageSettings,
        ): HomePageSettings {
            val newRows = settings.rows.toMutableList()
            var changed = false

            val splitReleasedIndices =
                newRows.mapIndexedNotNull { index, row ->
                    if (row is HomeRowConfig.Category &&
                        (
                            row.category == HomeCategory.RECENTLY_RELEASED_MOVIES ||
                                row.category == HomeCategory.RECENTLY_RELEASED_TV
                        )
                    ) {
                        index
                    } else {
                        null
                    }
                }
            if (splitReleasedIndices.isNotEmpty() &&
                newRows.none {
                    it is HomeRowConfig.Category &&
                        it.category == HomeCategory.RECENTLY_RELEASED
                }
            ) {
                val insertAt = splitReleasedIndices.min()
                splitReleasedIndices.reversed().forEach { newRows.removeAt(it) }
                newRows.add(insertAt, HomeRowConfig.Category(HomeCategory.RECENTLY_RELEASED))
                changed = true
            }

            val userDto = serverRepository.currentUserDto.value?.takeIf { it.id == userId }
            val libraries =
                try {
                    navDrawerService.getAllUserLibraries(userId, userDto?.tvAccess ?: false)
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not load libraries for home settings migration")
                    emptyList()
                }
            val mediaLibraryIds =
                libraries
                    .filter {
                        it.collectionType == CollectionType.MOVIES ||
                            it.collectionType == CollectionType.TVSHOWS
                    }.map { it.itemId }
                    .toSet()

            val recentlyAddedIndices =
                newRows.mapIndexedNotNull { index, row ->
                    if (row is HomeRowConfig.RecentlyAdded) index else null
                }
            if (recentlyAddedIndices.isNotEmpty() &&
                newRows.none {
                    it is HomeRowConfig.Category &&
                        it.category == HomeCategory.RECENTLY_ADDED
                }
            ) {
                val mediaRecentlyAddedIndices =
                    recentlyAddedIndices.filter { newRows[it] is HomeRowConfig.RecentlyAdded &&
                        (newRows[it] as HomeRowConfig.RecentlyAdded).parentId in mediaLibraryIds
                    }
                val indicesToMerge =
                    when {
                        mediaRecentlyAddedIndices.isNotEmpty() -> mediaRecentlyAddedIndices
                        recentlyAddedIndices.size > 1 -> recentlyAddedIndices
                        else -> emptyList()
                    }
                if (indicesToMerge.isNotEmpty()) {
                    val insertAt = indicesToMerge.min()
                    indicesToMerge.reversed().forEach { newRows.removeAt(it) }
                    newRows.add(insertAt, HomeRowConfig.Category(HomeCategory.RECENTLY_ADDED))
                    changed = true
                }
            }

            if (mediaLibraryIds.isNotEmpty()) {
                changed =
                    mergeMediaRows(
                        newRows,
                        mediaLibraryIds,
                        ::isMediaRecentlyReleased,
                        HomeRowConfig.Category(HomeCategory.RECENTLY_RELEASED),
                    ) || changed
            }

            return if (changed) settings.copy(rows = newRows) else settings
        }

        private fun isMediaRecentlyReleased(
            row: HomeRowConfig,
            mediaLibraryIds: Set<UUID>,
        ): Boolean = row is HomeRowConfig.RecentlyReleased && row.parentId in mediaLibraryIds

        private inline fun mergeMediaRows(
            rows: MutableList<HomeRowConfig>,
            mediaLibraryIds: Set<UUID>,
            matches: (HomeRowConfig, Set<UUID>) -> Boolean,
            combinedRow: HomeRowConfig.Category,
        ): Boolean {
            val indices =
                rows.mapIndexedNotNull { index, row ->
                    if (matches(row, mediaLibraryIds)) index else null
                }
            if (indices.isEmpty() ||
                rows.any {
                    it is HomeRowConfig.Category && it.category == combinedRow.category
                }
            ) {
                return false
            }
            val insertAt = indices.min()
            indices.reversed().forEach { rows.removeAt(it) }
            rows.add(insertAt, combinedRow)
            return true
        }

        /**
         * Resolve the settings and set them to be the current settings
         */
        suspend fun updateCurrent(settings: HomePageSettings) {
            val resolvedRows =
                settings.rows.mapIndexed { index, config ->
                    resolve(index, config)
                }
            val resolvedSettings = HomePageResolvedSettings(resolvedRows)
            currentSettings.update { resolvedSettings }
        }

        /**
         * Create a default [HomePageResolvedSettings] using the available libraries
         */
        suspend fun createDefault(userId: UUID): HomePageResolvedSettings {
            Timber.v("Creating default settings")
            val user = serverRepository.currentUser.value?.takeIf { it.id == userId }
            val userDto = serverRepository.currentUserDto.value?.takeIf { it.id == userId }
            val libraries =
                if (user != null) {
                    navDrawerService.getFilteredUserLibraries(user, userDto?.tvAccess ?: false)
                } else {
                    navDrawerService.getAllUserLibraries(userId, userDto?.tvAccess ?: false)
                }

            val prefs =
                userPreferencesService.getCurrent().appPreferences.homePagePreferences

            var rowId = 0
            val hasMoviesOrTv =
                libraries.any {
                    it.collectionType == CollectionType.MOVIES ||
                        it.collectionType == CollectionType.TVSHOWS
                }
            val combinedMediaRow =
                if (hasMoviesOrTv) {
                    listOf(
                        HomeRowConfigDisplay(
                            id = rowId++,
                            title = context.getString(R.string.recently_added),
                            config = HomeRowConfig.Category(HomeCategory.RECENTLY_ADDED),
                        ),
                        HomeRowConfigDisplay(
                            id = rowId++,
                            title = context.getString(R.string.recently_released),
                            config = HomeRowConfig.Category(HomeCategory.RECENTLY_RELEASED),
                        ),
                    )
                } else {
                    emptyList()
                }
            val includedIds =
                combinedMediaRow +
                    libraries.mapNotNull { library ->
                        when (library.collectionType) {
                            CollectionType.MOVIES,
                            CollectionType.TVSHOWS,
                            -> null

                            CollectionType.LIVETV ->
                                HomeRowConfigDisplay(
                                    id = rowId++,
                                    title = context.getString(R.string.live_tv),
                                    config = HomeRowConfig.TvPrograms(),
                                )

                            else ->
                                HomeRowConfigDisplay(
                                    id = rowId++,
                                    title = getRecentlyAddedTitle(context, library),
                                    config = HomeRowConfig.RecentlyAdded(library.itemId),
                                )
                        }
                    }
            val continueWatchingRows =
                if (prefs.combineContinueNext) {
                    listOf(
                        HomeRowConfigDisplay(
                            id = includedIds.size + 1,
                            title = context.getString(R.string.combine_continue_next),
                            config = HomeRowConfig.ContinueWatchingCombined(),
                        ),
                    )
                } else {
                    listOf(
                        HomeRowConfigDisplay(
                            id = includedIds.size + 1,
                            title = context.getString(R.string.continue_watching),
                            config = HomeRowConfig.ContinueWatching(),
                        ),
                        HomeRowConfigDisplay(
                            id = includedIds.size + 2,
                            title = context.getString(R.string.next_up),
                            config = HomeRowConfig.NextUp(),
                        ),
                    )
                }
            val rowConfig = continueWatchingRows + includedIds
            return HomePageResolvedSettings(rowConfig)
        }

        /**
         * Create home page settings from the user's web UI home page settings
         */
        suspend fun parseFromWebConfig(userId: UUID): HomePageResolvedSettings? {
            val customPrefs =
                displayPreferencesService
                    .getDisplayPreferences(
                        displayPreferencesId = "usersettings",
                        userId = userId,
                        client = "emby",
                    ).customPrefs
            val userDto by api.userApi.getUserById(userId)
            val config = userDto.configuration ?: DefaultUserConfiguration
            val libraries =
                api.userViewsApi
                    .getUserViews(userId = userId)
                    .content.items
                    .filter {
                        it.collectionType in supportedHomeCollectionTypes &&
                            it.id !in config.latestItemsExcludes
                    }

            return if (customPrefs.isNotEmpty()) {
                var id = 0
                val rowConfigs =
                    (0..9)
                        .mapNotNull { idx ->
                            val sectionType =
                                HomeSectionType.fromString(customPrefs["homesection$idx"]?.lowercase())
                            Timber.v(
                                "sectionType=$sectionType, %s",
                                customPrefs["homesection$idx"]?.lowercase(),
                            )
                            val config =
                                when (sectionType) {
                                    HomeSectionType.ACTIVE_RECORDINGS -> {
                                        HomeRowConfigDisplay(
                                            id = id++,
                                            title = context.getString(R.string.active_recordings),
                                            config = HomeRowConfig.Recordings(),
                                        )
                                    }

                                    HomeSectionType.RESUME -> {
                                        HomeRowConfigDisplay(
                                            id = id++,
                                            title = context.getString(R.string.continue_watching),
                                            config = HomeRowConfig.ContinueWatching(),
                                        )
                                    }

                                    HomeSectionType.NEXT_UP -> {
                                        HomeRowConfigDisplay(
                                            id = id++,
                                            title = context.getString(R.string.next_up),
                                            config = HomeRowConfig.NextUp(),
                                        )
                                    }

                                    HomeSectionType.LIVE_TV -> {
                                        if (userDto.tvAccess) {
                                            HomeRowConfigDisplay(
                                                id = id++,
                                                title = context.getString(R.string.live_tv),
                                                config = HomeRowConfig.TvPrograms(),
                                            )
                                        } else {
                                            null
                                        }
                                    }

                                    HomeSectionType.LATEST_MEDIA -> {
                                        // Handled below
                                        null
                                    }

                                    // Unsupported
                                    HomeSectionType.RESUME_AUDIO,
                                    HomeSectionType.RESUME_BOOK,
                                    -> {
                                        null
                                    }

                                    HomeSectionType.SMALL_LIBRARY_TILES,
                                    HomeSectionType.LIBRARY_BUTTONS,
                                    HomeSectionType.NONE,
                                    null,
                                    -> {
                                        null
                                    }
                                }
                            if (sectionType == HomeSectionType.LATEST_MEDIA) {
                                libraries.map {
                                    HomeRowConfigDisplay(
                                        id = id++,
                                        title =
                                            context.getString(
                                                R.string.recently_added_in,
                                                it.name ?: "",
                                            ),
                                        config = HomeRowConfig.RecentlyAdded(it.id),
                                    )
                                }
                            } else if (config != null) {
                                listOf(config)
                            } else {
                                null
                            }
                        }.flatten()
                HomePageResolvedSettings(rowConfigs)
            } else {
                null
            }
        }

        /**
         * Converts a [HomeRowConfig] into [HomeRowConfigDisplay] for UI purposes
         */
        suspend fun resolve(
            id: Int,
            config: HomeRowConfig,
        ): HomeRowConfigDisplay =
            when (config) {
                is HomeRowConfig.ByParent -> {
                    val name = getItemName(config.parentId) ?: ""
                    HomeRowConfigDisplay(
                        id,
                        name,
                        config,
                    )
                }

                is HomeRowConfig.ContinueWatching -> {
                    HomeRowConfigDisplay(
                        id,
                        context.getString(R.string.continue_watching),
                        config,
                    )
                }

                is HomeRowConfig.ContinueWatchingCombined -> {
                    HomeRowConfigDisplay(
                        id,
                        context.getString(R.string.combine_continue_next),
                        config,
                    )
                }

                is HomeRowConfig.Genres -> {
                    val name = getItemName(config.parentId) ?: ""
                    HomeRowConfigDisplay(
                        id,
                        context.getString(R.string.genres_in, name),
                        config,
                    )
                }

                is HomeRowConfig.ByGenre -> {
                    val libraryName = getItemName(config.parentId) ?: ""
                    HomeRowConfigDisplay(
                        id,
                        context.getString(R.string.genre_row_title, config.genreName, libraryName),
                        config,
                    )
                }

                is HomeRowConfig.RotatingGenre -> {
                    val libraryName = getItemName(config.parentId) ?: ""
                    HomeRowConfigDisplay(
                        id,
                        context.getString(R.string.rotating_genre_row_settings_title, libraryName),
                        config,
                    )
                }

                is HomeRowConfig.Studios -> {
                    val name = getItemName(config.parentId) ?: ""
                    HomeRowConfigDisplay(
                        id,
                        context.getString(R.string.studios_in, name),
                        config,
                    )
                }

                is HomeRowConfig.GetItems -> {
                    HomeRowConfigDisplay(id, config.name, config)
                }

                is HomeRowConfig.Category -> {
                    HomeRowConfigDisplay(
                        id = id,
                        title = getCategoryTitle(config.category),
                        config = config,
                    )
                }

                is HomeRowConfig.Seasonal -> {
                    HomeRowConfigDisplay(
                        id = id,
                        title =
                            context.getString(
                                R.string.seasonal_row_settings_title,
                                getSeasonalTitle(config.category),
                                getSeasonalMonth(config.category),
                            ),
                        config = config,
                    )
                }

                is HomeRowConfig.NextUp -> {
                    HomeRowConfigDisplay(
                        id,
                        context.getString(R.string.next_up),
                        config,
                    )
                }

                is HomeRowConfig.RecentlyAdded -> {
                    val name = getItemName(config.parentId) ?: ""
                    HomeRowConfigDisplay(
                        id,
                        context.getString(R.string.recently_added_in, name),
                        config,
                    )
                }

                is HomeRowConfig.RecentlyReleased -> {
                    val name = getItemName(config.parentId) ?: ""
                    HomeRowConfigDisplay(
                        id,
                        context.getString(R.string.recently_released_in, name),
                        config,
                    )
                }

                is HomeRowConfig.Favorite -> {
                    val name =
                        context.getString(
                            R.string.favorite_items,
                            context.getString(favoriteOptions[config.kind]!!),
                        )
                    HomeRowConfigDisplay(id, name, config)
                }

                is HomeRowConfig.Recordings -> {
                    HomeRowConfigDisplay(
                        id = id,
                        title = context.getString(R.string.active_recordings),
                        config,
                    )
                }

                is HomeRowConfig.TvPrograms -> {
                    HomeRowConfigDisplay(
                        id = id,
                        title = context.getString(R.string.live_tv),
                        config,
                    )
                }

                is HomeRowConfig.TvChannels -> {
                    HomeRowConfigDisplay(
                        id = id,
                        title = context.getString(R.string.channels),
                        config,
                    )
                }

                is HomeRowConfig.Suggestions -> {
                    val name = getItemName(config.parentId) ?: ""
                    HomeRowConfigDisplay(
                        id = id,
                        title = context.getString(R.string.suggestions_for, name),
                        config,
                    )
                }
            }

        private suspend fun getItemName(itemId: UUID): String? =
            try {
                api.userLibraryApi
                    .getItem(itemId = itemId)
                    .content.name
            } catch (ex: Exception) {
                Timber.e(ex, "Could not get name for %s", itemId)
                context.getString(R.string.unknown)
            }

        /**
         * Returns the latest home page media banner set, fetching it if the cache is empty.
         */
        suspend fun fetchCurrentMediaBannerItems(limit: Int = 10): List<BaseItem> {
            val userDto = serverRepository.currentUserDto.value ?: return emptyList()
            if (mediaBannerUserId == userDto.id && mediaBannerItemsCache.isNotEmpty()) {
                return mediaBannerItemsCache.take(limit)
            }
            val libraries = navDrawerService.getAllUserLibraries(userDto.id, userDto.tvAccess)
            return fetchMediaBannerItems(userDto, libraries, limit)
        }

        /**
         * Fetches a small randomized movie/show set for the home page media banner.
         */
        suspend fun fetchMediaBannerItems(
            userDto: UserDto,
            libraries: List<Library>,
            limit: Int = 10,
        ): List<BaseItem> {
            val mediaLibraries =
                libraries.filter {
                    it.collectionType == CollectionType.MOVIES || it.collectionType == CollectionType.TVSHOWS
                }
            if (mediaLibraries.isEmpty()) return emptyList()

            val itemsPerLibrary = (limit * 2 / mediaLibraries.size).coerceAtLeast(4)
            val items =
                mediaLibraries
                    .flatMap { library ->
                        val itemKind =
                            when (library.collectionType) {
                                CollectionType.MOVIES -> BaseItemKind.MOVIE
                                CollectionType.TVSHOWS -> BaseItemKind.SERIES
                                else -> return@flatMap emptyList()
                            }
                        val request =
                            GetItemsRequest(
                                userId = userDto.id,
                                parentId = library.itemId,
                                recursive = true,
                                includeItemTypes = listOf(itemKind),
                                sortBy = listOf(ItemSortBy.RANDOM),
                                limit = itemsPerLibrary,
                                fields = homeRowItemFields,
                                imageTypes = listOf(ImageType.BACKDROP),
                                enableImageTypes =
                                    listOf(
                                        ImageType.BACKDROP,
                                        ImageType.LOGO,
                                        ImageType.PRIMARY,
                                    ),
                                imageTypeLimit = 1,
                            )

                        try {
                            GetItemsRequestHandler
                                .execute(api, request)
                                .content.items
                                .map { BaseItem(it, useSeriesForPrimary = true) }
                        } catch (ex: Exception) {
                            Timber.w(ex, "Could not fetch media banner items for %s", library.name)
                            emptyList()
                        }
                    }.distinctBy { item ->
                        item.data.seriesId ?: item.id
                    }.shuffled()

            val bannerItems = items.take(limit)
            mediaBannerUserId = userDto.id
            mediaBannerItemsCache = bannerItems
            return bannerItems
        }

        /**
         * Fetch the data from the server for a given [HomeRowConfig]
         */
        private suspend fun fetchGetItemsPage(
            request: GetItemsRequest,
            limit: Int,
            startIndex: Int,
            useSeries: Boolean,
        ): Pair<List<BaseItem>, Boolean> {
            val response =
                GetItemsRequestHandler
                    .execute(
                        api,
                        request.copy(
                            limit = limit,
                            startIndex = startIndex,
                            enableTotalRecordCount = true,
                        ),
                    ).content
            val items = response.items.map { BaseItem(it, useSeries) }
            return items to hasMoreItems(items.size, limit, startIndex, response.totalRecordCount)
        }

        private suspend fun fetchGenresPage(
            request: GetGenresRequest,
            limit: Int,
            startIndex: Int,
        ): Pair<List<BaseItemDto>, Boolean> {
            val response =
                GetGenresRequestHandler
                    .execute(
                        api,
                        request.copy(
                            limit = limit,
                            startIndex = startIndex,
                            enableTotalRecordCount = true,
                        ),
                    ).content
            return response.items to
                hasMoreItems(
                    response.items.size,
                    limit,
                    startIndex,
                    response.totalRecordCount,
                )
        }

        private suspend fun fetchStudiosPage(
            request: GetStudiosRequest,
            limit: Int,
            startIndex: Int,
        ): Pair<List<BaseItemDto>, Boolean> {
            val response =
                GetStudiosRequestHandler
                    .execute(
                        api,
                        request.copy(
                            limit = limit,
                            startIndex = startIndex,
                            enableTotalRecordCount = true,
                        ),
                    ).content
            return response.items to
                hasMoreItems(
                    response.items.size,
                    limit,
                    startIndex,
                    response.totalRecordCount,
                )
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

        suspend fun fetchDataForRow(
            row: HomeRowConfig,
            scope: CoroutineScope,
            prefs: HomePagePreferences,
            userDto: UserDto,
            libraries: List<Library>,
            limit: Int = HOME_PAGE_ROW_SIZE,
            startIndex: Int = 0,
            isRefresh: Boolean,
            includeInactiveSeasonal: Boolean = false,
        ): HomeRowLoadingState =
            when (row) {
                is HomeRowConfig.ContinueWatching -> {
                    val (resume, hasMore) =
                        latestNextUpService.getResumePage(
                            userDto.id,
                            limit,
                            startIndex,
                            true,
                            row.viewOptions.useSeries,
                        )

                    Success(
                        title = context.getString(R.string.continue_watching),
                        items = resume,
                        viewOptions = row.viewOptions,
                        rowType = row,
                        hasMore = hasMore,
                    )
                }

                is HomeRowConfig.NextUp -> {
                    val (nextUp, hasMore) =
                        latestNextUpService.getNextUpPage(
                            userDto.id,
                            limit,
                            startIndex,
                            prefs.enableRewatchingNextUp,
                            false,
                            prefs.maxDaysNextUp,
                            row.viewOptions.useSeries,
                        )

                    Success(
                        title = context.getString(R.string.next_up),
                        items = nextUp,
                        viewOptions = row.viewOptions,
                        rowType = row,
                        hasMore = hasMore,
                    )
                }

                is HomeRowConfig.ContinueWatchingCombined -> {
                    val (items, hasMore) =
                        latestNextUpService.fetchCombinedContinueWatching(
                            userId = userDto.id,
                            limit = limit,
                            startIndex = startIndex,
                            includeEpisodes = true,
                            enableRewatching = prefs.enableRewatchingNextUp,
                            enableResumable = false,
                            maxDays = prefs.maxDaysNextUp,
                            useSeriesForPrimary = row.viewOptions.useSeries,
                        )

                    Success(
                        title = context.getString(R.string.continue_watching),
                        items = items,
                        viewOptions = row.viewOptions,
                        rowType = row,
                        hasMore = hasMore,
                    )
                }

                is HomeRowConfig.Category -> {
                    val request =
                        GetItemsRequest(
                            userId = userDto.id,
                            recursive = true,
                            includeItemTypes = row.category.itemKinds,
                            sortBy = listOf(row.category.sortBy),
                            sortOrder = listOf(row.category.sortOrder),
                            isPlayed = row.category.isPlayed,
                            minCommunityRating = row.category.minCommunityRating,
                            maxPremiereDate =
                                LocalDateTime.now().takeIf {
                                    row.category.sortBy == ItemSortBy.PREMIERE_DATE
                                },
                            fields = homeRowItemFields,
                        )
                    val (items, hasMore) =
                        fetchGetItemsPage(
                            request,
                            limit,
                            startIndex,
                            row.viewOptions.useSeries,
                        )

                    Success(
                        title = getCategoryTitle(row.category),
                        items = items,
                        viewOptions = row.viewOptions,
                        rowType = row,
                        hasMore = hasMore,
                    )
                }

                is HomeRowConfig.Seasonal -> {
                    val title = getSeasonalTitle(row.category)
                    if (!includeInactiveSeasonal && !row.category.isActive()) {
                        Success(
                            title = title,
                            items = emptyList(),
                            viewOptions = row.viewOptions,
                            rowType = row,
                        )
                    } else {
                        val mediaLibraries =
                            libraries.filter {
                                it.collectionType == CollectionType.MOVIES ||
                                    it.collectionType == CollectionType.TVSHOWS
                            }
                        val requests =
                            mediaLibraries.flatMap { library ->
                                val itemKind =
                                    when (library.collectionType) {
                                        CollectionType.MOVIES -> BaseItemKind.MOVIE
                                        CollectionType.TVSHOWS -> BaseItemKind.SERIES
                                        else -> return@flatMap emptyList()
                                    }
                                buildList {
                                    if (row.category.genres.isNotEmpty()) {
                                        add(
                                            GetItemsRequest(
                                                userId = userDto.id,
                                                parentId = library.itemId,
                                                recursive = true,
                                                includeItemTypes = listOf(itemKind),
                                                genres = row.category.genres,
                                                sortBy = listOf(ItemSortBy.RANDOM),
                                                fields = homeRowItemFields,
                                                enableTotalRecordCount = false,
                                            ),
                                        )
                                    }
                                    row.category.searchTerms.forEach { searchTerm ->
                                        add(
                                            GetItemsRequest(
                                                userId = userDto.id,
                                                parentId = library.itemId,
                                                recursive = true,
                                                includeItemTypes = listOf(itemKind),
                                                searchTerm = searchTerm,
                                                sortBy = listOf(ItemSortBy.RANDOM),
                                                fields = homeRowItemFields,
                                                enableTotalRecordCount = false,
                                            ),
                                        )
                                    }
                                }
                            }
                        val itemsPerRequest =
                            if (requests.isEmpty()) {
                                0
                            } else {
                                (limit * 2 / requests.size).coerceAtLeast(4)
                            }
                        val items =
                            requests
                                .flatMap { request ->
                                    try {
                                        GetItemsRequestHandler
                                            .execute(api, request.copy(limit = itemsPerRequest))
                                            .content.items
                                            .map { BaseItem(it, row.viewOptions.useSeries) }
                                    } catch (ex: Exception) {
                                        Timber.w(
                                            ex,
                                            "Could not fetch %s seasonal items",
                                            row.category,
                                        )
                                        emptyList()
                                    }
                                }.distinctBy { it.id }
                                .shuffled()
                                .take(limit)

                        Success(
                            title = title,
                            items = items,
                            viewOptions = row.viewOptions,
                            rowType = row,
                        )
                    }
                }

                is HomeRowConfig.ByGenre -> {
                    val library = libraries.firstOrNull { it.itemId == row.parentId }
                    val includeItemTypes = genreIncludeItemTypes(library)
                    val request =
                        GetItemsRequest(
                            userId = userDto.id,
                            parentId = row.parentId,
                            recursive = true,
                            includeItemTypes = includeItemTypes,
                            genres = listOf(row.genreName),
                            sortBy = listOf(ItemSortBy.DATE_CREATED),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            fields = homeRowItemFields,
                        )
                    val (items, hasMore) =
                        fetchGetItemsPage(
                            request,
                            limit,
                            startIndex,
                            row.viewOptions.useSeries,
                        )
                    val libraryName = library?.name ?: getItemName(row.parentId).orEmpty()
                    val title =
                        if (libraryName.isNotEmpty()) {
                            context.getString(R.string.genre_row_title, row.genreName, libraryName)
                        } else {
                            row.genreName
                        }

                    Success(
                        title = title,
                        items = items,
                        viewOptions = row.viewOptions,
                        rowType = row,
                        hasMore = hasMore,
                    )
                }

                is HomeRowConfig.RotatingGenre -> {
                    val library = libraries.firstOrNull { it.itemId == row.parentId }
                    val libraryName = library?.name ?: getItemName(row.parentId).orEmpty()
                    val genreNames =
                        getGenreNamesForLibrary(
                            userId = userDto.id,
                            parentId = row.parentId,
                            collectionType = library?.collectionType,
                        )
                    val genreName =
                        selectRotatingGenre(genreNames, row.intervalHours)
                    if (genreName == null) {
                        Success(
                            title =
                                if (libraryName.isNotEmpty()) {
                                    context.getString(
                                        R.string.rotating_genre_row_settings_title,
                                        libraryName,
                                    )
                                } else {
                                    context.getString(R.string.genres)
                                },
                            items = emptyList(),
                            viewOptions = row.viewOptions,
                            rowType = row,
                        )
                    } else {
                    val includeItemTypes = genreIncludeItemTypes(library)
                    val request =
                        GetItemsRequest(
                            userId = userDto.id,
                            parentId = row.parentId,
                            recursive = true,
                            includeItemTypes = includeItemTypes,
                            genres = listOf(genreName),
                            sortBy = listOf(ItemSortBy.DATE_CREATED),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            fields = homeRowItemFields,
                        )
                    val (items, hasMore) =
                        fetchGetItemsPage(
                            request,
                            limit,
                            startIndex,
                            row.viewOptions.useSeries,
                        )
                    val title =
                        if (libraryName.isNotEmpty()) {
                            context.getString(R.string.genre_row_title, genreName, libraryName)
                        } else {
                            genreName
                        }

                    Success(
                        title = title,
                        items = items,
                        viewOptions = row.viewOptions,
                        rowType = row,
                        hasMore = hasMore,
                    )
                    }
                }

                is HomeRowConfig.Genres -> {
                    val request =
                        GetGenresRequest(
                            parentId = row.parentId,
                            userId = userDto.id,
                        )
                    val (items, hasMore) = fetchGenresPage(request, limit, startIndex)
                    val genreIds = items.map { it.id }
                    val genreImages =
                        getGenreImageMap(
                            api = api,
                            userId = serverRepository.currentUser.value?.id,
                            scope = scope,
                            imageUrlService = imageUrlService,
                            genres = genreIds,
                            parentId = row.parentId,
                            includeItemTypes = null,
                            cardWidthPx = null,
                            useCache = isRefresh,
                        )
                    val library =
                        libraries
                            .firstOrNull { it.itemId == row.parentId }

                    val title =
                        library?.name?.let { context.getString(R.string.genres_in, it) }
                            ?: context.getString(R.string.genres)
                    val genres =
                        items.map {
                            BaseItem(
                                it,
                                false,
                                genreImages[it.id],
                                createGenreDestination(
                                    genreId = it.id,
                                    genreName = it.name ?: "",
                                    parentId = row.parentId,
                                    parentName = library?.name,
                                    includeItemTypes =
                                        library?.collectionType?.let {
                                            getTypeFor(it)?.let {
                                                listOf(it)
                                            }
                                        },
                                ),
                            )
                        }

                    Success(
                        title,
                        genres,
                        viewOptions = row.viewOptions,
                        rowType = row,
                        hasMore = hasMore,
                    )
                }

                is HomeRowConfig.Studios -> {
                    val request =
                        GetStudiosRequest(
                            parentId = row.parentId,
                            userId = userDto.id,
                            includeItemTypes = listOf(BaseItemKind.SERIES),
                        )
                    val (items, hasMore) = fetchStudiosPage(request, limit, startIndex)
                    val library =
                        libraries
                            .firstOrNull { it.itemId == row.parentId }
                    val title =
                        library?.name?.let { context.getString(R.string.studios_in, it) }
                            ?: context.getString(R.string.studios)
                    val studios =
                        items.map {
                            val imageUrl =
                                imageUrlService.getItemImageUrl(
                                    itemId = it.id,
                                    imageType = ImageType.THUMB,
                                )
                            BaseItem(
                                it,
                                false,
                                imageUrl,
                                createStudioDestination(
                                    studioId = it.id,
                                    name = it.name ?: "",
                                    parentId = row.parentId,
                                    parentName = library?.name,
                                    includeItemTypes =
                                        library?.collectionType?.let {
                                            getTypeFor(it)?.let {
                                                listOf(it)
                                            }
                                        },
                                ),
                            )
                        }

                    Success(
                        title,
                        studios,
                        viewOptions = row.viewOptions,
                        hasMore = hasMore,
                    )
                }

                is HomeRowConfig.RecentlyAdded -> {
                    val library =
                        libraries
                            .firstOrNull { it.itemId == row.parentId }
                    val title = getRecentlyAddedTitle(context, library)
                    val collectionType = library?.collectionType
                    val itemKind =
                        when (collectionType) {
                            CollectionType.MOVIES -> BaseItemKind.MOVIE
                            CollectionType.TVSHOWS -> BaseItemKind.SERIES
                            else -> null
                        }
                    if (itemKind != null) {
                        val request =
                            GetItemsRequest(
                                parentId = row.parentId,
                                sortBy = listOf(ItemSortBy.DATE_CREATED),
                                sortOrder = listOf(SortOrder.DESCENDING),
                                fields = homeRowItemFields,
                                recursive = true,
                                includeItemTypes = listOf(itemKind),
                            )
                        val (items, hasMore) =
                            fetchGetItemsPage(
                                request,
                                limit,
                                startIndex,
                                row.viewOptions.useSeries,
                            )
                        Success(
                            title,
                            items,
                            row.viewOptions,
                            rowType = row,
                            hasMore = hasMore,
                        )
                    } else {
                        val request =
                            GetLatestMediaRequest(
                                fields = SlimItemFields,
                                imageTypeLimit = 1,
                                parentId = row.parentId,
                                groupItems = true,
                                limit = limit,
                                isPlayed = null,
                            )
                        val latest =
                            api.userLibraryApi
                                .getLatestMedia(request)
                                .content
                                .map { BaseItem.Companion.from(it, api, row.viewOptions.useSeries) }
                        Success(
                            title,
                            latest,
                            row.viewOptions,
                            rowType = row,
                            hasMore = latest.size >= limit,
                        )
                    }
                }

                is HomeRowConfig.RecentlyReleased -> {
                    val name =
                        libraries
                            .firstOrNull { it.itemId == row.parentId }
                            ?.name
                    val title =
                        name?.let {
                            context.getString(R.string.recently_released_in, it)
                        } ?: context.getString(R.string.recently_released)
                    val request =
                        GetItemsRequest(
                            parentId = row.parentId,
                            sortBy = listOf(ItemSortBy.PREMIERE_DATE),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            fields = homeRowItemFields,
                            recursive = true,
                        )
                    val (items, hasMore) =
                        fetchGetItemsPage(
                            request,
                            limit,
                            startIndex,
                            row.viewOptions.useSeries,
                        )
                    Success(
                        title,
                        items,
                        row.viewOptions,
                        rowType = row,
                        hasMore = hasMore,
                    )
                }

                is HomeRowConfig.ByParent -> {
                    val request =
                        GetItemsRequest(
                            userId = userDto.id,
                            parentId = row.parentId,
                            recursive = row.recursive,
                            sortBy = row.sort?.let { listOf(it.sort) },
                            sortOrder = row.sort?.let { listOf(it.direction) },
                            fields = homeRowItemFields,
                        )
                    val (items, hasMore) =
                        fetchGetItemsPage(
                            request,
                            limit,
                            startIndex,
                            row.viewOptions.useSeries,
                        )
                    val name =
                        api.userLibraryApi
                            .getItem(itemId = row.parentId)
                            .content.name
                    Success(
                        name ?: context.getString(R.string.collection),
                        items,
                        row.viewOptions,
                        rowType = row,
                        hasMore = hasMore,
                    )
                }

                is HomeRowConfig.GetItems -> {
                    val request =
                        row.getItems.let {
                            if (it.limit == null) {
                                it.copy(
                                    userId = userDto.id,
                                )
                            } else {
                                it.copy(
                                    userId = userDto.id,
                                )
                            }
                        }
                    val (items, hasMore) =
                        fetchGetItemsPage(
                            request,
                            limit,
                            startIndex,
                            row.viewOptions.useSeries,
                        )
                    Success(
                        row.name,
                        items,
                        row.viewOptions,
                        rowType = row,
                        hasMore = hasMore,
                    )
                }

                is HomeRowConfig.Favorite -> {
                    val title =
                        context.getString(
                            R.string.favorite_items,
                            context.getString(favoriteOptions[row.kind]!!),
                        )
                    if (row.kind == BaseItemKind.PERSON) {
                        val request =
                            GetPersonsRequest(
                                userId = userDto.id,
                                limit = limit,
                                fields = homeRowItemFields,
                                isFavorite = true,
                                enableImages = true,
                                enableImageTypes = listOf(ImageType.PRIMARY),
                            )
                        GetPersonsHandler
                            .execute(api, request)
                            .content.items
                            .map { BaseItem(it, true) }
                            .let {
                                Success(
                                    title,
                                    it,
                                    row.viewOptions,
                                )
                            }
                    } else {
                        val request =
                            GetItemsRequest(
                                userId = userDto.id,
                                recursive = true,
                                fields = homeRowItemFields,
                                includeItemTypes = listOf(row.kind),
                                isFavorite = true,
                            )
                        val (items, hasMore) =
                            fetchGetItemsPage(
                                request,
                                limit,
                                startIndex,
                                row.viewOptions.useSeries,
                            )
                        Success(
                            title,
                            items,
                            row.viewOptions,
                            rowType = row,
                            hasMore = hasMore,
                        )
                    }
                }

                is HomeRowConfig.Recordings -> {
                    val request =
                        GetRecordingsRequest(
                            userId = userDto.id,
                            isInProgress = true,
                            fields = homeRowItemFields,
                            limit = limit,
                            enableImages = true,
                            enableUserData = true,
                        )
                    api.liveTvApi
                        .getRecordings(request)
                        .content.items
                        .map { BaseItem(it, row.viewOptions.useSeries) }
                        .let {
                            Success(
                                context.getString(R.string.active_recordings),
                                it,
                                row.viewOptions,
                                rowType = row,
                            )
                        }
                }

                is HomeRowConfig.TvPrograms -> {
                    val request =
                        GetRecommendedProgramsRequest(
                            userId = userDto.id,
                            fields = homeRowItemFields,
                            limit = limit,
                            enableUserData = true,
                            enableImages = true,
                            enableImageTypes = listOf(ImageType.PRIMARY, ImageType.LOGO),
                            imageTypeLimit = 1,
                        )
                    api.liveTvApi
                        .getRecommendedPrograms(request)
                        .content.items
                        .map { BaseItem(it, row.viewOptions.useSeries) }
                        .let {
                            Success(
                                context.getString(R.string.live_tv),
                                it,
                                row.viewOptions,
                                rowType = row,
                            )
                        }
                }

                is HomeRowConfig.TvChannels -> {
                    api.liveTvApi
                        .getLiveTvChannels(
                            userId = userDto.id,
                            fields = homeRowItemFields,
                            limit = limit,
                            enableImages = true,
                        ).toBaseItems(api, row.viewOptions.useSeries)
                        .let {
                            Success(
                                context.getString(R.string.channels),
                                it,
                                row.viewOptions,
                                rowType = row,
                            )
                        }
                }

                is HomeRowConfig.Suggestions -> {
                    val library =
                        api.userLibraryApi
                            .getItem(itemId = row.parentId)
                            .content
                    val title = context.getString(R.string.suggestions_for, library.name ?: "")
                    val itemKind = SuggestionsWorker.getTypeForCollection(library.collectionType)
                    val suggestions =
                        itemKind?.let {
                            suggestionService
                                .getSuggestionsFlow(row.parentId, itemKind)
                                .firstOrNull()
                        }
                    if (suggestions != null && suggestions is SuggestionsResource.Success) {
                        Success(
                            title,
                            suggestions.items,
                            row.viewOptions,
                            rowType = row,
                        )
                    } else if (suggestions is SuggestionsResource.Empty) {
                        Success(
                            title,
                            listOf(),
                            row.viewOptions,
                            rowType = row,
                        )
                    } else {
                        Error(
                            title,
                            message = "Unsupported type ${library.collectionType}",
                        )
                    }
                }
            }

        private fun getCategoryTitle(category: HomeCategory): String =
            context.getString(
                when (category) {
                    HomeCategory.TOP_RATED_MOVIES -> R.string.top_rated_movies
                    HomeCategory.TOP_RATED_TV -> R.string.top_rated_tv
                    HomeCategory.POPULAR_MOVIES -> R.string.popular_movies
                    HomeCategory.POPULAR_TV -> R.string.popular_tv
                    HomeCategory.RECENTLY_ADDED -> R.string.recently_added
                    HomeCategory.RECENTLY_RELEASED -> R.string.recently_released
                    HomeCategory.RECENTLY_RELEASED_MOVIES -> R.string.recently_released_movies
                    HomeCategory.RECENTLY_RELEASED_TV -> R.string.recently_released_tv
                    HomeCategory.UNWATCHED_MOVIES -> R.string.unwatched_movies
                    HomeCategory.UNWATCHED_TV -> R.string.unwatched_tv
                },
            )

        private fun getSeasonalTitle(category: SeasonalCategory): String =
            context.getString(
                when (category) {
                    SeasonalCategory.HALLOWEEN -> R.string.halloween
                    SeasonalCategory.CHRISTMAS -> R.string.christmas
                },
            )

        private fun getSeasonalMonth(category: SeasonalCategory): String =
            context.getString(
                when (category) {
                    SeasonalCategory.HALLOWEEN -> R.string.october
                    SeasonalCategory.CHRISTMAS -> R.string.december
                },
            )

        private fun genreIncludeItemTypes(library: Library?): List<BaseItemKind> =
            when (library?.collectionType) {
                CollectionType.MOVIES -> listOf(BaseItemKind.MOVIE)
                CollectionType.TVSHOWS -> listOf(BaseItemKind.SERIES)
                else -> listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)
            }

        private suspend fun getGenreNamesForLibrary(
            userId: UUID,
            parentId: UUID,
            collectionType: CollectionType?,
        ): List<String> {
            val includeItemTypes =
                when (collectionType) {
                    CollectionType.MOVIES -> listOf(BaseItemKind.MOVIE)
                    CollectionType.TVSHOWS -> listOf(BaseItemKind.SERIES)
                    else -> null
                }
            val request =
                GetGenresRequest(
                    userId = userId,
                    parentId = parentId,
                    includeItemTypes = includeItemTypes,
                    sortBy = listOf(ItemSortBy.SORT_NAME),
                    sortOrder = listOf(SortOrder.ASCENDING),
                )
            return GetGenresRequestHandler
                .execute(api, request)
                .content.items
                .mapNotNull { it.name }
                .distinct()
        }

        companion object {
            const val CUSTOM_PREF_ID = "home_settings"
            private const val HOME_PAGE_CACHE_TTL_MS = 10 * 60 * 1000L
            private val homeRowItemFields = SlimItemFields
        }
    }

/**
 * A [HomeRowConfig] with a resolved ID and title so it is usable in the UI
 */
data class HomeRowConfigDisplay(
    val id: Int,
    val title: String,
    val config: HomeRowConfig,
)

data class CachedHomePage(
    val homeRows: List<HomeRowLoadingState>,
    val mediaBannerItems: List<BaseItem>,
)

/**
 * List of resolved [HomeRowConfig]s as [HomeRowConfigDisplay]s
 *
 * @see HomePageSettings
 */
data class HomePageResolvedSettings(
    val rows: List<HomeRowConfigDisplay>,
) {
    companion object {
        val EMPTY = HomePageResolvedSettings(listOf())
    }
}

// https://github.com/jellyfin/jellyfin/blob/v10.11.6/src/Jellyfin.Database/Jellyfin.Database.Implementations/Enums/HomeSectionType.cs
enum class HomeSectionType(
    val serialName: String,
) {
    NONE("none"),
    SMALL_LIBRARY_TILES("smalllibrarytitles"),
    LIBRARY_BUTTONS("librarybuttons"),
    ACTIVE_RECORDINGS("activerecordings"),
    RESUME("resume"),
    RESUME_AUDIO("resumeaudio"),
    LATEST_MEDIA("latestmedia"),
    NEXT_UP("nextup"),
    LIVE_TV("livetv"),
    RESUME_BOOK("resumebook"),
    ;

    companion object {
        fun fromString(homeKey: String?) = homeKey?.let { entries.firstOrNull { it.serialName == homeKey } }
    }
}

class UnsupportedHomeSettingsVersionException(
    val unsupportedVersion: Int?,
    val maxSupportedVersion: Int = SUPPORTED_HOME_PAGE_SETTINGS_VERSION,
) : Exception("Unsupported version $unsupportedVersion, max supported is $maxSupportedVersion")

fun getRecentlyAddedTitle(
    context: Context,
    library: Library?,
): String =
    if (library?.isRecordingFolder == true) {
        context.getString(R.string.recently_recorded)
    } else {
        library?.name?.let { context.getString(R.string.recently_added_in, it) }
            ?: context.getString(R.string.recently_added)
    }
