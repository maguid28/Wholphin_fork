package com.github.damontecres.wholphin.ui.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import com.github.damontecres.wholphin.util.subtitleMimeTypes
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.parser.AssSubtitleParserFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpMethod
import org.jellyfin.sdk.api.client.extensions.subtitleApi
import org.jellyfin.sdk.model.api.MediaStream
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.UUID

data class TimedSubtitleCue(
    val startTimeUs: Long,
    val endTimeUs: Long,
    val cue: Cue,
)

data class FetchedSecondarySubtitle(
    val bytes: ByteArray,
    val format: String,
)

object SecondarySubtitleFetcher {
    suspend fun fetch(
        api: ApiClient,
        context: Context,
        httpClient: OkHttpClient,
        itemId: UUID,
        mediaSourceIds: List<String>,
        subtitleIndex: Int,
        stream: MediaStream?,
    ): FetchedSecondarySubtitle? =
        withContext(Dispatchers.IO) {
            stream?.deliveryUrl?.let { deliveryUrl ->
                fetchBytes(context, httpClient, api.createUrl(deliveryUrl))?.let { bytes ->
                    Timber.i("Fetched secondary subtitle from deliveryUrl (${bytes.size} bytes)")
                    return@withContext FetchedSecondarySubtitle(bytes, subtitleDeliveryFormat(stream))
                }
            }

            for (mediaSourceId in mediaSourceIds.distinct()) {
                for (format in subtitleDeliveryFormatsToTry(stream)) {
                    fetchViaApiRequest(api, itemId, mediaSourceId, subtitleIndex, format)?.let { bytes ->
                        Timber.i(
                            "Fetched secondary subtitle via API request (${bytes.size} bytes) as $format for source $mediaSourceId",
                        )
                        return@withContext FetchedSecondarySubtitle(bytes, format)
                    }

                    try {
                        val subtitlePath =
                            api.subtitleApi.getSubtitleUrl(
                                routeItemId = itemId,
                                routeMediaSourceId = mediaSourceId,
                                routeIndex = subtitleIndex,
                                routeFormat = format,
                            )
                        fetchBytes(context, httpClient, api.createUrl(subtitlePath))?.let { bytes ->
                            Timber.i(
                                "Fetched secondary subtitle from URL (${bytes.size} bytes) as $format for source $mediaSourceId",
                            )
                            return@withContext FetchedSecondarySubtitle(bytes, format)
                        }
                    } catch (ex: Exception) {
                        Timber.v(ex, "Subtitle URL fetch failed for source=$mediaSourceId format=$format")
                    }

                    try {
                        val content =
                            api.subtitleApi
                                .getSubtitle(
                                    routeItemId = itemId,
                                    routeMediaSourceId = mediaSourceId,
                                    routeIndex = subtitleIndex,
                                    routeFormat = format,
                                    startPositionTicks = 0L,
                                ).content
                        if (!content.isNullOrEmpty()) {
                            Timber.i(
                                "Fetched secondary subtitle via getSubtitle (${content.length} chars) as $format for source $mediaSourceId",
                            )
                            return@withContext FetchedSecondarySubtitle(content.toByteArray(Charsets.UTF_8), format)
                        }
                    } catch (ex: Exception) {
                        Timber.v(ex, "getSubtitle failed for source=$mediaSourceId format=$format")
                    }

                    try {
                        val content =
                            api.subtitleApi
                                .getSubtitleWithTicks(
                                    routeItemId = itemId,
                                    routeMediaSourceId = mediaSourceId,
                                    routeIndex = subtitleIndex,
                                    routeStartPositionTicks = 0L,
                                    routeFormat = format,
                                ).content
                        if (!content.isNullOrEmpty()) {
                            Timber.i(
                                "Fetched secondary subtitle via getSubtitleWithTicks (${content.length} chars) as $format for source $mediaSourceId",
                            )
                            return@withContext FetchedSecondarySubtitle(content.toByteArray(Charsets.UTF_8), format)
                        }
                    } catch (ex: Exception) {
                        Timber.v(ex, "getSubtitleWithTicks failed for source=$mediaSourceId format=$format")
                    }
                }

                fetchHlsSubtitlePlaylist(
                    api = api,
                    context = context,
                    httpClient = httpClient,
                    itemId = itemId,
                    mediaSourceId = mediaSourceId,
                    subtitleIndex = subtitleIndex,
                )?.let { return@withContext it }
            }

            Timber.w(
                "All secondary subtitle fetch strategies failed for itemId=$itemId index=$subtitleIndex sources=$mediaSourceIds",
            )
            null
        }

