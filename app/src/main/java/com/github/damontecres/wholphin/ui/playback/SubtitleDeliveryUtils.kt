package com.github.damontecres.wholphin.ui.playback

import androidx.media3.common.MimeTypes
import com.github.damontecres.wholphin.util.profile.Codec
import org.jellyfin.sdk.model.api.MediaStream
import java.io.File
import java.util.UUID
import com.github.damontecres.wholphin.ui.toServerString

fun subtitleDeliveryFormat(stream: MediaStream?): String {
    stream?.path?.let { path ->
        File(path).extension.takeIf { it.isNotBlank() }?.let { return it }
    }
    return when (stream?.codec?.lowercase()) {
        Codec.Subtitle.SUBRIP,
        Codec.Subtitle.SRT,
        -> "srt"

        Codec.Subtitle.ASS,
        Codec.Subtitle.SSA,
        -> "ass"

        Codec.Subtitle.VTT,
        Codec.Subtitle.WEBVTT,
        -> "vtt"

        Codec.Subtitle.TTML,
        -> "ttml"

        else -> stream?.codec?.lowercase() ?: "srt"
    }
}

fun subtitleDeliveryFormatsToTry(stream: MediaStream?): List<String> =
    buildList {
        add(subtitleDeliveryFormat(stream))
        add("srt")
        add("ass")
        add("vtt")
    }.distinct()

fun subtitleMimeTypeForDeliveryFormat(format: String): String? =
    when (format.lowercase()) {
        "srt", "subrip" -> MimeTypes.APPLICATION_SUBRIP
        "ass", "ssa" -> MimeTypes.TEXT_SSA
        "vtt", "webvtt" -> MimeTypes.TEXT_VTT
        "ttml" -> MimeTypes.APPLICATION_TTML
        else -> null
    }

fun mediaSourceIdsToTry(
    sourceId: String?,
    savedSourceId: UUID?,
    additionalSourceIds: Collection<String> = emptyList(),
): List<String> =
    buildList {
        sourceId?.let { add(it) }
        sourceId?.normalizeMediaSourceId()?.let { if (it != sourceId) add(it) }
        savedSourceId?.toServerString()?.let { add(it) }
        additionalSourceIds.forEach { id ->
            add(id)
            add(id.normalizeMediaSourceId())
        }
    }.distinct()

fun String.normalizeMediaSourceId(): String = replace("-", "")

fun isTextSubtitleStream(stream: MediaStream?): Boolean {
    if (stream == null) return false
    if (isImageSubtitleStream(stream)) return false
    if (stream.isTextSubtitleStream == true) return true
    return when (stream.codec?.lowercase()) {
        Codec.Subtitle.ASS,
        Codec.Subtitle.SSA,
        Codec.Subtitle.SRT,
        Codec.Subtitle.SUBRIP,
        Codec.Subtitle.VTT,
        Codec.Subtitle.WEBVTT,
        Codec.Subtitle.TTML,
        Codec.Subtitle.SMI,
        Codec.Subtitle.SUB,
        Codec.Subtitle.SMIL,
        -> true

        else -> stream.isExternal
    }
}

fun isImageSubtitleStream(stream: MediaStream?): Boolean {
    if (stream == null) return false
    if (stream.isTextSubtitleStream == false) return true
    return when (stream.codec?.lowercase()) {
        Codec.Subtitle.PGS,
        Codec.Subtitle.PGSSUB,
        Codec.Subtitle.DVDSUB,
        Codec.Subtitle.DVBSUB,
        Codec.Subtitle.IDX,
        -> true

        else -> false
    }
}
