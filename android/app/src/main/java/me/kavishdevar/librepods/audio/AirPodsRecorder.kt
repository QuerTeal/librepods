/*
    LibrePods - AirPods liberated from Apple’s ecosystem
    Copyright (C) 2025 LibrePods contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package me.kavishdevar.librepods.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlin.math.sqrt

/**
 * Decodes the AirPods microphone stream (AAC-ELD) and writes it to a 16-bit PCM WAV file.
 * Frames arrive on the socket thread; decoding runs on its own thread in arrival order.
 */
class AirPodsRecorder(private val file: File, private val onLevel: (Float) -> Unit) {
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "AirPodsRecorder") }
    private val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    private val info = MediaCodec.BufferInfo()
    private val wav = WavWriter(file, OUTPUT_SAMPLE_RATE)
    private var units = 0
    private var pcmBytes = 0L

    // The buds stop sending after about a quarter second unless their A2DP stream is running
    // (upstream's Linux client resets A2DP instead), so play silence while recording.
    private val silence = android.media.AudioTrack.Builder()
        .setAudioAttributes(android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .setAudioFormat(android.media.AudioFormat.Builder()
            .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(48_000)
            .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_STEREO).build())
        .setTransferMode(android.media.AudioTrack.MODE_STATIC)
        .setBufferSizeInBytes(48_000 * 4)
        .build().apply {
            write(ByteArray(48_000 * 4), 0, 48_000 * 4)
            setLoopPoints(0, 48_000, -1)
            play()
        }

    init {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectELD)
            setByteBuffer("csd-0", ByteBuffer.wrap(AUDIO_SPECIFIC_CONFIG))
        }
        codec.configure(format, null, null, 0)
        codec.start()
    }

    fun submit(accessUnit: ByteArray) = worker.execute {
        units++
        runCatching { decode(accessUnit) }.onFailure { Log.w(TAG, "Decode failed: ${it.message}") }
    }

    /** Finishes decoding what has arrived, closes the file and returns it. */
    fun finish(): File {
        runCatching { silence.stop(); silence.release() }
        worker.execute {
            runCatching { codec.stop() }
            codec.release()
            wav.close()
            Log.i(TAG, "Recorded $units access units, $pcmBytes PCM bytes")
        }
        worker.shutdown()
        worker.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)
        return file
    }

    private fun decode(accessUnit: ByteArray) {
        val input = codec.dequeueInputBuffer(10_000)
        if (input >= 0) {
            codec.getInputBuffer(input)?.apply { clear(); put(accessUnit) }
            codec.queueInputBuffer(input, 0, accessUnit.size, 0, 0)
        }
        while (true) {
            val output = codec.dequeueOutputBuffer(info, 0)
            when {
                output >= 0 -> {
                    val pcm = ByteArray(info.size)
                    codec.getOutputBuffer(output)?.apply {
                        position(info.offset)
                        limit(info.offset + info.size)
                        get(pcm)
                    }
                    codec.releaseOutputBuffer(output, false)
                    wav.write(pcm)
                    pcmBytes += pcm.size
                    onLevel(level(pcm))
                }
                output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                else -> break
            }
        }
    }

    /** RMS of a 16-bit PCM block, 0..1. */
    private fun level(pcm: ByteArray): Float {
        val samples = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        if (!samples.hasRemaining()) return 0f
        var sum = 0.0
        val count = samples.remaining()
        while (samples.hasRemaining()) {
            val s = samples.get() / 32768.0
            sum += s * s
        }
        return sqrt(sum / count).toFloat().coerceIn(0f, 1f)
    }

    companion object {
        private const val TAG = "AirPodsRecorder"
        private const val SAMPLE_RATE = 48_000

        /**
         * The buds' real rate. Their frames are 480 samples and time-stamped 7.5 ms apart, so the
         * audio is 64 kHz although the config below (which the decoder needs) says 48 kHz.
         */
        const val OUTPUT_SAMPLE_RATE = 64_000

        /** AAC-ELD, 48 kHz (see [OUTPUT_SAMPLE_RATE]), mono, 480-sample frames. */
        private val AUDIO_SPECIFIC_CONFIG = byteArrayOf(0xF8.toByte(), 0xE6.toByte(), 0x30, 0x00)
    }
}

/** A mono 16-bit PCM WAV file; the header is completed on [close]. */
class WavWriter(file: File, private val sampleRate: Int) : AutoCloseable {
    private val out = RandomAccessFile(file, "rw").apply { setLength(0); write(ByteArray(HEADER_SIZE)) }
    private var dataSize = 0L

    fun write(pcm: ByteArray) {
        out.write(pcm)
        dataSize += pcm.size
    }

    override fun close() {
        out.seek(0)
        out.write(header(sampleRate, dataSize))
        out.close()
    }

    companion object {
        private const val HEADER_SIZE = 44

        fun header(sampleRate: Int, dataSize: Long): ByteArray = ByteBuffer.allocate(HEADER_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt((36 + dataSize).toInt())
            .put("WAVE".toByteArray())
            .put("fmt ".toByteArray()).putInt(16)
            .putShort(1) // PCM
            .putShort(1) // mono
            .putInt(sampleRate)
            .putInt(sampleRate * 2) // byte rate
            .putShort(2) // block align
            .putShort(16) // bits per sample
            .put("data".toByteArray()).putInt(dataSize.toInt())
            .array()
    }
}
