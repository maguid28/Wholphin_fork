package com.github.damontecres.wholphin.ui.playback

import android.content.Context
import android.media.MediaCodecList
import android.os.Build
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.ui.text.intl.Locale
import androidx.core.net.toUri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.session.MediaSession
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Size
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ItemPlaybackDao
import com.github.damontecres.wholphin.data.ItemPlaybackRepository
import com.github.damontecres.wholphin.data.LibraryTvWatchedEpisodeDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Chapter
import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.data.model.LibraryTvWatchedEpisode
import com.github.damontecres.wholphin.data.model.Playlist
import com.github.damontecres.wholphin.data.model.PlaylistItem
import com.github.damontecres.wholphin.data.model.TrackIndex
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.preferences.ShowNextUpWhen
import com.github.damontecres.wholphin.preferences.SkipSegmentBehavior
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.DatePlayedService
import com.github.damontecres.wholphin.services.DeviceProfileService
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.PlayerFactory
import com.github.damontecres.wholphin.services.PlaylistCreationResult
import com.github.damontecres.wholphin.services.PlaylistCreator
import com.github.damontecres.wholphin.services.RefreshRateService
import com.github.damontecres.wholphin.services.ScreensaverService
import com.github.damontecres.wholphin.services.StreamChoiceService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.services.hilt.AuthOkHttpClient
import com.github.damontecres.wholphin.ui.detail.librarytv.LibraryTvChannel
import com.github.damontecres.wholphin.ui.detail.librarytv.LibraryTvGuideMemoryCache
import com.github.damontecres.wholphin.ui.detail.librarytv.LibraryTvGuideService
import com.github.damontecres.wholphin.ui.detail.librarytv.LibraryTvProgram
import com.github.damontecres.wholphin.ui.detail.librarytv.withEnabledChannels
import com.github.damontecres.wholphin.ui.formatBitrate
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.onMain
import com.github.damontecres.wholphin.ui.seekBack
import com.github.damontecres.wholphin.ui.seekForward
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.ui.toServerString
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.PlaybackItemState
import com.github.damontecres.wholphin.util.TrackActivityPlaybackListener
import com.github.damontecres.wholphin.util.checkForSupport
import com.github.damontecres.wholphin.util.mpv.mpvDeviceProfile
import com.github.damontecres.wholphin.util.profile.Codec
import com.github.damontecres.wholphin.util.mpv.MpvPlayer
import com.github.damontecres.wholphin.util.subtitleMimeTypes
import org.jellyfin.sdk.api.client.extensions.subtitleApi
import org.jellyfin.sdk.model.api.MediaStream
import com.github.damontecres.wholphin.util.supportItemKinds
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.peerless2012.ass.media.AssHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.mediaSegmentsApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.trickplayApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.api.sockets.subscribe
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaystateCommand
import org.jellyfin.sdk.model.api.PlaystateMessage
import org.jellyfin.sdk.model.api.TrickplayInfo
import org.jellyfin.sdk.model.api.VideoRange
import org.jellyfin.sdk.model.api.VideoRangeType
import org.jellyfin.sdk.model.extensions.inWholeTicks
import org.jellyfin.sdk.model.extensions.ticks
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.time.Instant
import java.util.Date
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * This [ViewModel] is responsible for playing media including moving through playlists (including next up episodes)
 */
data class LibraryTvPlaybackInfo(
    val channelKey: String,
    val channelNumber: Int,
    val channelName: String,
    val title: String,
    val subtitle: String?,
    val overview: String?,
    val start: Instant,
    val end: Instant,
)

private val LibraryTvWatchedProgressInterval = 5.seconds
private const val LibraryTvChannelTuneDebounceMs = 180L

private data class LibraryTvWatchSample(
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
)