    private suspend fun fetchHlsSubtitlePlaylist(
        api: ApiClient,
        context: Context,
        httpClient: OkHttpClient,
        itemId: UUID,
        mediaSourceId: String,
        subtitleIndex: Int,
    ): FetchedSecondarySubtitle? {
        try {
            val playlistBytes =
                api.subtitleApi
                    .getSubtitlePlaylist(
                        itemId = itemId,
                        index = subtitleIndex,
                        mediaSourceId = mediaSourceId,
                        segmentLength = 60,
                    ).content
            if (playlistBytes.isEmpty()) return null

            val playlistUrl =
                api.createUrl(
                    api.subtitleApi.getSubtitlePlaylistUrl(
                        itemId = itemId,
                        index = subtitleIndex,
                        mediaSourceId = mediaSourceId,
                        segmentLength = 60,
                    ),
                )
            val mergedVtt =
                HlsSubtitlePlaylistParser.mergeSegments(
                    playlistBytes = playlistBytes,
                    playlistBaseUrl = playlistUrl,
                    fetchSegment = { segmentUrl ->
                        fetchBytes(context, httpClient, segmentUrl)
                    },
                ) ?: return null

            Timber.i("Fetched secondary subtitle from HLS playlist (${mergedVtt.size} bytes)")
            return FetchedSecondarySubtitle(mergedVtt, "vtt")
        } catch (ex: Exception) {
            Timber.v(ex, "HLS subtitle playlist fetch failed for source=$mediaSourceId index=$subtitleIndex")
            return null
        }
    }

    private suspend fun fetchViaApiRequest(
        api: ApiClient,
        itemId: UUID,
        mediaSourceId: String,
        subtitleIndex: Int,
        format: String,
    ): ByteArray? =
        try {
            val response =
                api.request(
                    method = HttpMethod.GET,
                    pathTemplate = "/Videos/{routeItemId}/{routeMediaSourceId}/Subtitles/{routeIndex}/Stream.{routeFormat}",
                    pathParameters =
                        mapOf(
                            "routeItemId" to itemId,
                            "routeMediaSourceId" to mediaSourceId,
                            "routeIndex" to subtitleIndex,
                            "routeFormat" to format,
                        ),
                    queryParameters = emptyMap(),
                    requestBody = null,
                )
            if (response.status in 200..299) {
                response.body.takeIf { it.isNotEmpty() }
            } else {
                Timber.v("Subtitle API request failed: ${response.status} for source=$mediaSourceId format=$format")
                null
            }
        } catch (ex: Exception) {
            Timber.v(ex, "Subtitle API request exception for source=$mediaSourceId format=$format")
            null
        }

    private fun fetchBytes(
        context: Context,
        httpClient: OkHttpClient,
        url: String,
    ): ByteArray? =
        fetchViaHttpClient(httpClient, url)
            ?: fetchViaDataSource(context, httpClient, url)

    private fun fetchViaHttpClient(
        httpClient: OkHttpClient,
        url: String,
    ): ByteArray? =
        try {
            httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.v("Subtitle HTTP fetch failed: ${response.code} for $url")
                    null
                } else {
                    response.body.bytes().takeIf { it.isNotEmpty() }
                }
            }
        } catch (ex: Exception) {
            Timber.v(ex, "Subtitle HTTP fetch exception for $url")
            null
        }

    private fun fetchViaDataSource(
        context: Context,
        httpClient: OkHttpClient,
        url: String,
    ): ByteArray? {
        val dataSourceFactory =
            DefaultDataSource.Factory(context, OkHttpDataSource.Factory(httpClient))
        val dataSource: DataSource = dataSourceFactory.createDataSource()
        return try {
            val dataSpec = DataSpec(url.toUri())
            dataSource.open(dataSpec)
            val buffer = ByteArrayOutputStream()
            val scratch = ByteArray(16 * 1024)
            while (true) {
                val read = dataSource.read(scratch, 0, scratch.size)
                if (read == -1) break
                buffer.write(scratch, 0, read)
            }
            buffer.toByteArray().takeIf { it.isNotEmpty() }
        } catch (ex: Exception) {
            Timber.v(ex, "Subtitle DataSource fetch exception for $url")
            null
        } finally {
            dataSource.close()
        }
    }
}

internal object HlsSubtitlePlaylistParser {
    suspend fun mergeSegments(
        playlistBytes: ByteArray,
        playlistBaseUrl: String,
        fetchSegment: suspend (String) -> ByteArray?,
    ): ByteArray? {
        val segments = parseSegments(playlistBytes, playlistBaseUrl)
        if (segments.isEmpty()) return null

        val merged = StringBuilder()
        merged.append("WEBVTT\n\n")
        for ((offsetUs, segmentUrl) in segments) {
            val segmentBytes = fetchSegment(segmentUrl) ?: continue
            val segmentText = segmentBytes.toString(Charsets.UTF_8).trim()
            if (segmentText.isEmpty()) continue
            val body =
                segmentText
                    .lineSequence()
                    .dropWhile { it.startsWith("WEBVTT") || it.isBlank() }
                    .joinToString("\n")
            if (body.isBlank()) continue
            merged.append(shiftVttTimestamps(body, offsetUs / 1000))
            merged.append('\n')
        }
        val result = merged.toString().trim()
        return result.takeIf { it.length > 8 }?.toByteArray(Charsets.UTF_8)
    }

