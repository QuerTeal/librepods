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

package me.kavishdevar.librepods.presentation.screens

import android.content.Intent
import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.data.Recordings
import me.kavishdevar.librepods.presentation.components.StyledList
import me.kavishdevar.librepods.presentation.components.StyledListItem
import me.kavishdevar.librepods.presentation.components.screenBottomPadding
import me.kavishdevar.librepods.presentation.components.screenTopPadding
import me.kavishdevar.librepods.presentation.theme.LocalOneUI
import me.kavishdevar.librepods.presentation.viewmodel.AirPodsViewModel
import java.io.File
import java.text.DateFormat
import java.util.Date

/** The iOS Voice Memos record red. */
private val RecordRed = Color(0xFFFF3B30)

@Composable
fun RecorderScreen(viewModel: AirPodsViewModel) {
    val context = LocalContext.current
    val backdrop = rememberLayerBackdrop()
    val state by viewModel.uiState.collectAsState()
    val recording by viewModel.recording.collectAsState()
    val connected = state.isLocallyConnected
    val files = remember(recording.revision) { viewModel.recordings() }

    var playing by remember { mutableStateOf<File?>(null) }
    val player = remember { MediaPlayer() }
    DisposableEffect(Unit) { onDispose { player.release() } }
    fun stopPlayback() {
        if (playing != null) runCatching { player.stop() }
        player.reset()
        playing = null
    }
    fun play(file: File) {
        val wasPlaying = playing == file
        stopPlayback()
        if (wasPlaying) return
        runCatching {
            player.setDataSource(file.path)
            player.setOnCompletionListener { stopPlayback() }
            player.prepare()
            player.start()
            playing = file
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .layerBackdrop(backdrop)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(screenTopPadding(material = 0.dp)))

        CardSurface {
            RecorderControls(
                active = recording.active,
                startedAtMillis = recording.startedAtMillis,
                level = recording.level,
                enabled = connected || recording.active,
                onToggle = {
                    if (recording.active) viewModel.stopRecording()
                    else {
                        stopPlayback()
                        if (!viewModel.startRecording())
                            Toast.makeText(context, R.string.recorder_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
        Text(
            text = stringResource(if (connected || recording.active) R.string.recorder_description else R.string.recorder_connect),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = if (LocalOneUI.current) 18.dp else 20.dp, vertical = 8.dp)
        )

        if (files.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            // StyledList collects its rows once per composition; rebuild it when the files change.
            key(files, playing) { StyledList(title = stringResource(R.string.recordings)) {
                files.forEach { file ->
                    StyledListItem(
                        name = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(file.lastModified())),
                        description = if (playing == file) stringResource(R.string.recording_stop_playback)
                            else formatDuration(Recordings.durationMillis(file)),
                        onClick = { play(file) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { share(context, file) }) {
                                    Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.share),
                                        tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = {
                                    if (playing == file) stopPlayback()
                                    viewModel.deleteRecording(file)
                                }) {
                                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete),
                                        tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    )
                }
            } }
        }

        Spacer(modifier = Modifier.height(screenBottomPadding(material = 0.dp)))
    }
}

/** Elapsed time, a level meter and the record button, after iOS Voice Memos. */
@Composable
private fun RecorderControls(active: Boolean, startedAtMillis: Long, level: Float, enabled: Boolean, onToggle: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) {
        while (active) {
            now = System.currentTimeMillis()
            delay(200)
        }
    }
    val shownLevel by animateFloatAsState(if (active) level else 0f, label = "level")
    val innerSize by animateDpAsState(if (active) 28.dp else 56.dp, label = "button")
    val innerCorner by animateDpAsState(if (active) 6.dp else 28.dp, label = "corner")
    val label = stringResource(if (active) R.string.recorder_stop else R.string.recorder_start)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = formatDuration(if (active) now - startedAtMillis else 0L),
            style = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Light, fontFeatureSettings = "tnum"),
            color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        LevelMeter(shownLevel)
        Spacer(modifier = Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .size(72.dp)
                .border(3.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clip(CircleShape)
                .clickable(enabled = enabled, onClick = onToggle)
                .semantics { role = Role.Button; contentDescription = label },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(innerSize)
                    .background(if (enabled) RecordRed else RecordRed.copy(alpha = 0.35f), RoundedCornerShape(innerCorner))
            )
        }
    }
}

/** A row of bars that rises with the microphone level. */
@Composable
private fun LevelMeter(level: Float) {
    // A quiet room reads about -48 dBFS on the buds and speech about -25; map -60..-10 dB onto the bars.
    val db = if (level > 0f) 20 * kotlin.math.log10(level) else -100f
    val lit = (((db + 60f) / 50f).coerceIn(0f, 1f) * BARS).toInt()
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(BARS) { i ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(14.dp)
                    .background(
                        if (i < lit) RecordRed else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

private const val BARS = 30

private fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000).coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

private fun share(context: android.content.Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("audio/wav")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
