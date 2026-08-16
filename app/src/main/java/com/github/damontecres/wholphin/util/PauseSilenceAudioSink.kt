package com.github.damontecres.wholphin.util

import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Keeps the player's AudioTrack running while video is paused by writing silence instead of
 * calling [AudioSink.pause]. HDMI receivers drop lock after AudioTrack.pause(), which is why
 * sound takes a few seconds to return after a long pause.
 */
@OptIn(UnstableApi::class)
class PauseSilenceAudioSink(
    sink: AudioSink,
) : ForwardingAudioSink(sink) {
    private val lock = Any()

    @Volatile
    private var feedingSilence = false

    @Volatile
    private var pausedPositionUs = C.TIME_UNSET
    private var inputFormat: Format? = null
    private var handler: Handler? = null
    private var silenceBuffer: ByteBuffer = EMPTY_BUFFER
    private var silenceDurationUs = 20_000L
    private var presentationTimeUs = 0L

    override fun configure(
        inputFormat: Format,
        specifiedBufferSize: Int,
        outputChannels: IntArray?,
    ) {
        stopSilence()
        this.inputFormat = inputFormat
        prepareSilenceBuffer(inputFormat)
        super.configure(inputFormat, specifiedBufferSize, outputChannels)
    }

    override fun play() {
        val wasFeeding = feedingSilence
        stopSilence()
        if (wasFeeding) {
            handleDiscontinuity()
        }
        pausedPositionUs = C.TIME_UNSET
        super.play()
    }

    override fun pause() {
        val looper = Looper.myLooper()
        if (looper == null || silenceBuffer.capacity() == 0) {
            if (silenceBuffer.capacity() == 0) {
                Timber.w(
                    "No pause-silence frames for %s, HDMI audio may drop",
                    inputFormat?.sampleMimeType,
                )
            }
            super.pause()
            return
        }
        synchronized(lock) {
            if (feedingSilence) {
                return
            }
            pausedPositionUs =
                super.getCurrentPositionUs(true).let { position ->
                    if (position == C.TIME_UNSET) 0L else position
                }
            presentationTimeUs = pausedPositionUs
            handler = Handler(looper)
            feedingSilence = true
        }
        Timber.d(
            "Keeping audio track alive with silence while paused (%s)",
            inputFormat?.sampleMimeType,
        )
        silenceRunnable.run()
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val frozen = pausedPositionUs
        if (feedingSilence && frozen != C.TIME_UNSET) {
            return frozen
        }
        return super.getCurrentPositionUs(sourceEnded)
    }

    override fun flush() {
        stopSilence()
        pausedPositionUs = C.TIME_UNSET
        super.flush()
    }

    override fun reset() {
        stopSilence()
        pausedPositionUs = C.TIME_UNSET
        super.reset()
    }

    override fun release() {
        stopSilence()
        pausedPositionUs = C.TIME_UNSET
        super.release()
    }

    private val silenceRunnable =
        object : Runnable {
            override fun run() {
                if (!feedingSilence) {
                    return
                }
                val buffer = silenceBuffer
                if (!buffer.hasRemaining()) {
                    buffer.rewind()
                }
                val handled =
                    try {
                        super@PauseSilenceAudioSink.handleBuffer(
                            buffer,
                            presentationTimeUs,
                            1,
                        )
                    } catch (ex: Exception) {
                        Timber.w(ex, "Pause silence write failed, falling back to AudioTrack.pause()")
                        stopSilence()
                        super@PauseSilenceAudioSink.pause()
                        return
                    }
                if (!feedingSilence) {
                    return
                }
                if (handled) {
                    presentationTimeUs += silenceDurationUs
                    buffer.rewind()
                }
                handler?.postDelayed(
                    this,
                    if (handled) {
                        (silenceDurationUs / 1000L).coerceAtLeast(10L)
                    } else {
                        10L
                    },
                )
            }
        }

    private fun stopSilence() {
        synchronized(lock) {
            feedingSilence = false
            handler?.removeCallbacks(silenceRunnable)
            handler = null
        }
    }

    private fun prepareSilenceBuffer(format: Format) {
        val mime = format.sampleMimeType
        if (mime != null && mime != MimeTypes.AUDIO_RAW) {
            val encoded = encodedSilence(format)
            if (encoded == null) {
                silenceBuffer = EMPTY_BUFFER
                return
            }
            val (bytes, durationUs) = encoded
            silenceBuffer =
                ByteBuffer
                    .allocateDirect(bytes.size)
                    .order(ByteOrder.nativeOrder())
                    .put(bytes)
                    .also { it.flip() }
            silenceDurationUs = durationUs
            return
        }
        val sampleRate = format.sampleRate.takeIf { it > 0 } ?: 48_000
        val channelCount = format.channelCount.takeIf { it > 0 } ?: 2
        val pcmEncoding =
            if (format.pcmEncoding != Format.NO_VALUE && format.pcmEncoding != C.ENCODING_INVALID) {
                format.pcmEncoding
            } else {
                C.ENCODING_PCM_16BIT
            }
        val bytesPerFrame =
            try {
                Util.getPcmFrameSize(pcmEncoding, channelCount)
            } catch (_: Exception) {
                channelCount * 2
            }
        val frames = (sampleRate / 50).coerceAtLeast(1)
        silenceBuffer =
            ByteBuffer
                .allocateDirect(frames * bytesPerFrame)
                .order(ByteOrder.nativeOrder())
        silenceDurationUs = frames * 1_000_000L / sampleRate
    }

    private fun encodedSilence(format: Format): Pair<ByteArray, Long>? =
        when (format.sampleMimeType) {
            MimeTypes.AUDIO_AC3 -> SILENT_AC3 to 32_000L
            MimeTypes.AUDIO_E_AC3,
            MimeTypes.AUDIO_E_AC3_JOC,
            -> SILENT_EAC3 to 32_000L
            MimeTypes.AUDIO_DTS,
            MimeTypes.AUDIO_DTS_HD,
            MimeTypes.AUDIO_DTS_EXPRESS,
            MimeTypes.AUDIO_DTS_X,
            -> SILENT_DTS to 10_667L
            else -> null
        }

    companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0)

        // 48 kHz stereo 64 kbps silent frames generated with ffmpeg anullsrc.
        private val SILENT_AC3 =
            hex(
                "0b77b49e084043e106f00108080821010104571f3e7cf9f3e7cf9f3e7cfbfe75" +
                    "7cf9f3e7cf9f3e7cf8ff9d5f3e7cf9f3e7cf9f3e32ff0244892000000001b5f3" +
                    "e7cf9b6db6db6f1e3c0000000daf9f3e7cdb6db6db78f1e1800000000006d7cf" +
                    "9f3e6db6db6dbc78f000000036be7cf9f36db6db6de3c78600000000001b5f3e" +
                    "7cf9b6db6db6f1e3c0000000daf9f3e7cdb6db6db78f1e1800000000006d7cf9" +
                    "f3e6db6db6dbc78f000000036be7cf9f36db6db6de3c78600000000001b5f3e7" +
                    "cf9b6db6db6f1e3c0000000daf9f3e7cdb6db6db78f1e1800000000006d7cf9f" +
                    "3e6db6db6dbc78f000000036be7cf9f36db6db6de3c78000000000000000e256",
            )
        private val SILENT_EAC3 =
            hex(
                "0b77007f3487c00020000000418000040404010101018f9f3e7cf9f3e7cf9f3e" +
                    "7dff3abe7cf9f3e7cf9f3e7c7fceaf9f3e7cf9f3e7cf9f00000000037cf9f3e6" +
                    "db6db6dbc78f1e3c0000000df3e7cc9b6db6db6f1e3c78f0000000006f9f3e7c" +
                    "db6db6db78f1e3c780000001be7cf9936db6db6de3c78f1e000000000df3e7cf" +
                    "9b6db6db6f1e3c78f000000037cf9f326db6db6dbc78f1e3c000000001be7cf9" +
                    "f36db6db6de3c78f1e00000006f9f3e64db6db6db78f1e3c780000000037cf9f" +
                    "3e6db6db6dbc78f1e3c0000000df3e7cc9b6db6db6f1e3c78f0000000006f9f3" +
                    "e7cdb6db6db78f1e3c780000001be7cf9936db6db6de3c78f1e000000000ce0e",
            )

        private val SILENT_DTS =
            hex(
                "7ffe8001fc3c3ff0b5e001380003ef7fe006db1fe1edfedb6000000020000000" +
                    "0000000001ce7be0f7b9ce6b5ad6b18c5a9283102108421085ce7be0f7b9ce6b" +
                    "5ad6b18c5a92831021084210840000000000000000a142850d142850a141e3c7" +
                    "8f1e244890408000000000000000000000a142850d142850a141e3c78f1e2448" +
                    "9040800000000000000000000000000000000000000000000000000000000000" +
                    "0000000000000000000000000000000000000000000000000000000000000000" +
                    "0000000000000000000000000000000000000000000000000000000000000000" +
                    "0000000000000000000000000000000000000000000000000000000000000000" +
                    "0000000000000000000000000000000000000000000000000000000000000000" +
                    "0000000000000000000000000000000000000000000000000000000000000000" +
                    "0000000000000000000000000000000000000000000222222222492491555400" +
                    "0000000000000000000000000000000000000000000000000000000000000000" +
                    "0000000000000000000000000000000000000000000000000000000000000000" +
                    "0000000000000000000000000000000000000000000000000000000000000000" +
                    "0000000000000000000000000000000000000000000000000000000000000000" +
                    "0000000000000000000000000000000000000000000000000000000000000000" +
                    "0000000000000000000000000000000000000000000000000000000000000000" +
                    "0000000000000000000000000000000000022222222249249155540000000000" +
                    "0000000000000000000000000000000000000000000000000000000000000000" +
                    "0000000000000000000000000000000000000000000000000000000000000000" +
                    "0000000000000000000000000000000000000000000000000000000000000000" +
                    "0000000000000000000000000000000000000000000000000000000000000000" +
                    "0000000000000000000000000000000000000000000000000000000000000000" +
                    "0000000000000000000000000000000000000000000000000000000000000000" +
                    "0000000000000000000000000002222222224924915554000000000000000000" +
                    "0000000000000000000000000000000000000000000000000000000000000000" +
                    "0000000000000000000000000000000000000000000000000000000000000000" +
                    "0000000000000000000000000000000000000000000000000000000000000000" +
                    "0000000000000000000000000000000000000000000000000000000000000000" +
                    "0000000000000000000000000000000000000000000000000000000000000000" +
                    "0000000000000000000000000000000000000000000000000000000000000000" +
                    "0000000000000000000222222222492491555400000000000000000003fffc00",
            )

        private fun hex(value: String): ByteArray {
            val hex = value.filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
            return ByteArray(hex.length / 2) { index ->
                hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        }
    }
}