    private fun parseSegments(
        playlistBytes: ByteArray,
        playlistBaseUrl: String,
    ): List<Pair<Long, String>> {
        val baseUrl = playlistBaseUrl.toHttpUrlOrNull() ?: return emptyList()
        val basePath = baseUrl.newBuilder().removePathSegment(baseUrl.pathSegments.lastIndex).build()

        var segmentDurationSec = 0.0
        var accumulatedSec = 0.0
        val segments = mutableListOf<Pair<Long, String>>()

        for (line in playlistBytes.toString(Charsets.UTF_8).lines()) {
            when {
                line.startsWith("#EXTINF:") -> {
                    segmentDurationSec =
                        line
                            .removePrefix("#EXTINF:")
                            .substringBefore(',')
                            .trim()
                            .toDoubleOrNull() ?: 60.0
                }

                line.isNotBlank() && !line.startsWith("#") -> {
                    val segmentUrl =
                        if (line.startsWith("http://") || line.startsWith("https://")) {
                            line
                        } else {
                            basePath.newBuilder().addPathSegments(line.trimStart('/')).build().toString()
                        }
                    segments.add((accumulatedSec * 1_000_000).toLong() to segmentUrl)
                    accumulatedSec += segmentDurationSec
                }
            }
        }
        return segments
    }

    private fun shiftVttTimestamps(
        body: String,
        offsetMs: Long,
    ): String {
        val timestampPattern = Regex("""(\d{2}:\d{2}:\d{2}\.\d{3}) --> (\d{2}:\d{2}:\d{2}\.\d{3})""")
        return body.replace(timestampPattern) { match ->
            val start = parseVttTimestamp(match.groupValues[1]) + offsetMs
            val end = parseVttTimestamp(match.groupValues[2]) + offsetMs
            "${formatVttTimestamp(start)} --> ${formatVttTimestamp(end)}"
        }
    }

    private fun parseVttTimestamp(value: String): Long {
        val parts = value.split(':', '.')
        if (parts.size != 4) return 0L
        val hours = parts[0].toLong()
        val minutes = parts[1].toLong()
        val seconds = parts[2].toLong()
        val millis = parts[3].toLong()
        return (((hours * 60) + minutes) * 60 + seconds) * 1000 + millis
    }

    private fun formatVttTimestamp(ms: Long): String {
        val hours = ms / 3_600_000
        val minutes = (ms % 3_600_000) / 60_000
        val seconds = (ms % 60_000) / 1000
        val millis = ms % 1000
        return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
    }
}

@OptIn(UnstableApi::class)
object SecondarySubtitleParser {
    fun parse(
        bytes: ByteArray,
        deliveryFormat: String,
        stream: MediaStream?,
        assHandler: AssHandler? = null,
    ): List<TimedSubtitleCue> {
        val mimeTypes =
            buildList {
                subtitleMimeTypeForDeliveryFormat(deliveryFormat)?.let { add(it) }
                stream?.codec?.let { subtitleMimeTypes[it] }?.let { add(it) }
                add(MimeTypes.APPLICATION_SUBRIP)
                add(MimeTypes.TEXT_VTT)
                add(MimeTypes.TEXT_SSA)
            }.distinct()

        val useAssParser =
            assHandler != null &&
                (
                    deliveryFormat.lowercase() in setOf("ass", "ssa") ||
                        stream?.codec?.lowercase() in setOf("ass", "ssa")
                )
        val parserFactory: SubtitleParser.Factory =
            if (useAssParser) {
                AssSubtitleParserFactory(assHandler!!)
            } else {
                DefaultSubtitleParserFactory()
            }

        for (mimeType in mimeTypes) {
            val cues = parseWithMimeType(bytes, mimeType, parserFactory)
            if (cues.isNotEmpty()) {
                Timber.i("Parsed ${cues.size} secondary subtitle cues using $mimeType (delivery=$deliveryFormat)")
                return cues
            }
        }
        Timber.w("Failed to parse secondary subtitles for delivery format $deliveryFormat")
        return emptyList()
    }

    private fun parseWithMimeType(
        bytes: ByteArray,
        mimeType: String,
        parserFactory: SubtitleParser.Factory,
    ): List<TimedSubtitleCue> {
        val format = Format.Builder().setSampleMimeType(mimeType).build()
        if (!parserFactory.supportsFormat(format)) {
            return emptyList()
        }
        val parser = parserFactory.create(format)
        val subtitle = parser.parseToLegacySubtitle(bytes, 0, bytes.size)
        parser.reset()
        val result = mutableListOf<TimedSubtitleCue>()
        for (i in 0 until subtitle.getEventTimeCount()) {
            val eventTimeUs = subtitle.getEventTime(i)
            val nextEventTimeUs =
                if (i + 1 < subtitle.getEventTimeCount()) {
                    subtitle.getEventTime(i + 1)
                } else {
                    Long.MAX_VALUE
                }
            subtitle.getCues(eventTimeUs).forEach { cue ->
                result.add(TimedSubtitleCue(eventTimeUs, nextEventTimeUs, cue))
            }
        }
        return result
    }

    fun cuesAtPosition(
        allCues: List<TimedSubtitleCue>,
        positionMs: Long,
    ): List<Cue> {
        val positionUs = positionMs * 1000
        return allCues
            .filter { it.startTimeUs <= positionUs && positionUs < it.endTimeUs }
            .map { it.cue }
    }
}
