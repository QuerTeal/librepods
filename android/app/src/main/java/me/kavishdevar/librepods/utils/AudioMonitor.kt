package me.kavishdevar.librepods.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRouting
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import me.kavishdevar.librepods.data.AudioMonitorPolicy
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Foreground phone microphone to the selected Bluetooth output. No file or network storage. */
class AudioMonitor(private val context: Context, private val stateChanged: (Boolean, Boolean) -> Unit) : Closeable {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val lifecycle = Any()
    private var generation = 0L
    private var active: Session? = null
    private var closed = false
    @Volatile var gain = 0.25f

    private inner class Session(val record: AudioRecord, val track: AudioTrack,
                                val focus: AudioFocusRequest, val token: Long, val outputId: Int) : Closeable {
        private val closed = AtomicBoolean(false)
        val monitoring = AtomicBoolean(false)
        private val routingListener = AudioRouting.OnRoutingChangedListener {
            if (monitoring.get() && current(token) && !routesMatch(track, record, outputId)) {
                stop()
                stateChanged(false, true)
            }
        }
        init {
            record.addOnRoutingChangedListener(routingListener, handler)
            track.addOnRoutingChangedListener(routingListener, handler)
        }
        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            monitoring.set(false)
            runCatching { track.pause() }; runCatching { track.flush() }
            runCatching { record.removeOnRoutingChangedListener(routingListener) }
            runCatching { track.removeOnRoutingChangedListener(routingListener) }
            runCatching { record.stop() }; runCatching { record.release() }
            runCatching { track.stop() }; runCatching { track.release() }
            runCatching { audio.abandonAudioFocusRequest(focus) }
        }
    }

    private fun current(token: Long) = synchronized(lifecycle) { !closed && generation == token }
    private fun notify(token: Long, running: Boolean, failed: Boolean) {
        handler.post { if (current(token)) stateChanged(running, failed) }
    }

    @SuppressLint("MissingPermission")
    fun start(address: String) {
        stop()
        val token = synchronized(lifecycle) { if (closed) return; ++generation }
        worker.execute {
            var resource: Session? = null
            var record: AudioRecord? = null
            var track: AudioTrack? = null
            var focus: AudioFocusRequest? = null
            try {
                check(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                check(context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
                check(address.isNotBlank() && current(token))
                val output = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
                    it.type in setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET) &&
                        it.address.equals(address, ignoreCase = true)
                } ?: error("selected output unavailable")
                val input = audio.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC
                } ?: error("phone microphone unavailable")
                val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attributes).setOnAudioFocusChangeListener({ change ->
                        if (change < 0 && current(token)) { stop(); stateChanged(false, true) }
                    }, handler).build()
                check(current(token) && audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                val inputSize = AudioRecord.getMinBufferSize(48000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val outputSize = AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                check(inputSize > 0 && outputSize > 0)
                val bufferBytes = maxOf(inputSize, outputSize, 4096)
                record = AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(48000).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()).setBufferSizeInBytes(bufferBytes).build()
                track = AudioTrack.Builder().setAudioAttributes(attributes)
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(48000).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setBufferSizeInBytes(bufferBytes)
                    .setTransferMode(AudioTrack.MODE_STREAM).build()
                check(record.setPreferredDevice(input) && track.setPreferredDevice(output))
                resource = Session(record, track, focus, token, output.id)
                synchronized(lifecycle) { if (!current(token)) error("cancelled"); active = resource }
                val recording = record
                val playback = track
                recording.startRecording()
                playback.play()
                val samples = ShortArray(bufferBytes / 2)
                check(playback.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING) > 0) // Silence during route establishment.
                var routed = false
                repeat(20) {
                    if (!current(token)) error("cancelled")
                    if (routesMatch(playback, recording, output.id)) routed = true
                    if (!routed) Thread.sleep(25)
                }
                check(routed)
                resource.monitoring.set(true)
                check(routesMatch(playback, recording, output.id))
                notify(token, true, false)
                while (current(token)) {
                    val count = recording.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING)
                    check(count > 0)
                    check(current(token) && routesMatch(playback, recording, output.id))
                    AudioMonitorPolicy.applyGain(samples, count, gain)
                    var written = 0
                    while (written < count && current(token)) {
                        check(routesMatch(playback, recording, output.id))
                        val result = playback.write(samples, written, count - written, AudioTrack.WRITE_BLOCKING)
                        check(result > 0)
                        written += result
                    }
                    samples.fill(0)
                }
            } catch (_: Exception) { notify(token, false, true) }
            finally {
                synchronized(lifecycle) { if (active === resource) active = null }
                if (resource != null) resource.close() else {
                    record?.let { runCatching { it.release() } }
                    track?.let { runCatching { it.release() } }
                    focus?.let { runCatching { audio.abandonAudioFocusRequest(it) } }
                }
            }
        }
    }

    private fun routesMatch(track: AudioTrack, record: AudioRecord, target: Int): Boolean {
        val outputs = if (Build.VERSION.SDK_INT >= 36) track.routedDevices.map { it.id }
            else listOfNotNull(track.routedDevice?.id)
        return AudioMonitorPolicy.canForward(target, outputs,
            record.routedDevice?.type == AudioDeviceInfo.TYPE_BUILTIN_MIC)
    }

    fun stop() {
        val previous = synchronized(lifecycle) { generation++; active.also { active = null } }
        previous?.close()
    }
    override fun close() {
        synchronized(lifecycle) { closed = true }
        stop()
        worker.shutdownNow()
    }
}