@HiltViewModel(assistedFactory = PlaybackViewModel.Factory::class)
@OptIn(markerClass = [UnstableApi::class])
class PlaybackViewModel
    @AssistedInject
    constructor(
        @param:ApplicationContext internal val context: Context,
        internal val api: ApiClient,
        val navigationManager: NavigationManager,
        private val playlistCreator: PlaylistCreator,
        private val itemPlaybackDao: ItemPlaybackDao,
        private val libraryTvWatchedEpisodeDao: LibraryTvWatchedEpisodeDao,
        private val serverRepository: ServerRepository,
        private val itemPlaybackRepository: ItemPlaybackRepository,
        private val playerFactory: PlayerFactory,
        private val datePlayedService: DatePlayedService,
        private val deviceInfo: DeviceInfo,
        private val deviceProfileService: DeviceProfileService,
        private val refreshRateService: RefreshRateService,
        val streamChoiceService: StreamChoiceService,
        private val userPreferencesService: UserPreferencesService,
        private val imageUrlService: ImageUrlService,
        private val screensaverService: ScreensaverService,
        private val musicService: MusicService,
        private val libraryTvGuideService: LibraryTvGuideService,
        @AuthOkHttpClient private val authOkHttpClient: OkHttpClient,
        @Assisted private val destination: Destination,
    ) : ViewModel(),
        Player.Listener,
        AnalyticsListener {
        @AssistedFactory
        interface Factory {
            fun create(destination: Destination): PlaybackViewModel
        }

        val currentPlayer = MutableStateFlow<PlayerState?>(null)

        internal lateinit var player: Player

        private var mediaSession: MediaSession? = null
        internal val mutex = Mutex()

        val controllerViewState =
            ControllerViewState(
                AppPreference.ControllerTimeout.defaultValue,
                true,
            )

        val loading = MutableLiveData<LoadingState>(LoadingState.Loading)

        val currentMediaInfo = MutableLiveData<CurrentMediaInfo>(CurrentMediaInfo.EMPTY)
        val currentPlayback = MutableStateFlow<CurrentPlayback?>(null)
        val currentItemPlayback = MutableLiveData<ItemPlayback>()
        val currentSegment = MutableStateFlow<MediaSegmentState?>(null)
        val analyticsState = MutableStateFlow(AnalyticsState())

        val subtitleCues = MutableLiveData<List<Cue>>(listOf())
        val secondarySubtitleCues = MutableLiveData<List<Cue>>(listOf())
        val secondarySubtitlesActive = MutableLiveData(false)

        private var loadedSecondaryCues: List<TimedSubtitleCue> = emptyList()

        private lateinit var preferences: UserPreferences
        internal lateinit var itemId: UUID
        internal lateinit var currentItem: PlaylistItem
        internal var forceTranscoding: Boolean = false
        private var activityListener: TrackActivityPlaybackListener? = null
        private var libraryTvWatchedJob: Job? = null
        private val markedLibraryTvEpisodeIds = mutableSetOf<UUID>()
        private val jobs = mutableListOf<Job>()

        val nextUp = MutableLiveData<BaseItem?>()
        private val isPlaylist = destination is Destination.PlaybackList
        private val trackPlayback = (destination as? Destination.Playback)?.trackPlayback ?: true
        private val initialLibraryTvChannelKey = (destination as? Destination.Playback)?.libraryTvChannelKey
        private val isLibraryTvPlayback = initialLibraryTvChannelKey != null
        private var appliedLibraryTvDestinationEntryId = (destination as? Destination.Playback)?.entryId
        private var currentLibraryTvChannelKey = initialLibraryTvChannelKey
        private var currentLibraryTvProgram: LibraryTvProgram? = null
        private var pendingLibraryTvChannelIndex: Int? = null
        private var libraryTvTuneJob: Job? = null
        private var playbackSession = 0

        val playlist = MutableLiveData<Playlist>(Playlist(listOf()))
        val libraryTvPlayback = MutableStateFlow<LibraryTvPlaybackInfo?>(null)
        val subtitleSearchStatus = MutableLiveData<SubtitleSearchStatus?>(null)
        val subtitleSearchLanguage = MutableLiveData<String>(Locale.current.language)

        val currentUserDto = serverRepository.currentUserDto

        init {
            viewModelScope.launchIO {
                addCloseable {
                    screensaverService.keepScreenOn(false)
                    disconnectPlayer()
                }
                init()
            }
        }

        private fun disconnectPlayer() {
            if (this@PlaybackViewModel::player.isInitialized) {
                player.removeListener(this@PlaybackViewModel)
                (player as? ExoPlayer)?.removeAnalyticsListener(this@PlaybackViewModel)

                this@PlaybackViewModel.activityListener?.let {
                    it.release()
                    player.removeListener(it)
                }
                player.release()
                mediaSession?.release()
                mediaSession = null
            }
            libraryTvTuneJob?.cancel()
            libraryTvTuneJob = null
            pendingLibraryTvChannelIndex = null
            libraryTvWatchedJob?.cancel()
            libraryTvWatchedJob = null
            jobs.forEach { it.cancel() }
            jobs.clear()
        }

        private suspend fun createPlayer(
            isHdr: Boolean,
            is4k: Boolean,
        ) {
            val softwareDecoding =
                !preferences.appPreferences.playbackPreferences.mpvOptions.enableHardwareDecoding
            val requestedBackend =
                (destination as? Destination.Playback)?.backend
                    ?: preferences.appPreferences.playbackPreferences.playerBackend
            val playerBackend =
                when (requestedBackend) {
                    PlayerBackend.UNRECOGNIZED,
                    PlayerBackend.EXO_PLAYER,
                    -> PlayerBackend.EXO_PLAYER

                    PlayerBackend.MPV -> PlayerBackend.MPV

                    PlayerBackend.PREFER_MPV -> if (isHdr || (is4k && softwareDecoding)) PlayerBackend.EXO_PLAYER else PlayerBackend.MPV

                    PlayerBackend.EXTERNAL_PLAYER -> throw IllegalStateException("Cannot use this for external playback")
                }

            Timber.d("Selected backend: %s", playerBackend)
            if (currentPlayer.value?.backend != playerBackend) {
                Timber.i("Switching player backend to %s", playerBackend)
                withContext(Dispatchers.Main) {
                    disconnectPlayer()
                }

                val playerCreation =
                    playerFactory.createVideoPlayer(
                        playerBackend,
                        preferences.appPreferences.playbackPreferences,
                    )
                this.player = playerCreation.player
                currentPlayer.update {
                    PlayerState(playerCreation.player, playerBackend, playerCreation.assHandler)
                }
                configurePlayer()
            }
        }

        private fun configurePlayer() {
            player.addListener(this)
            (player as? ExoPlayer)?.addAnalyticsListener(this)
            jobs.add(subscribe())
            jobs.add(listenForTranscodeReason())
            val sessionPlayer =
                MediaSessionPlayer(
                    player,
                    preferences.appPreferences.playbackPreferences,
                )
            mediaSession =
                MediaSession
                    .Builder(context, sessionPlayer)
                    .build()
        }

        /**
         * Initialize from the UI to start playback
         */
        private suspend fun init() {
            musicService.stop()
            screensaverService.keepScreenOn(true)
            nextUp.setValueOnMain(null)
            this.preferences = userPreferencesService.getCurrent()
            if (preferences.appPreferences.playbackPreferences.refreshRateSwitching) {
                addCloseable { refreshRateService.resetRefreshRate() }
            }
            controllerViewState.hideMilliseconds =
                preferences.appPreferences.playbackPreferences.controllerTimeoutMs
            this.forceTranscoding =
                (destination as? Destination.Playback)?.forceTranscoding ?: false
            val positionMs: Long
            val itemPlayback: ItemPlayback?
            val forceTranscoding: Boolean

            val itemId =
                when (val d = destination) {
                    is Destination.Playback -> {
                        positionMs = d.positionMs
                        itemPlayback = d.itemPlayback
                        forceTranscoding = d.forceTranscoding
                        d.itemId
                    }

                    is Destination.PlaybackList -> {
                        positionMs = 0
                        itemPlayback = null
                        forceTranscoding = false
                        d.itemId
                    }

                    else -> {
                        throw IllegalArgumentException("Destination not supported: $destination")
                    }
                }
            this.itemId = itemId
            val queriedItem = api.userLibraryApi.getItem(itemId).content
            val playlistItem =
                if (queriedItem.type.playable) {
                    PlaylistItem.Media(BaseItem(queriedItem, false))
                } else if (destination is Destination.PlaybackList) {
                    val playlistResult =
                        playlistCreator.createFrom(
                            item = queriedItem,
                            startIndex = destination.startIndex ?: 0,
                            sortAndDirection = destination.sortAndDirection,
                            shuffled = destination.shuffle,
                            recursive = destination.recursive,
                            filter = destination.filter,
                        )
                    when (val r = playlistResult) {
                        is PlaylistCreationResult.Error -> {
                            loading.setValueOnMain(LoadingState.Error(r.message, r.ex))
                            return
                        }

                        is PlaylistCreationResult.Success -> {
                            if (r.playlist.items.isEmpty()) {
                                showToast(context, "Playlist is empty", Toast.LENGTH_SHORT)
                                navigationManager.goBack()
                                return
                            }
                            if (preferences.appPreferences.playbackPreferences.showNextUpWhen != ShowNextUpWhen.NEXT_UP_NEVER) {
                                withContext(Dispatchers.Main) {
                                    this@PlaybackViewModel.playlist.value = r.playlist
                                }
                            }
                            r.playlist.items.first()
                        }
                    }
                } else {
                    throw IllegalArgumentException("Item is not playable and not PlaybackList: ${queriedItem.type}")
                }

            viewModelScope.launch(ExceptionHandler()) { controllerViewState.observe() }

            val intros =
                // If not resuming playback & cinema mode is enabled, get potential intros
                if (positionMs == 0L && preferences.appPreferences.playbackPreferences.cinemaMode) {
                    api.userLibraryApi
                        .getIntros(
                            itemId = playlistItem.id,
                            userId = serverRepository.currentUser.value?.id,
                        ).content.items
                        .map {
                            PlaylistItem.Intro(BaseItem(it))
                        }
                } else {
                    emptyList()
                }
            val firstItem =
                if (intros.isNotEmpty()) {
                    Timber.v("Got %s intros", intros.size)
                    val currentPlaylist =
                        this@PlaybackViewModel
                            .playlist.value
                            ?.items
                            .orEmpty()
                    val newPlaylist = Playlist(intros + currentPlaylist)
                    this@PlaybackViewModel.playlist.setValueOnMain(newPlaylist)
                    intros.first()
                } else {
                    playlistItem
                }

            val played =
                play(
                    firstItem,
                    positionMs,
                    itemPlayback,
                    forceTranscoding,
                )
            if (!played) {
                playNextUp()
            }
            updateLibraryTvPlaybackState(currentLibraryTvChannelKey)

            if (!isPlaylist && !isLibraryTvPlayback && preferences.appPreferences.playbackPreferences.showNextUpWhen != ShowNextUpWhen.NEXT_UP_NEVER) {
                val result = playlistCreator.createFrom(queriedItem)
                if (result is PlaylistCreationResult.Success && result.playlist.items.isNotEmpty()) {
                    val currentPlaylist =
                        this@PlaybackViewModel
                            .playlist.value
                            ?.items
                            .orEmpty()
                    val newPlaylist = Playlist(currentPlaylist + result.playlist.items)
                    this@PlaybackViewModel.playlist.setValueOnMain(newPlaylist)
                }
            }
        }

        /**
         * Play an item
         *
         * @param currentItem the item to play
         * @param positionMs the starting playback position in milliseconds
         * @param itemPlayback the parameters for playback such chosen subtitle or audio streams
         * @param forceTranscoding whether the user has requested to force playback via transcoding
         */
        private suspend fun play(
            playlistItem: PlaylistItem,
            positionMs: Long,
            itemPlayback: ItemPlayback? = null,
            forceTranscoding: Boolean = this.forceTranscoding,
        ): Boolean =
            withContext(Dispatchers.IO) {
                val item =
                    when (playlistItem) {
                        is PlaylistItem.Intro -> playlistItem.item
                        is PlaylistItem.Media -> playlistItem.item
                    }

                Timber.i("Playing ${item.id}")

                // New item, so we can clear the media segment tracker & subtitle cues
                resetSegmentState()
                this@PlaybackViewModel.subtitleCues.setValueOnMain(listOf())
                clearSecondarySubtitles()

                viewModelScope.launchIO {
                    // Starting playback, so want to invalidate the last played timestamp for this item
                    datePlayedService.invalidate(item)
                }

                if (item.type !in supportItemKinds) {
                    showToast(
                        context,
                        "Unsupported type '${item.type}', skipping...",
                        Toast.LENGTH_SHORT,
                    )
                    return@withContext false
                }
                this@PlaybackViewModel.currentItem = playlistItem
                this@PlaybackViewModel.itemId = item.id

                val isLiveTv = item.type == BaseItemKind.TV_CHANNEL
                val base = item.data

                // Use the provided playback parameters or else check if the database has some
                val playbackConfig =
                    itemPlayback
                        ?: serverRepository.currentUser.value?.let { user ->
                            itemPlaybackDao.getItem(user, base.id)?.let {
                                Timber.v("Fetched itemPlayback from DB: %s", it)
                                if (it.sourceId != null) {
                                    it
                                } else {
                                    null
                                }
                            }
                        }
                val mediaSource = streamChoiceService.chooseSource(base, playbackConfig)
                val plc = streamChoiceService.getPlaybackLanguageChoice(base)

                if (mediaSource == null) {
                    showToast(
                        context,
                        "Item has no media sources, skipping...",
                        Toast.LENGTH_SHORT,
                    )
                    return@withContext false
                }

                val videoStream =
                    mediaSource.mediaStreams
                        ?.firstOrNull { it.type == MediaStreamType.VIDEO }
                        ?.let {
                            val isHdr =
                                it.videoRange == VideoRange.HDR ||
                                    (it.videoRangeType != VideoRangeType.SDR && it.videoRangeType != VideoRangeType.UNKNOWN)
                            // Often times 4k movies have a wider aspect ratio so the height is lower even though the width is still 3840
                            val is4k = (it.width ?: 0) > 2560 || (it.height ?: 0) > 1440
                            SimpleVideoStream(it.index, isHdr, is4k)
                        }

                // Create the correct player for the media
                createPlayer(videoStream?.hdr == true, videoStream?.is4k == true)
                val subtitleLanguagePreference =
                    serverRepository.currentUserDto.value
                        ?.configuration
                        ?.subtitleLanguagePreference
                val subtitleStreams =
                    mediaSource.mediaStreams
                        ?.filter { it.type == MediaStreamType.SUBTITLE }
                        .let {
                            if (subtitleLanguagePreference.isNotNullOrBlank()) {
                                it?.sortedByDescending { it.language != null && subtitleLanguagePreference == it.language }
                            } else {
                                it
                            }
                        }?.map {
                            SimpleMediaStream.from(context, it, true)
                        }.orEmpty()

                val audioStreams =
                    mediaSource.mediaStreams
                        ?.filter { it.type == MediaStreamType.AUDIO }
                        ?.map {
                            SimpleMediaStream.from(context, it, true)
                        }
//                        ?.sortedWith(compareBy<AudioStream> { it.language }.thenByDescending { it.channels })
                        .orEmpty()
                val audioStream =
                    streamChoiceService
                        .chooseAudioStream(
                            source = mediaSource,
                            seriesId = base.seriesId,
                            itemPlayback = playbackConfig,
                            plc = plc,
                            prefs = preferences,
                        )
                val audioIndex = audioStream?.index

                val subtitleIndex =
                    streamChoiceService
                        .chooseSubtitleStream(
                            source = mediaSource,
                            audioStream = audioStream,
                            seriesId = base.seriesId,
                            itemPlayback = playbackConfig,
                            plc = plc,
                            prefs = preferences,
                        )?.index

                Timber.d("Selected mediaSource=${mediaSource.id}, audioIndex=$audioIndex, subtitleIndex=$subtitleIndex")

                val itemPlaybackToUse =
                    playbackConfig ?: ItemPlayback(
                        rowId = -1,
                        userId = -1,
                        itemId = base.id,
                        sourceId = if (!isLiveTv) mediaSource.id?.toUUIDOrNull() else null,
                        audioIndex = audioIndex ?: TrackIndex.UNSPECIFIED,
                        subtitleIndex = subtitleIndex ?: TrackIndex.UNSPECIFIED,
                    )
                val trickPlayInfo =
                    item.data.trickplay
                        ?.get(mediaSource.id)
                        ?.values
                        ?.firstOrNull()
                trickPlayInfo?.let { trickplayInfo ->
                    mediaSource.runTimeTicks?.ticks?.let { duration ->
                        viewModelScope.launchIO {
                            prefetchTrickplay(
                                duration,
                                trickplayInfo,
                                mediaSource.id?.toUUIDOrNull(),
                            )
                        }
                    }
                }

                val chapters = Chapter.fromDto(base, api)
                withContext(Dispatchers.Main) {
                    this@PlaybackViewModel.currentItemPlayback.value = itemPlaybackToUse
                    updateCurrentMedia {
                        CurrentMediaInfo(
                            sourceId = mediaSource.id,
                            videoStream = videoStream,
                            audioStreams = audioStreams,
                            subtitleStreams = subtitleStreams,
                            chapters = chapters,
                            trickPlayInfo = trickPlayInfo,
                        )
                    }

                    changeStreams(
                        item,
                        itemPlaybackToUse,
                        audioIndex,
                        subtitleIndex,
                        if (isLibraryTvPlayback) positionMs else if (positionMs > 0) positionMs else C.TIME_UNSET,
                        itemPlayback != null, // If it was passed in, then it was not queried from the database
                        enableDirectPlay = !forceTranscoding,
                        enableDirectStream = !forceTranscoding,
                    )
                    player.prepare()
                    player.play()
                }
                listenForSegments(
                    itemId = item.id,
                    suppressIntroOutroSegments = isLiveTv,
                )
                startLibraryTvWatchedTracking(item)
                return@withContext true
            }

        /**
         * Change which streams (ie audio or subtitle) are active
         */
        @OptIn(UnstableApi::class)
        internal suspend fun changeStreams(
            item: BaseItem,
            currentItemPlayback: ItemPlayback = this@PlaybackViewModel.currentItemPlayback.value!!,
            audioIndex: Int?,
            subtitleIndex: Int?,
            positionMs: Long = 0,
            userInitiated: Boolean,
            enableDirectPlay: Boolean = !this.forceTranscoding,
            enableDirectStream: Boolean = !this.forceTranscoding,
        ) = withContext(Dispatchers.IO) {
            val session = playbackSession
            val itemId = item.id

            val currentPlayback = this@PlaybackViewModel.currentPlayback.value
            if (
                enableDirectPlay &&
                currentPlayback != null &&
                currentPlayback.item.id == item.id &&
                currentPlayback.playMethod == PlayMethod.DIRECT_PLAY
            ) {
                val wasSuccessful =
                    changeStreamsDirectPlay(
                        currentPlayback = currentPlayback,
                        currentItemPlayback = currentItemPlayback,
                        audioIndex = audioIndex,
                        subtitleIndex = subtitleIndex,
                        userInitiated = userInitiated,
                    )
                if (wasSuccessful) {
                    if (isLibraryTvPlayback && positionMs != C.TIME_UNSET) {
                        withContext(Dispatchers.Main) {
                            player.seekTo(positionMs)
                            player.play()
                        }
                    }
                    return@withContext
                }
            }

            // Let ExoPlayer choose startup audio unless the user or a saved preference chose one.
            val forceLocalAudioSelection =
                userInitiated || (currentItemPlayback.rowId >= 0 && currentItemPlayback.audioIndexEnabled)

            Timber.d(
                "changeStreams: userInitiated=$userInitiated, audioIndex=$audioIndex, subtitleIndex=$subtitleIndex, " +
                    "enableDirectPlay=$enableDirectPlay, enableDirectStream=$enableDirectStream, positionMs=$positionMs",
            )

            val maxBitrate =
                preferences.appPreferences.playbackPreferences.maxBitrate
                    .takeIf { it > 0 } ?: AppPreference.DEFAULT_BITRATE
            val response by
                api.mediaInfoApi
                    .getPostedPlaybackInfo(
                        itemId,
                        PlaybackInfoDto(
                            startTimeTicks = null,
                            deviceProfile =
                                if (currentPlayer.value!!.backend == PlayerBackend.EXO_PLAYER) {
                                    deviceProfileService.getOrCreateDeviceProfile(
                                        preferences.appPreferences.playbackPreferences,
                                        serverRepository.currentServer.value?.serverVersion,
                                    )
                                } else {
                                    mpvDeviceProfile
                                },
                            maxAudioChannels = null,
                            audioStreamIndex = audioIndex,
                            subtitleStreamIndex = subtitleIndex,
                            mediaSourceId = currentItemPlayback.sourceId?.toServerString(),
                            alwaysBurnInSubtitleWhenTranscoding = false,
                            maxStreamingBitrate = maxBitrate.toInt(),
                            enableDirectPlay = enableDirectPlay,
                            enableDirectStream = enableDirectStream,
                            allowVideoStreamCopy = enableDirectStream,
                            allowAudioStreamCopy = enableDirectStream,
                            enableTranscoding = true,
                            autoOpenLiveStream = true,
                        ),
                    )
            if (response.errorCode != null) {
                loading.setValueOnMain(LoadingState.Error(response.errorCode?.serialName))
                return@withContext
            }
            val source = response.mediaSources.firstOrNull()
            source?.let { source ->
                val mediaUrl =
                    if (source.supportsDirectPlay) {
                        if (source.isRemote && source.path.isNotNullOrBlank()) {
                            Timber.i("Playback is remote for source: %s", source.id)
                            source.path
                        } else {
                            api.videosApi.getVideoStreamUrl(
                                itemId = itemId,
                                mediaSourceId = source.id,
                                static = true,
                                tag = source.eTag,
                                playSessionId = response.playSessionId,
                            )
                        }
                    } else if (source.supportsDirectStream) {
                        source.transcodingUrl?.let(api::createUrl)
                    } else {
                        source.transcodingUrl?.let(api::createUrl)
                    }
                if (mediaUrl.isNullOrBlank()) {
                    loading.setValueOnMain(
                        LoadingState.Error("Unable to get media URL from the server. Do you have permission to view and/or transcode?"),
                    )
                    return@withContext
                }
                val transcodeType =
                    when {
//                        playerBackend == PlayerBackend.MPV -> PlayMethod.DIRECT_PLAY
                        source.supportsDirectPlay -> PlayMethod.DIRECT_PLAY

                        source.supportsDirectStream -> PlayMethod.DIRECT_STREAM

                        source.supportsTranscoding -> PlayMethod.TRANSCODE

                        else -> throw Exception("No supported playback method")
                    }
                Timber.i("Playback decision for $itemId: $transcodeType")

                val externalSubtitleCount = source.externalSubtitlesCount

                val externalSubtitle =
                    source.findExternalSubtitle(subtitleIndex)?.let {
                        it.deliveryUrl?.let { deliveryUrl ->
                            var flags = 0
                            if (it.isForced) flags = flags.or(C.SELECTION_FLAG_FORCED)
                            if (it.isDefault) flags = flags.or(C.SELECTION_FLAG_DEFAULT)
                            MediaItem.SubtitleConfiguration
                                .Builder(
                                    api.createUrl(deliveryUrl).toUri(),
                                ).setId("e:${it.index}")
                                .setMimeType(subtitleMimeTypes[it.codec])
                                .setLanguage(it.language)
                                .setLabel(it.title)
                                .setSelectionFlags(flags)
                                .build()
                        }
                    }

                val secondarySubtitle =
                    if (preferences.appPreferences.playbackPreferences.enableDualSubtitles) {
                        val secondaryIndex = currentItemPlayback.secondarySubtitleIndex
                        if (secondaryIndex >= 0 && secondaryIndex != subtitleIndex) {
                            source.mediaStreams
                                ?.firstOrNull { it.index == secondaryIndex && it.type == MediaStreamType.SUBTITLE }
                                ?.takeUnless { isImageSubtitleStream(it) }
                                ?.let { stream ->
                                    source.id?.let { mediaSourceId ->
                                        buildSecondarySubtitleConfiguration(itemId, mediaSourceId, stream)
                                    }
                                }
                        } else {
                            null
                        }
                    } else {
                        null
                    }

                Timber.v("subtitleIndex=$subtitleIndex, externalSubtitleCount=$externalSubtitleCount, externalSubtitle=$externalSubtitle")

                val mediaItem =
                    MediaItem
                        .Builder()
                        .setMediaId(itemId.toString())
                        .setMediaMetadata(
                            item.toMediaMetadata(
                                imageUrlService.getItemImageUrl(
                                    item,
                                    ImageType.PRIMARY,
                                    useSeriesForPrimary = true,
                                ),
                            ),
                        ).setUri(mediaUrl.toUri())
                        .setSubtitleConfigurations(listOfNotNull(externalSubtitle, secondarySubtitle))
                        .apply {
                            when (source.container) {
                                Codec.Container.HLS -> setMimeType(MimeTypes.APPLICATION_M3U8)
                                Codec.Container.DASH -> setMimeType(MimeTypes.APPLICATION_MPD)
                            }
                        }.build()

                val playback =
                    CurrentPlayback(
                        item = item,
                        tracks = listOf(),
                        backend = currentPlayer.value!!.backend,
                        playMethod = transcodeType,
                        playSessionId = response.playSessionId,
                        liveStreamId = source.liveStreamId,
                        mediaSourceInfo = source,
                    )

                preferences.appPreferences.playbackPreferences.let { prefs ->
                    source.mediaStreams
                        ?.firstOrNull { it.type == MediaStreamType.VIDEO }
                        ?.let { stream ->
                            refreshRateService.changeRefreshRate(
                                stream = stream,
                                switchRefreshRate = prefs.refreshRateSwitching,
                                switchResolution = prefs.resolutionSwitching,
                            )
                        }
                }
                withContext(Dispatchers.Main) {
                    if (session != playbackSession || currentPlayer.value == null) {
                        return@withContext
                    }
                    // TODO, don't need to release & recreate when switching streams
                    this@PlaybackViewModel.activityListener?.let {
                        it.release()
                        player.removeListener(it)
                    }

                    if (trackPlayback) {
                        val playbackItemState = PlaybackItemState(playback, currentItemPlayback)
                        val activityListener =
                            TrackActivityPlaybackListener(
                                api = api,
                                player = player,
                                getState = { playbackItemState },
                            )
                        player.addListener(activityListener)
                        this@PlaybackViewModel.activityListener = activityListener
                    } else {
                        this@PlaybackViewModel.activityListener = null
                    }

                    loading.value = LoadingState.Success
                    this@PlaybackViewModel.currentPlayback.update { playback }
                    player.setMediaItem(
                        mediaItem,
                        positionMs,
                    )
                    if (audioIndex != null || subtitleIndex != null) {
                        val onTracksChangedListener =
                            object : Player.Listener {
                                override fun onTracksChanged(tracks: Tracks) {
                                    Timber.v("onTracksChanged: $tracks")
                                    if (tracks.groups.isNotEmpty()) {
                                        val result =
                                            TrackSelectionUtils.createTrackSelections(
                                                player.trackSelectionParameters,
                                                player.currentTracks,
                                                currentPlayer.value!!.backend,
                                                source.supportsDirectPlay,
                                                audioIndex.takeIf {
                                                    transcodeType == PlayMethod.DIRECT_PLAY &&
                                                        forceLocalAudioSelection
                                                },
                                                subtitleIndex,
                                                source,
                                            )
                                        if (result.bothSelected) {
                                            player.trackSelectionParameters =
                                                result.trackSelectionParameters
                                            player.removeListener(this)
                                        }
                                        viewModelScope.launchIO { loadSubtitleDelay() }
                                    }
                                }
                            }
                        player.addListener(onTracksChangedListener)
                    }
                }
            }
        }

        /**
         * If direct playing, can try to switch tracks without playback restarting
         * Except for external subtitles
         */
        @OptIn(UnstableApi::class)
        private suspend fun changeStreamsDirectPlay(
            currentPlayback: CurrentPlayback,
            currentItemPlayback: ItemPlayback,
            audioIndex: Int?,
            subtitleIndex: Int?,
            userInitiated: Boolean,
        ): Boolean =
            withContext(Dispatchers.IO) {
                // TODO there's probably no reason why we can't add external subtitles?
                Timber.v("changeStreams direct play")

                val source = currentPlayback.mediaSourceInfo
                val externalSubtitle = source.findExternalSubtitle(subtitleIndex)

                if (externalSubtitle == null) {
                    val result =
                        withContext(Dispatchers.Main) {
                            TrackSelectionUtils.createTrackSelections(
                                onMain { player.trackSelectionParameters },
                                onMain { player.currentTracks },
                                currentPlayer.value!!.backend,
                                true,
                                audioIndex,
                                subtitleIndex,
                                source,
                            )
                        }
                    if (result.bothSelected) {
                        onMain { player.trackSelectionParameters = result.trackSelectionParameters }
                        // TODO lots of duplicate code in this block
                        Timber.d("Changes tracks audio=$audioIndex, subtitle=$subtitleIndex")
                        val itemPlayback =
                            currentItemPlayback.copy(
                                sourceId = source.id?.toUUIDOrNull(),
                                audioIndex = audioIndex ?: TrackIndex.UNSPECIFIED,
                                // Preserve special constants (ONLY_FORCED, DISABLED) instead of resolved index
                                subtitleIndex =
                                    if (currentItemPlayback.subtitleIndex < 0) {
                                        currentItemPlayback.subtitleIndex
                                    } else {
                                        subtitleIndex ?: TrackIndex.DISABLED
                                    },
                            )
                        if (userInitiated) {
                            viewModelScope.launchIO {
                                Timber.v("Saving user initiated item playback: %s", itemPlayback)
                                val updated = itemPlaybackRepository.saveItemPlayback(itemPlayback)
                                withContext(Dispatchers.Main) {
                                    this@PlaybackViewModel.currentItemPlayback.value = updated
                                }
                            }
                        }
                        withContext(Dispatchers.Main) {
                            this@PlaybackViewModel.currentPlayback.update {
                                (it ?: currentPlayback).copy(
                                    tracks = checkForSupport(player.currentTracks),
                                )
                            }

                            this@PlaybackViewModel.currentItemPlayback.value = itemPlayback
                        }
                        loadSubtitleDelay()
                        return@withContext true
                    }
                } else {
                    Timber.v("changeStreams direct play, external subtitle was requested")
                }
                return@withContext false
            }

        fun changeAudioStream(index: Int) {
            viewModelScope.launchIO {
                Timber.d("Changing audio track to %s", index)
                val itemPlayback =
                    itemPlaybackRepository.saveTrackSelection(
                        item = currentItem.item,
                        itemPlayback = currentItemPlayback.value!!,
                        trackIndex = index,
                        type = MediaStreamType.AUDIO,
                    )
                this@PlaybackViewModel.currentItemPlayback.setValueOnMain(itemPlayback)

                // Resolve ONLY_FORCED to actual track based on new audio language
                val source = currentPlayback.value?.mediaSourceInfo
                val resolvedSubtitleIndex =
                    if (source != null) {
                        streamChoiceService.resolveSubtitleIndex(
                            source = source,
                            audioStreamIndex = index,
                            seriesId = currentItem.item.data.seriesId,
                            subtitleIndex = itemPlayback.subtitleIndex,
                            prefs = preferences,
                        )
                    } else {
                        itemPlayback.subtitleIndex.takeIf { it >= 0 }
                    }

                changeStreams(
                    currentItem.item,
                    itemPlayback,
                    index,
                    resolvedSubtitleIndex,
                    onMain { player.currentPosition },
                    true,
                )
            }
        }

        fun changeSubtitleStream(index: Int): Job =
            viewModelScope.launchIO {
                Timber.d("Changing subtitle track to %s", index)
                val currentPlayback = currentItemPlayback.value!!
                val secondaryIndex =
                    if (index < 0 || index == currentPlayback.secondarySubtitleIndex) {
                        TrackIndex.DISABLED
                    } else {
                        currentPlayback.secondarySubtitleIndex
                    }
                val itemPlayback =
                    itemPlaybackRepository.saveSecondarySubtitleSelection(
                        item = currentItem.item,
                        itemPlayback =
                            itemPlaybackRepository.saveTrackSelection(
                                item = currentItem.item,
                                itemPlayback = currentPlayback,
                                trackIndex = index,
                                type = MediaStreamType.SUBTITLE,
                            ),
                        trackIndex = secondaryIndex,
                    )
                this@PlaybackViewModel.currentItemPlayback.setValueOnMain(itemPlayback)

                // Resolve ONLY_FORCED to actual track index for playback
                val source = this@PlaybackViewModel.currentPlayback.value?.mediaSourceInfo
                val resolvedIndex =
                    if (source != null) {
                        streamChoiceService.resolveSubtitleIndex(
                            source = source,
                            audioStreamIndex = itemPlayback.audioIndex,
                            seriesId = currentItem.item.data.seriesId,
                            subtitleIndex = index,
                            prefs = preferences,
                        )
                    } else {
                        index.takeIf { it >= 0 }
                    }

                changeStreams(
                    currentItem.item,
                    itemPlayback,
                    itemPlayback.audioIndex,
                    resolvedIndex,
                    onMain { player.currentPosition },
                    true,
                )
            }

        fun changeSecondarySubtitleStream(index: Int): Job =
            viewModelScope.launchIO {
                Timber.d("Changing secondary subtitle track to %s", index)
                val itemPlayback =
                    itemPlaybackRepository.saveSecondarySubtitleSelection(
                        item = currentItem.item,
                        itemPlayback = currentItemPlayback.value!!,
                        trackIndex = index,
                    )
                this@PlaybackViewModel.currentItemPlayback.setValueOnMain(itemPlayback)
                applySecondarySubtitle(itemPlayback)
            }

        fun updateSecondaryCuePosition(positionMs: Long) {
            if (loadedSecondaryCues.isEmpty()) return
            secondarySubtitleCues.postValue(SecondarySubtitleParser.cuesAtPosition(loadedSecondaryCues, positionMs))
        }

        private fun clearSecondarySubtitles() {
            loadedSecondaryCues = emptyList()
            secondarySubtitleCues.postValue(listOf())
            secondarySubtitlesActive.postValue(false)
            if (::player.isInitialized) {
                (player as? MpvPlayer)?.setSecondarySubtitleTrack("no")
            }
        }

        internal suspend fun applySecondarySubtitle(itemPlayback: ItemPlayback = currentItemPlayback.value!!) {
            if (!preferences.appPreferences.playbackPreferences.enableDualSubtitles) {
                clearSecondarySubtitles()
                return
            }
            if (!itemPlayback.subtitleIndexEnabled) {
                clearSecondarySubtitles()
                return
            }
            val secondaryIndex = itemPlayback.secondarySubtitleIndex
            if (secondaryIndex < 0) {
                clearSecondarySubtitles()
                return
            }
            if (secondaryIndex == itemPlayback.subtitleIndex) {
                clearSecondarySubtitles()
                return
            }

            val source = currentPlayback.value?.mediaSourceInfo ?: return
            val stream =
                source.mediaStreams?.firstOrNull { it.index == secondaryIndex && it.type == MediaStreamType.SUBTITLE }
                    ?: currentItem.item.data.mediaSources
                        ?.asSequence()
                        ?.flatMap { it.mediaStreams.orEmpty().asSequence() }
                        ?.firstOrNull { it.index == secondaryIndex && it.type == MediaStreamType.SUBTITLE }
            if (stream != null && isImageSubtitleStream(stream)) {
                Timber.w("Secondary subtitle track $secondaryIndex is image-based and unsupported")
                clearSecondarySubtitles()
                notifySecondarySubtitleFailed(R.string.secondary_subtitles_image_not_supported)
                return
            }
            val playerBackend = currentPlayer.value?.backend ?: return
            val supportsDirectPlay = currentPlayback.value?.playMethod == PlayMethod.DIRECT_PLAY
            val subtitleUrl = buildSubtitleUrl(itemPlayback.itemId, source.id, secondaryIndex, stream)

            if (player is MpvPlayer) {
                withContext(Dispatchers.Main) {
                    DualSubtitleUtils.applySecondarySubtitleToMpv(
                        mpvPlayer = player as MpvPlayer,
                        tracks = player.currentTracks,
                        playerBackend = playerBackend,
                        supportsDirectPlay = supportsDirectPlay,
                        secondarySubtitleIndex = secondaryIndex,
                        source = source,
                        subtitleUrl = subtitleUrl,
                        stream = stream,
                    )
                }
                loadedSecondaryCues = emptyList()
                secondarySubtitleCues.postValue(listOf())
                secondarySubtitlesActive.postValue(true)
                return
            }

            val mediaSourceId = source.id ?: run {
                clearSecondarySubtitles()
                notifySecondarySubtitleFailed(R.string.secondary_subtitles_failed)
                return
            }
            val additionalSourceIds =
                currentItem.item.data.mediaSources?.mapNotNull { it.id }.orEmpty()
            val fetched =
                SecondarySubtitleFetcher.fetch(
                    api = api,
                    context = context,
                    httpClient = authOkHttpClient,
                    itemId = itemPlayback.itemId,
                    mediaSourceIds =
                        mediaSourceIdsToTry(
                            sourceId = mediaSourceId,
                            savedSourceId = itemPlayback.sourceId,
                            additionalSourceIds = additionalSourceIds,
                        ),
                    subtitleIndex = secondaryIndex,
                    stream = stream,
                )
            if (fetched == null) {
                Timber.w("Failed to fetch secondary subtitle bytes for index $secondaryIndex")
                clearSecondarySubtitles()
                notifySecondarySubtitleFailed(R.string.secondary_subtitles_failed)
                return
            }
            loadedSecondaryCues =
                SecondarySubtitleParser.parse(
                    bytes = fetched.bytes,
                    deliveryFormat = fetched.format,
                    stream = stream,
                    assHandler = currentPlayer.value?.assHandler,
                )
            Timber.d("Loaded ${loadedSecondaryCues.size} secondary cues")
            if (loadedSecondaryCues.isEmpty()) {
                clearSecondarySubtitles()
                notifySecondarySubtitleFailed(R.string.secondary_subtitles_failed)
                return
            }
            secondarySubtitlesActive.postValue(true)
            withContext(Dispatchers.Main) {
                updateSecondaryCuePosition(player.currentPosition)
            }
        }

        private suspend fun notifySecondarySubtitleFailed(messageRes: Int) {
            withContext(Dispatchers.Main) {
                showToast(context, context.getString(messageRes), Toast.LENGTH_LONG)
            }
        }

        private fun buildSecondarySubtitleConfiguration(
            itemId: UUID,
            mediaSourceId: String,
            stream: MediaStream,
        ): MediaItem.SubtitleConfiguration? {
            val uri =
                stream.deliveryUrl?.let { api.createUrl(it).toUri() }
                    ?: buildSubtitleUrl(itemId, mediaSourceId, stream.index, stream)?.toUri()
                    ?: return null
            var flags = 0
            if (stream.isForced) flags = flags.or(C.SELECTION_FLAG_FORCED)
            if (stream.isDefault) flags = flags.or(C.SELECTION_FLAG_DEFAULT)
            return MediaItem.SubtitleConfiguration
                .Builder(uri)
                .setId("s:${stream.index}")
                .setMimeType(subtitleMimeTypes[stream.codec])
                .setLanguage(stream.language)
                .setLabel(stream.title ?: "Secondary")
                .setSelectionFlags(flags)
                .build()
        }

        private fun buildSubtitleUrl(
            itemId: UUID,
            mediaSourceId: String?,
            subtitleIndex: Int,
            stream: org.jellyfin.sdk.model.api.MediaStream?,
        ): String? {
            if (mediaSourceId == null) return null
            val format = subtitleDeliveryFormat(stream)
            val normalizedSourceId = mediaSourceId.normalizeMediaSourceId()
            return try {
                api.createUrl(
                    api.subtitleApi
                        .getSubtitleUrl(
                            routeItemId = itemId,
                            routeMediaSourceId = normalizedSourceId,
                            routeIndex = subtitleIndex,
                            routeFormat = format,
                        ),
                )
            } catch (ex: Exception) {
                Timber.e(ex, "Failed to build subtitle URL for index $subtitleIndex")
                null
            }
        }

        private suspend fun prefetchTrickplay(
            duration: Duration,
            trickplayInfo: TrickplayInfo,
            mediaSourceId: UUID?,
        ) {
            val tilesPerImage = trickplayInfo.tileWidth * trickplayInfo.tileHeight
            val totalCount =
                (duration.inWholeMilliseconds / trickplayInfo.interval).toInt() / tilesPerImage + 1
            (0..<totalCount).forEach {
                val url = getTrickplayUrl(it, trickplayInfo, mediaSourceId)
                context.imageLoader.enqueue(
                    ImageRequest
                        .Builder(context)
                        .data(url)
                        .size(Size.ORIGINAL)
                        .build(),
                )
            }
        }

        fun getTrickplayUrl(
            index: Int,
            trickPlayInfo: TrickplayInfo? = currentMediaInfo.value?.trickPlayInfo,
            mediaSourceId: UUID? = currentItemPlayback.value?.sourceId,
        ): String? =
            trickPlayInfo?.let {
                val itemId = currentItem.id
                return api.trickplayApi.getTrickplayTileImageUrl(
                    itemId,
                    trickPlayInfo.width,
                    index,
                    mediaSourceId,
                )
            }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                Timber.v("Playback state is STATE_ENDED")
                viewModelScope.launchDefault {
                    if (isLibraryTvPlayback) {
                        playNextLibraryTvProgram()
                        return@launchDefault
                    }
                    when (val nextItem = playlist.value?.peek()) {
                        is PlaylistItem.Intro -> {
                            Timber.v("Next item is intro, so playing immediately")
                            playNextUp()
                        }

                        is PlaylistItem.Media -> {
                            if (currentItem is PlaylistItem.Intro) {
                                Timber.v("Current item is intro, so playing next up immediately")
                                playNextUp()
                            } else {
                                Timber.v("Setting next up to ${nextItem.id}")
                                withContext(Dispatchers.Main) {
                                    nextUp.value = nextItem.item
                                }
                            }
                        }

                        null -> {
                            Timber.v("No next up")
                            navigationManager.goBack()
                        }
                    }
                }
            }
        }

        // Variables for tracking segment state
        private var segmentJob: Job? = null
        private val autoSkippedSegments = mutableSetOf<UUID>()
        private val outroShownSegments = mutableSetOf<UUID>()

        /**
         * Cancels listening for segments and clears current segment state
         */
        private fun resetSegmentState() {
            segmentJob?.cancel()
            autoSkippedSegments.clear()
            outroShownSegments.clear()
            currentSegment.value = null
        }

        /**
         * This sets up a coroutine to periodically check whether the current playback progress is within a media segment (intro, outro, etc)
         */
        private fun listenForSegments(
            itemId: UUID,
            suppressIntroOutroSegments: Boolean,
        ) {
            segmentJob?.cancel()
            segmentJob =
                viewModelScope.launchIO {
                    val prefs = preferences.appPreferences.playbackPreferences
                    val segments by api.mediaSegmentsApi.getItemSegments(itemId)
                    if (segments.items.isNotEmpty()) {
                        while (isActive) {
                            delay(500L)
                            val currentTicks =
                                onMain { player.currentPosition.milliseconds.inWholeTicks }
                            val currentSegment =
                                segments.items
                                    .firstOrNull {
                                        it.type != MediaSegmentType.UNKNOWN &&
                                            !(suppressIntroOutroSegments && it.type.isIntroOrOutro()) &&
                                            currentTicks >= it.startTicks &&
                                            currentTicks < it.endTicks
                                    }
                            if (currentSegment != null &&
                                currentSegment.itemId == this@PlaybackViewModel.itemId
                            ) {
                                if (currentSegment.id !=
                                    this@PlaybackViewModel
                                        .currentSegment.value
                                        ?.segment
                                        ?.id
                                ) {
                                    Timber.d(
                                        "Found media segment for %s: %s, %s",
                                        currentSegment.itemId,
                                        currentSegment.id,
                                        currentSegment.type,
                                    )
                                }
                                val playlist = this@PlaybackViewModel.playlist.value

                                if (currentSegment.type == MediaSegmentType.OUTRO &&
                                    prefs.showNextUpWhen == ShowNextUpWhen.DURING_CREDITS &&
                                    playlist != null && playlist.hasNext() &&
                                    outroShownSegments.add(currentSegment.id)
                                ) {
                                    val nextItem = playlist.peek()
                                    if (nextItem is PlaylistItem.Media) {
                                        Timber.v("Setting next up during outro to ${nextItem?.id}")
                                        withContext(Dispatchers.Main) {
                                            nextUp.value = nextItem.item
                                        }
                                    }
                                } else {
                                    val behavior =
                                        when (currentSegment.type) {
                                            MediaSegmentType.COMMERCIAL -> prefs.skipCommercials
                                            MediaSegmentType.PREVIEW -> prefs.skipPreviews
                                            MediaSegmentType.RECAP -> prefs.skipRecaps
                                            MediaSegmentType.OUTRO -> prefs.skipOutros
                                            MediaSegmentType.INTRO -> prefs.skipIntros
                                            MediaSegmentType.UNKNOWN -> SkipSegmentBehavior.IGNORE
                                        }
                                    withContext(Dispatchers.Main) {
                                        when (behavior) {
                                            SkipSegmentBehavior.AUTO_SKIP -> {
                                                if (autoSkippedSegments.add(currentSegment.id)) {
                                                    onMain { player.seekTo(currentSegment.endTicks.ticks.inWholeMilliseconds + 1) }
                                                }
                                                this@PlaybackViewModel.currentSegment.update {
                                                    MediaSegmentState(currentSegment, true)
                                                }
                                            }

                                            SkipSegmentBehavior.ASK_TO_SKIP -> {
                                                this@PlaybackViewModel.currentSegment.update {
                                                    MediaSegmentState(
                                                        currentSegment,
                                                        autoSkippedSegments.contains(currentSegment.id),
                                                    )
                                                }
                                            }

                                            else -> {
                                                this@PlaybackViewModel.currentSegment.value = null
                                            }
                                        }
                                    }
                                }
                            } else if (currentSegment == null) {
                                withContext(Dispatchers.Main) {
                                    this@PlaybackViewModel.currentSegment.value = null
                                }
                            }
                        }
                    }
                }
        }

        private fun MediaSegmentType.isIntroOrOutro(): Boolean =
            this == MediaSegmentType.INTRO || this == MediaSegmentType.OUTRO

        fun updateSegment(
            segmentId: UUID?,
            dismissed: Boolean,
        ) {
            viewModelScope.launchDefault {
                val segment = currentSegment.value?.segment
                if (segment != null && segment.id == segmentId) {
                    autoSkippedSegments.add(segment.id)
                    if (dismissed) {
                        currentSegment.update {
                            it?.copy(interacted = true)
                        }
                    } else {
                        currentSegment.update {
                            null
                        }
                        onMain { player.seekTo(segment.endTicks.ticks.inWholeMilliseconds + 1) }
                    }
                }
            }
        }

        private fun listenForTranscodeReason(): Job =
            viewModelScope.launchIO {
                currentPlayback.collectLatest {
                    if (it != null) {
                        try {
                            var transcodeInfo = it.transcodeInfo
                            while (isActive && it.playMethod == PlayMethod.TRANSCODE && transcodeInfo == null) {
                                delay(2.seconds)
                                transcodeInfo =
                                    api.sessionApi
                                        .getSessions(deviceId = deviceInfo.id)
                                        .content
                                        .firstOrNull()
                                        ?.transcodingInfo
                                if (transcodeInfo == null) delay(3.seconds)
                            }
                            Timber.v("transcodeInfo=$transcodeInfo")
                            currentPlayback.update { current ->
                                current?.copy(transcodeInfo = transcodeInfo)
                            }
                        } catch (ex: Exception) {
                            if (ex !is CancellationException) {
                                Timber.w(ex, "Exception trying to get session info")
                                currentPlayback.update { current ->
                                    current?.copy(transcodeInfo = null)
                                }
                            }
                        }
                    }
                }
            }

        private var lastInteractionDate: Date = Date()

        /**
         * Tracks interactions with the UI for passout protection
         */
        fun reportInteraction() {
//            Timber.v("reportInteraction")
            lastInteractionDate = Date()
        }

        fun tuneLibraryTvChannel(direction: Int) {
            if (!isLibraryTvPlayback) return
            viewModelScope.launchDefault {
                val channels = ensureCurrentLibraryTvChannels()
                if (channels.isEmpty()) return@launchDefault
                val currentIndex =
                    pendingLibraryTvChannelIndex
                        ?: channels
                            .indexOfFirst { it.key == currentLibraryTvChannelKey }
                            .takeIf { it >= 0 }
                        ?: 0
                val nextIndex = Math.floorMod(currentIndex + direction, channels.size)
                pendingLibraryTvChannelIndex = nextIndex
                updateLibraryTvPlaybackPreview(channels[nextIndex])
                libraryTvTuneJob?.cancel()
                libraryTvTuneJob =
                    viewModelScope.launchDefault {
                        delay(LibraryTvChannelTuneDebounceMs)
                        val channelIndex = pendingLibraryTvChannelIndex ?: return@launchDefault
                        pendingLibraryTvChannelIndex = null
                        channels.getOrNull(channelIndex)?.let { channel ->
                            playLibraryTvChannel(channel)
                        }
                    }
            }
        }

        fun playLibraryTvDestination(destination: Destination.Playback) {
            val channelKey = destination.libraryTvChannelKey ?: return
            if (!isLibraryTvPlayback || appliedLibraryTvDestinationEntryId == destination.entryId) return
            appliedLibraryTvDestinationEntryId = destination.entryId
            viewModelScope.launchDefault {
                try {
                    if (!::preferences.isInitialized) {
                        preferences = userPreferencesService.getCurrent()
                        controllerViewState.hideMilliseconds =
                            preferences.appPreferences.playbackPreferences.controllerTimeoutMs
                    }
                    forceTranscoding = destination.forceTranscoding
                    val channel =
                        ensureCurrentLibraryTvChannels()
                            .firstOrNull { it.key == channelKey }
                            ?: return@launchDefault
                    val now = Instant.now()
                    val program =
                        channel.programs.firstOrNull { it.item.id == destination.itemId }
                            ?: channel.programAt(now)
                            ?: channel.programs.firstOrNull()
                            ?: return@launchDefault
                    playLibraryTvProgram(channel, program, destination.positionMs)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Could not start Library TV playback")
                    loading.setValueOnMain(LoadingState.Error("Could not start Library TV playback", ex))
                }
            }
        }

        private fun updateLibraryTvPlaybackPreview(channel: LibraryTvChannel) {
            val now = Instant.now()
            val program = channel.programAt(now) ?: channel.programs.firstOrNull() ?: return
            updateLibraryTvPlaybackState(channel, program)
        }

        private suspend fun tuneCurrentLibraryTvChannel() {
            libraryTvTuneJob?.cancel()
            pendingLibraryTvChannelIndex = null
            val channel =
                ensureCurrentLibraryTvChannels()
                    .firstOrNull { it.key == currentLibraryTvChannelKey }
                    ?: return
            playLibraryTvChannel(channel)
        }

        private suspend fun playNextLibraryTvProgram() {
            val channel =
                ensureCurrentLibraryTvChannels()
                    .firstOrNull { it.key == currentLibraryTvChannelKey }
                    ?: return
            val currentEpisodeId =
                currentLibraryTvProgram
                    ?.item
                    ?.takeIf { it.type == BaseItemKind.EPISODE }
                    ?.id
            val avoidedEpisodeIds = currentLibraryTvAvoidedEpisodeIds() + listOfNotNull(currentEpisodeId)
            val nextProgram =
                currentLibraryTvProgram
                    ?.let { currentProgram -> channel.programAfter(currentProgram, avoidedEpisodeIds) }
                    ?: channel.programAt(Instant.now())
                    ?: channel.programs.firstOrNull()
                    ?: return
            playLibraryTvProgram(channel, nextProgram, positionMs = 0L)
        }

        private suspend fun playLibraryTvChannel(channel: LibraryTvChannel) {
            val now = Instant.now()
            val program = channel.programAt(now) ?: channel.programs.firstOrNull() ?: return
            playLibraryTvProgram(channel, program, program.libraryTvPositionMs(now))
        }

        private suspend fun playLibraryTvProgram(
            channel: LibraryTvChannel,
            program: LibraryTvProgram,
            positionMs: Long,
        ) {
            currentLibraryTvChannelKey = channel.key
            currentLibraryTvProgram = program
            updateLibraryTvPlaybackState(channel, program)
            cancelUpNextEpisode()
            val item = BaseItem(api.userLibraryApi.getItem(program.item.id).content, false)
            updateLibraryTvPlaybackState(channel, program, item.data.overview)
            val played =
                play(
                    PlaylistItem.Media(item),
                    positionMs,
                    itemPlayback = null,
                    forceTranscoding = forceTranscoding,
                )
            if (played) {
                updateLibraryTvPlaybackState(channel, program, item.data.overview)
            }
        }

        private fun startLibraryTvWatchedTracking(item: BaseItem) {
            libraryTvWatchedJob?.cancel()
            if (!isLibraryTvPlayback || item.type != BaseItemKind.EPISODE || item.id in markedLibraryTvEpisodeIds) {
                return
            }
            val fallbackDurationMs = item.libraryTvWatchedDurationMs() ?: return
            libraryTvWatchedJob =
                viewModelScope.launchIO {
                    var watchedMs = 0L
                    var lastPositionMs = withContext(Dispatchers.Main) { player.currentPosition }
                    while (isActive && this@PlaybackViewModel.itemId == item.id) {
                        delay(LibraryTvWatchedProgressInterval)
                        val sample =
                            withContext(Dispatchers.Main) {
                                LibraryTvWatchSample(
                                    isPlaying = player.isPlaying,
                                    positionMs = player.currentPosition,
                                    durationMs = player.duration,
                                )
                            }
                        val durationMs =
                            sample.durationMs
                                .takeIf { it > 0 && it != C.TIME_UNSET }
                                ?: fallbackDurationMs
                        if (sample.isPlaying && sample.positionMs >= 0 && lastPositionMs >= 0) {
                            watchedMs +=
                                (sample.positionMs - lastPositionMs)
                                    .coerceIn(0L, LibraryTvWatchedProgressInterval.inWholeMilliseconds + 1000L)
                        }
                        lastPositionMs = sample.positionMs
                        if (watchedMs * 2 > durationMs) {
                            markLibraryTvEpisodeWatched(item)
                            return@launchIO
                        }
                    }
                }
        }

        private suspend fun markLibraryTvEpisodeWatched(item: BaseItem) {
            val user = serverRepository.currentUser.value ?: return
            if (!markedLibraryTvEpisodeIds.add(item.id)) return
            libraryTvWatchedEpisodeDao.save(
                LibraryTvWatchedEpisode(
                    userId = user.rowId,
                    itemId = item.id,
                    watchedAtEpochMs = System.currentTimeMillis(),
                ),
            )
        }

        private suspend fun currentLibraryTvAvoidedEpisodeIds(): Set<UUID> {
            val user = serverRepository.currentUser.value ?: return markedLibraryTvEpisodeIds.toSet()
            return markedLibraryTvEpisodeIds + libraryTvWatchedEpisodeDao.getWatchedEpisodeIds(user.rowId)
        }

        private fun BaseItem.libraryTvWatchedDurationMs(): Long? =
            data.runTimeTicks
                ?.ticks
                ?.inWholeMilliseconds
                ?.takeIf { it > 0 }
                ?: currentLibraryTvProgram
                    ?.takeIf { it.item.id == id }
                    ?.let { it.end.toEpochMilli() - it.start.toEpochMilli() }
                    ?.takeIf { it > 0 }

        private fun currentLibraryTvChannels(): List<LibraryTvChannel> {
            val disabledChannelKeys =
                preferences.appPreferences.interfacePreferences.liveTvPreferences.disabledLibraryTvChannelKeysList.toSet()
            return LibraryTvGuideMemoryCache
                .currentGuide()
                ?.withEnabledChannels(disabledChannelKeys)
                ?.channels
                .orEmpty()
        }

        private suspend fun ensureCurrentLibraryTvChannels(): List<LibraryTvChannel> {
            val user = serverRepository.currentUser.value ?: return currentLibraryTvChannels()
            val disabledChannelKeys =
                preferences.appPreferences.interfacePreferences.liveTvPreferences.disabledLibraryTvChannelKeysList.toSet()
            return libraryTvGuideService
                .load(user)
                .withEnabledChannels(disabledChannelKeys)
                .channels
        }

        private fun updateLibraryTvPlaybackState(channelKey: String?) {
            if (!isLibraryTvPlayback || channelKey == null) return
            val now = Instant.now()
            currentLibraryTvChannels()
                .firstOrNull { it.key == channelKey }
                ?.let { channel ->
                    (channel.programAt(now) ?: channel.programs.firstOrNull())
                        ?.let { updateLibraryTvPlaybackState(channel, it) }
                }
        }

        private fun updateLibraryTvPlaybackState(
            channel: LibraryTvChannel,
            program: LibraryTvProgram,
            overview: String? = null,
        ) {
            if (channel.key == currentLibraryTvChannelKey) {
                currentLibraryTvProgram = program
            }
            libraryTvPlayback.update {
                LibraryTvPlaybackInfo(
                    channelKey = channel.key,
                    channelNumber = channel.number,
                    channelName = channel.name,
                    title = program.title,
                    subtitle = program.item.subtitle,
                    overview = overview ?: program.item.data.overview,
                    start = program.start,
                    end = program.end,
                )
            }
        }

        private fun LibraryTvProgram.libraryTvPositionMs(now: Instant): Long =
            if (!now.isBefore(start) && now.isBefore(end)) {
                (now.toEpochMilli() - start.toEpochMilli()).coerceAtLeast(0L)
            } else {
                0L
            }

        private fun LibraryTvChannel.programAfter(
            program: LibraryTvProgram,
            avoidedEpisodeIds: Set<UUID> = emptySet(),
        ): LibraryTvProgram? {
            val exactIndex =
                programs.indexOfFirst {
                    it.item.id == program.item.id &&
                        it.start == program.start &&
                        it.end == program.end
                }
            if (exactIndex >= 0) {
                val nextPrograms = programs.drop(exactIndex + 1)
                nextPrograms
                    .firstOrNull { it.item.type != BaseItemKind.EPISODE || it.item.id !in avoidedEpisodeIds }
                    ?.let { return it }
                return nextPrograms.firstOrNull()
            }
            val nextPrograms = programs.filter { it.start.isAfter(program.start) }
            return nextPrograms.firstOrNull { it.item.type != BaseItemKind.EPISODE || it.item.id !in avoidedEpisodeIds }
                ?: nextPrograms.firstOrNull()
        }

        fun shouldAutoPlayNextUp(): Boolean =
            preferences.appPreferences.playbackPreferences.let {
                it.autoPlayNext &&
                    if (it.passOutProtectionMs > 0) {
                        (Date().time - lastInteractionDate.time) < it.passOutProtectionMs
                    } else {
                        true
                    }
            }

        fun playNextUp() {
            playlist.value?.let {
                if (it.hasNext()) {
                    viewModelScope.launchDefault {
                        cancelUpNextEpisode()
                        val item = it.getAndAdvance()
                        val played = play(item, 0)
                        if (!played) {
                            playNextUp()
                        }
                    }
                }
            }
        }

        fun playPrevious() {
            playlist.value?.let {
                if (it.hasPrevious()) {
                    viewModelScope.launchDefault {
                        cancelUpNextEpisode()
                        val item = it.getPreviousAndReverse()
                        val played = play(item, 0)
                        if (!played) {
                            playPrevious()
                        }
                    }
                }
            }
        }

        suspend fun cancelUpNextEpisode() {
            nextUp.setValueOnMain(null)
        }

        fun playItemInPlaylist(item: BaseItem) {
            playlist.value?.let { playlist ->
                viewModelScope.launchIO {
                    val toPlay = playlist.advanceTo(item.id)
                    if (toPlay != null) {
                        val played = play(toPlay, 0)
                        if (!played) {
                            playNextUp()
                        }
                    } else {
                        // TODO
                    }
                }
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            currentPlayback.update {
                it?.copy(
                    tracks = checkForSupport(tracks),
                )
            }
        }

        override fun onCues(cueGroup: CueGroup) {
            subtitleCues.value = cueGroup.cues
        }

        override fun onPlayerError(error: PlaybackException) {
            Timber.e(error, "Playback error")
            viewModelScope.launch(Dispatchers.Main + ExceptionHandler()) {
                currentPlayback.value?.let {
                    when (it.playMethod) {
                        PlayMethod.TRANSCODE -> {
                            loading.setValueOnMain(
                                LoadingState.Error(
                                    "Error during playback",
                                    error,
                                ),
                            )
                        }

                        PlayMethod.DIRECT_STREAM, PlayMethod.DIRECT_PLAY -> {
                            Timber.w("Playback error during ${it.playMethod}, falling back to transcoding")
                            changeStreams(
                                currentItem.item,
                                currentItemPlayback.value!!,
                                currentItemPlayback.value?.audioIndex,
                                currentItemPlayback.value?.subtitleIndex,
                                player.currentPosition,
                                false,
                                enableDirectPlay = false,
                                enableDirectStream = false,
                            )
                            withContext(Dispatchers.Main) {
                                player.prepare()
                                player.play()
                            }
                        }
                    }
                }
            }
        }

        fun release() {
            Timber.v("release")
            playbackSession++
            disconnectPlayer()
            activityListener = null
            screensaverService.keepScreenOn(false)
            loading.value = LoadingState.Loading
            currentPlayback.update { null }
            currentPlayer.update { null }
        }

        fun subscribe(): Job =
            api.webSocket
                .subscribe<PlaystateMessage>()
                .onEach { message ->
                    message.data?.let {
                        withContext(Dispatchers.Main) {
                            when (it.command) {
                                PlaystateCommand.STOP -> {
                                    release()
                                    navigationManager.goBack()
                                }

                                PlaystateCommand.PAUSE -> {
                                    player.pause()
                                }

                                PlaystateCommand.UNPAUSE -> {
                                    player.play()
                                }

                                PlaystateCommand.NEXT_TRACK -> {
                                    playNextUp()
                                }

                                PlaystateCommand.PREVIOUS_TRACK -> {
                                    playPrevious()
                                }

                                PlaystateCommand.SEEK -> {
                                    it.seekPositionTicks?.ticks?.let {
                                        player.seekTo(
                                            it.inWholeMilliseconds,
                                        )
                                    }
                                }

                                PlaystateCommand.REWIND -> {
                                    player.seekBack(
                                        preferences.appPreferences.playbackPreferences.skipBackMs.milliseconds,
                                    )
                                }

                                PlaystateCommand.FAST_FORWARD -> {
                                    player.seekForward(
                                        preferences.appPreferences.playbackPreferences.skipForwardMs.milliseconds,
                                    )
                                }

                                PlaystateCommand.PLAY_PAUSE -> {
                                    if (player.isPlaying) player.pause() else player.play()
                                }
                            }
                        }
                    }
                }.launchIn(viewModelScope)

        /**
         * Atomically update [currentMediaInfo]
         */
        internal suspend fun updateCurrentMedia(block: (CurrentMediaInfo) -> CurrentMediaInfo) =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val newMediaInfo = block.invoke(currentMediaInfo.value!!)
                    currentMediaInfo.setValueOnMain(newMediaInfo)
                }
            }

        private fun updateDecoder(
            decoderName: String,
            type: MediaType,
        ) {
            viewModelScope.launchDefault {
                val codecInfo =
                    MediaCodecList(MediaCodecList.ALL_CODECS)
                        .codecInfos
                        .firstOrNull { !it.isEncoder && it.name == decoderName }
                val decoderString =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        if (codecInfo?.isHardwareAccelerated == true) {
                            "$decoderName (HW)"
                        } else {
                            decoderName
                        }
                    } else {
                        decoderName
                    }
                currentPlayback.update {
                    when (type) {
                        MediaType.VIDEO -> it?.copy(videoDecoder = decoderString)
                        MediaType.AUDIO -> it?.copy(audioDecoder = decoderString)
                        else -> throw IllegalArgumentException("Unsupported type: $type")
                    }
                }
            }
        }

        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            Timber.v("onVideoDecoderInitialized: decoder=$decoderName")
            updateDecoder(decoderName, MediaType.VIDEO)
        }

        override fun onVideoDisabled(
            eventTime: AnalyticsListener.EventTime,
            decoderCounters: DecoderCounters,
        ) {
            Timber.d("onVideoDisabled")
            currentPlayback.update { it?.copy(videoDecoder = null) }
        }

        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            decoderReuseEvaluation?.let { decoder ->
                if (decoder.result != DecoderReuseEvaluation.REUSE_RESULT_NO) {
                    Timber.d("onVideoInputFormatChanged: decoder=${decoder.decoderName}")
                    updateDecoder(decoder.decoderName, MediaType.VIDEO)
                }
            }
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            Timber.d("decoder: onAudioDecoderInitialized: decoder=$decoderName")
            updateDecoder(decoderName, MediaType.AUDIO)
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            decoderReuseEvaluation?.let { decoder ->
                if (decoder.result != DecoderReuseEvaluation.REUSE_RESULT_NO) {
                    Timber.d("decoder: onAudioInputFormatChanged: decoder=${decoder.decoderName}")
                    updateDecoder(decoder.decoderName, MediaType.AUDIO)
                }
            }
        }

        override fun onAudioDisabled(
            eventTime: AnalyticsListener.EventTime,
            decoderCounters: DecoderCounters,
        ) {
            Timber.d("decoder: onAudioDisabled")
            currentPlayback.update { it?.copy(audioDecoder = null) }
        }

        private var subtitleDelaySaveJob: Job? = null

        fun updateSubtitleDelay(delta: Duration) {
            subtitleDelaySaveJob?.cancel()
            currentPlayback.update {
                it?.let {
                    val newDelay = it.subtitleDelay + delta
                    val result = it.copy(subtitleDelay = it.subtitleDelay + delta)
                    subtitleDelaySaveJob =
                        viewModelScope.launchIO {
                            // Debounce & save
                            currentItemPlayback.value?.let { item ->
                                delay(1500)
                                itemPlaybackRepository.saveTrackModifications(
                                    item.itemId,
                                    item.subtitleIndex,
                                    newDelay,
                                )
                            }
                        }
                    result
                }
            }
        }

        suspend fun loadSubtitleDelay() {
            currentItemPlayback.value?.let {
                if (it.subtitleIndexEnabled) {
                    val result =
                        itemPlaybackRepository.getTrackModifications(it.itemId, it.subtitleIndex)
                    if (result != null) {
                        Timber.v(
                            "Loading subtitle delay %s for track=%s, itemId=%s",
                            result.delayMs,
                            it.subtitleIndex,
                            it.itemId,
                        )
                        currentPlayback.update { it?.copy(subtitleDelay = result.delayMs.milliseconds) }
                    }
                }
                applySecondarySubtitle(it)
            }
        }

        override fun onBandwidthEstimate(
            eventTime: AnalyticsListener.EventTime,
            totalLoadTimeMs: Int,
            totalBytesLoaded: Long,
            bitrateEstimate: Long,
        ) {
            Timber.v(
                "onBandwidthEstimate: totalLoadTimeMs=%s, totalBytesLoaded=%s, bitrateEstimate=%s",
                totalLoadTimeMs,
                totalBytesLoaded,
                bitrateEstimate,
            )
            if (totalLoadTimeMs > 0 && totalBytesLoaded > 0) {
                analyticsState.update {
                    it.copy(
                        bitrate = formatBitrate((totalBytesLoaded.toDouble() / (totalLoadTimeMs / 1000.0) * 8).roundToInt()),
                        bitrateEstimate = formatBitrate(bitrateEstimate.toInt()),
                    )
                }
            }
        }

        override fun onDroppedVideoFrames(
            eventTime: AnalyticsListener.EventTime,
            droppedFrames: Int,
            elapsedMs: Long,
        ) {
//            Timber.v("onDroppedVideoFrames: droppedFrames=%s", droppedFrames)
            analyticsState.update { it.copy(droppedFrames = it.droppedFrames + droppedFrames) }
        }
    }

data class PlayerState(
    val player: Player,
    val backend: PlayerBackend,
    val assHandler: AssHandler?,
)

data class MediaSegmentState(
    val segment: MediaSegmentDto,
    val interacted: Boolean,
)

data class AnalyticsState(
    val bitrate: String = formatBitrate(0),
    val bitrateEstimate: String = formatBitrate(0),
    val droppedFrames: Int = 0,
)
