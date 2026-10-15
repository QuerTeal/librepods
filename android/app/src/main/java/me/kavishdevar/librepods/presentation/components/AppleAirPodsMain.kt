// SPDX-License-Identifier: GPL-3.0-or-later
package me.kavishdevar.librepods.presentation.components

import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.painter.Painter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.presentation.theme.AppleColors
import me.kavishdevar.librepods.presentation.theme.AppleMetrics
import me.kavishdevar.librepods.presentation.theme.AppleShape
import me.kavishdevar.librepods.presentation.theme.sfProFamily
import kotlin.math.roundToInt

/** SF Symbols used by the iOS 27 AirPods page (the font's private-use code points). */
internal object AppleGlyphs {
    const val SPEAKER = "􀊩"       // speaker.wave.3.fill
    const val EAR = "􀞇"           // ear.fill
    const val GESTURE = "􀖔"       // hand.tap.fill
    const val ACCESSIBILITY = "􀕽" // accessibility
    const val BATTERY = "􀛨"       // battery.100
    const val STATUS = "􀞜"        // checkmark.shield.fill
}

/** The rounded colored square iOS Settings puts in front of a navigation row. */
@Composable
internal fun AppleIconTile(glyph: String, color: Color) {
    Box(
        modifier = Modifier
            .size(AppleMetrics.IconTile)
            .background(color, AppleShape(AppleMetrics.IconTileRadius)),
        contentAlignment = Alignment.Center
    ) {
        Text(glyph, style = TextStyle(fontFamily = sfProFamily, fontSize = 15.sp, color = Color.White))
    }
}

/** [AppleIconTile] with a drawable for glyphs the bundled SF font lacks. */
@Composable
internal fun AppleIconTile(painter: Painter, color: Color) {
    Box(
        modifier = Modifier
            .size(AppleMetrics.IconTile)
            .background(color, AppleShape(AppleMetrics.IconTileRadius)),
        contentAlignment = Alignment.Center
    ) {
        Icon(painter, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

/**
 * The iOS 27 AirPods volume bar: a 56 pt capsule whose fill follows the phone's media volume,
 * set by dragging or tapping anywhere on it.
 */
@Composable
fun AppleVolumeSlider(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val max = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var level by remember { mutableIntStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }

    DisposableEffect(audio) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                level = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            ContextCompat.RECEIVER_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    fun apply(fraction: Float) {
        val target = (fraction.coerceIn(0f, 1f) * max).roundToInt()
        if (target != level) {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            level = target
        }
    }

    val fraction = dragFraction ?: (level.toFloat() / max)
    val label = stringResource(R.string.airpods_volume)
    Column(modifier = modifier) {
        AppleSectionHeader(label)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(AppleMetrics.VolumeHeight)
                .clip(AppleShape(AppleMetrics.VolumeHeight / 2))
                .background(AppleColors.volumeTrack())
                .semantics {
                    contentDescription = label
                    progressBarRangeInfo = ProgressBarRangeInfo(level.toFloat(), 0f..max.toFloat(), max - 1)
                    setProgress { value -> apply(value / max); true }
                }
                .pointerInput(max) {
                    detectTapGestures { offset -> apply(offset.x / size.width) }
                }
                .pointerInput(max) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> dragFraction = offset.x / size.width },
                        onDragEnd = { dragFraction = null },
                        onDragCancel = { dragFraction = null }
                    ) { change, _ ->
                        val f = (change.position.x / size.width).coerceIn(0f, 1f)
                        dragFraction = f
                        apply(f)
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .background(AppleColors.volumeFill())
            )
            Text(
                AppleGlyphs.SPEAKER,
                style = TextStyle(fontFamily = sfProFamily, fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 18.dp)
            )
        }
    }
}
