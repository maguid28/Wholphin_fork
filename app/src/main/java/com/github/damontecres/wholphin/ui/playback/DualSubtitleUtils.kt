package com.github.damontecres.wholphin.ui.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.github.damontecres.wholphin.data.model.TrackIndex
import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.util.mpv.MPVLib
import com.github.damontecres.wholphin.util.mpv.MpvPlayer
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import timber.log.Timber

object DualSubtitleUtils {
    @OptIn(UnstableApi::class)
    fun findSubtitleTrackGroup(
        tracks: Tracks,
        playerBackend: PlayerBackend,
        supportsDirectPlay: Boolean,
        subtitleIndex: Int?,
        source: MediaSourceInfo,
    ): Tracks.Group? {
        if (subtitleIndex == null || subtitleIndex < 0) return null
        val embeddedSubtitleCount = source.embeddedSubtitleCount
        val externalSubtitleCount = source.externalSubtitlesCount
        val subtitleIsExternal = source.findExternalSubtitle(subtitleIndex) != null
        if (!subtitleIsExternal && !supportsDirectPlay) return null

        return if (subtitleIsExternal && playerBackend == PlayerBackend.EXO_PLAYER) {
            tracks.groups.firstOrNull { group ->
                group.type == C.TRACK_TYPE_TEXT && group.isSupported &&
                    (0..<group.mediaTrackGroup.length)
                        .mapNotNull { group.getTrackFormat(it).id }
                        .any { it.endsWith("e:$subtitleIndex") }
            }
        } else {
            val actualEmbeddedCount =
                tracks.groups.count { group ->
                    group.type == C.TRACK_TYPE_TEXT &&
                        (0..<group.mediaTrackGroup.length)
                            .mapNotNull { group.getTrackFormat(it).id }
                            .none { it.contains("e:") }
                }
            val calculatedIndex =
                calculateSubtitleIndexToFind(
                    subtitleIndex = subtitleIndex,
                    playerBackend = playerBackend,
                    embeddedSubtitleCount = embeddedSubtitleCount,
                    externalSubtitleCount = externalSubtitleCount,
                    subtitleIsExternal = subtitleIsExternal,
                    actualEmbeddedCount = actualEmbeddedCount,
                    source = source,
                )
            tracks.groups.firstOrNull { group ->
                group.type == C.TRACK_TYPE_TEXT && group.isSupported &&
                    (0..<group.mediaTrackGroup.length)
                        .filter {
                            if (subtitleIsExternal) {
                                group.getTrackFormat(it).id?.contains("e:") == true
                            } else {
                                group.getTrackFormat(it).id?.contains("e:") == false
                            }
                        }.map { group.getTrackFormat(it).idAsInt }
                        .contains(calculatedIndex)
            }
        }
    }

    private fun calculateSubtitleIndexToFind(
        subtitleIndex: Int,
        playerBackend: PlayerBackend,
        embeddedSubtitleCount: Int,
        externalSubtitleCount: Int,
        subtitleIsExternal: Boolean,
        actualEmbeddedCount: Int,
        source: MediaSourceInfo,
    ): Int =
        when (playerBackend) {
            PlayerBackend.EXO_PLAYER,
            PlayerBackend.UNRECOGNIZED,
            -> subtitleIndex - externalSubtitleCount + 1

            PlayerBackend.PREFER_MPV,
            PlayerBackend.MPV,
            -> {
                if (subtitleIsExternal) {
                    subtitleIndex + maxOf(actualEmbeddedCount, embeddedSubtitleCount) + 1
                } else {
                    val videoStreamCount = source.videoStreamCount
                    val audioStreamCount = source.audioStreamCount
                    subtitleIndex - externalSubtitleCount - videoStreamCount - audioStreamCount + 1
                }
            }

            PlayerBackend.EXTERNAL_PLAYER -> throw IllegalStateException("Cannot calculate tracks for external playback")
        }

    @OptIn(UnstableApi::class)
    fun mpvTrackIdFromGroup(group: Tracks.Group): String? = group.mediaTrackGroup.getFormat(0).id?.split(":")?.lastOrNull()

    fun applySecondarySubtitleToMpv(
        mpvPlayer: MpvPlayer,
        tracks: Tracks,
        playerBackend: PlayerBackend,
        supportsDirectPlay: Boolean,
        secondarySubtitleIndex: Int?,
        source: MediaSourceInfo,
        subtitleUrl: String?,
        stream: MediaStream?,
    ) {
        if (secondarySubtitleIndex == null || secondarySubtitleIndex < 0) {
            mpvPlayer.setSecondarySubtitleTrack("no")
            return
        }

        val existingGroup =
            findSubtitleTrackGroup(
                tracks = tracks,
                playerBackend = playerBackend,
                supportsDirectPlay = supportsDirectPlay,
                subtitleIndex = secondarySubtitleIndex,
                source = source,
            )
        val existingTrackId = existingGroup?.let { mpvTrackIdFromGroup(it) }
        if (existingTrackId != null) {
            Timber.d("Applying existing secondary subtitle track id=$existingTrackId")
            mpvPlayer.setSecondarySubtitleTrack(existingTrackId)
            return
        }

        val externalStream = source.findExternalSubtitle(secondarySubtitleIndex)
        if (externalStream != null && subtitleUrl != null) {
            val title = stream?.title ?: externalStream.title ?: "Secondary Subtitles"
            val language = stream?.language ?: externalStream.language
            if (language.isNotNullOrBlank()) {
                MPVLib.command(arrayOf("sub-add", subtitleUrl, "auto", title, language!!))
            } else {
                MPVLib.command(arrayOf("sub-add", subtitleUrl, "auto", title))
            }
            val updatedTracks = mpvPlayer.currentTracks
            val addedGroup =
                findSubtitleTrackGroup(
                    tracks = updatedTracks,
                    playerBackend = playerBackend,
                    supportsDirectPlay = supportsDirectPlay,
                    subtitleIndex = secondarySubtitleIndex,
                    source = source,
                ) ?: updatedTracks.groups.lastOrNull { it.type == C.TRACK_TYPE_TEXT }
            val trackId = addedGroup?.let { mpvTrackIdFromGroup(it) }
            if (trackId != null) {
                Timber.d("Applied newly added secondary subtitle track id=$trackId")
                mpvPlayer.setSecondarySubtitleTrack(trackId)
            } else {
                Timber.w("Could not resolve secondary subtitle track after sub-add")
            }
            return
        }

        if (supportsDirectPlay) {
            val refreshedGroup =
                findSubtitleTrackGroup(
                    tracks = mpvPlayer.currentTracks,
                    playerBackend = playerBackend,
                    supportsDirectPlay = true,
                    subtitleIndex = secondarySubtitleIndex,
                    source = source,
                )
            refreshedGroup?.let { mpvTrackIdFromGroup(it) }?.let {
                mpvPlayer.setSecondarySubtitleTrack(it)
            }
        }
    }

    fun shouldShowSecondarySubtitleOption(
        dualSubtitlesEnabled: Boolean,
        primarySubtitleIndex: Int?,
    ): Boolean = dualSubtitlesEnabled && (primarySubtitleIndex ?: TrackIndex.DISABLED) >= 0

    fun secondarySubtitleIndexEnabled(index: Int): Boolean = index >= 0
}
