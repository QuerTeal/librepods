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

package me.kavishdevar.librepods.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecordingState(
    val active: Boolean = false,
    val startedAtMillis: Long = 0,
    /** Microphone level of the latest decoded block, 0..1. */
    val level: Float = 0f,
    /** Bumped whenever a recording is saved or deleted, so lists reload. */
    val revision: Int = 0
)

/** The AirPods microphone recorder's live state, kept by the service and shown by the UI. */
object RecordingMonitor {
    private val _state = MutableStateFlow(RecordingState())
    val state = _state.asStateFlow()

    fun started(atMillis: Long = System.currentTimeMillis()) =
        _state.update { it.copy(active = true, startedAtMillis = atMillis, level = 0f) }

    fun level(level: Float) = _state.update { if (it.active) it.copy(level = level) else it }

    fun stopped() = _state.update { it.copy(active = false, level = 0f) }

    fun changed() = _state.update { it.copy(revision = it.revision + 1) }
}

/** Recordings are WAV files in the app's private storage. */
object Recordings {
    private fun dir(context: Context) = File(context.filesDir, "recordings").apply { mkdirs() }

    fun newFile(context: Context): File = File(
        dir(context),
        "LibrePods_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.wav"
    )

    fun list(context: Context): List<File> =
        dir(context).listFiles { f -> f.extension == "wav" }?.sortedByDescending { it.lastModified() } ?: emptyList()

    /** Length from the WAV header: data size over the byte rate. */
    fun durationMillis(file: File): Long = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(28)
            val byteRate = Integer.reverseBytes(raf.readInt())
            raf.seek(40)
            val dataSize = Integer.reverseBytes(raf.readInt()).toLong() and 0xFFFFFFFFL
            if (byteRate > 0) dataSize * 1000 / byteRate else 0L
        }
    }.getOrDefault(0L)

    fun delete(context: Context, file: File) {
        if (file.parentFile == dir(context)) file.delete()
        RecordingMonitor.changed()
    }
}
