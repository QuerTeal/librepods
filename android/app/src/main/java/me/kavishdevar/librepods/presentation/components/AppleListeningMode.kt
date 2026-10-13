// SPDX-License-Identifier: GPL-3.0-or-later
package me.kavishdevar.librepods.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.delay
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.data.NoiseControlMode
import me.kavishdevar.librepods.presentation.theme.AppleMetrics
import me.kavishdevar.librepods.presentation.theme.AppleShape
import me.kavishdevar.librepods.presentation.theme.AppleText
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The iOS 27 listening-mode control, from the user's iPadOS 27 recordings:
 * - The selection pill slides between the modes when tapped or dragged.
 * - While Adaptive is selected, the side modes shrink to 20 % of the track and the middle 60 % becomes
 *   a darker track between a dotted (more ambient) and a solid (more reduction) ring. Dragging the
 *   Adaptive pill along it sets the adaptive strength; held, the pill widens and turns translucent.
 * - A mode that needs both buds in the ears isn't selected; the label row shows [wearGuidance]'s
 *   message for a moment instead.
 */
@Composable
internal fun AppleListeningModeControl(
    showOff: Boolean,
    mode: NoiseControlMode,
    onModeChange: (NoiseControlMode) -> Unit,
    adaptiveStrength: Int?,
    onAdaptiveStrengthChange: ((Int) -> Unit)?,
    wearGuidance: (NoiseControlMode) -> String?,
) {
    val dark = isSystemInDarkTheme()
    val haptics = LocalHapticFeedback.current
    val modes = buildList {
        if (showOff) add(NoiseControlMode.OFF)
        add(NoiseControlMode.TRANSPARENCY); add(NoiseControlMode.ADAPTIVE); add(NoiseControlMode.NOISE_CANCELLATION)
    }
    val selected = if (mode in modes) mode else NoiseControlMode.TRANSPARENCY
    val adaptiveIndex = modes.indexOf(NoiseControlMode.ADAPTIVE)
    val strengthEnabled = adaptiveStrength != null && onAdaptiveStrengthChange != null

    var guidance by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(guidance) { if (guidance != null) { delay(2500); guidance = null } }

    val latestMode by rememberUpdatedState(selected)
    val pickNow: (NoiseControlMode) -> Unit = { target ->
        val message = wearGuidance(target)
        if (message != null && target != latestMode) {
            guidance = message
            haptics.performHapticFeedback(HapticFeedbackType.Reject)
        } else if (target != latestMode) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onModeChange(target)
        }
    }

    // Gesture handlers keep their first lambdas; these always reach the current wear state and callback.
    val pick by rememberUpdatedState(pickNow)
    val setStrength by rememberUpdatedState(onAdaptiveStrengthChange)

    val trackColor = if (dark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    val subTrackColor = if (dark) Color(0xFF3A3A3C) else Color(0xFFD1D1D6)
    val ringColor = if (dark) Color(0xFF8E8E92) else Color(0xFF8D8D91)
    val iconTint = MaterialTheme.colorScheme.onSurface

    Column {
        AppleSectionHeader(stringResource(R.string.noise_control))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val width = with(density) { maxWidth.toPx() }
            val n = modes.size
            val adaptiveLayout by animateFloatAsState(
                if (selected == NoiseControlMode.ADAPTIVE && strengthEnabled) 1f else 0f,
                spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow), label = "adaptiveLayout")
            // Segment widths: equal, or the Adaptive slider taking 60 % (50 % with Off shown).
            val subFraction = if (n == 3) 0.6f else 0.5f
            val sideWidth = lerp(width / n, width * (1 - subFraction) / (n - 1), adaptiveLayout)
            val subWidth = lerp(width / n, width * subFraction, adaptiveLayout)
            val lefts = FloatArray(n).also { arr ->
                var x = 0f
                for (i in 0 until n) { arr[i] = x; x += if (i == adaptiveIndex) subWidth else sideWidth }
            }
            fun segWidth(i: Int) = if (i == adaptiveIndex) subWidth else sideWidth
            fun center(i: Int) = lefts[i] + segWidth(i) / 2

            // Adaptive pill travel stays inside the slider track.
            val restPillWidth = width * (1 - subFraction) / (n - 1)
            val subLeft = lefts[adaptiveIndex]
            fun strengthCenter(fraction: Float) = subLeft + restPillWidth / 2 + fraction * (subWidth - restPillWidth)

            var dragX by remember { mutableStateOf<Float?>(null) }
            var draggingStrength by remember { mutableStateOf(false) }
            var liveStrength by remember { mutableStateOf<Float?>(null) }
            val strengthFraction = (liveStrength ?: (adaptiveStrength ?: 50) / 100f).coerceIn(0f, 1f)

            val selectedIndex = modes.indexOf(selected)
            // Gesture handlers outlive recompositions; they read the current geometry from here.
            val geo by rememberUpdatedState(Geometry(lefts.copyOf(), sideWidth, subWidth, subLeft, restPillWidth, strengthFraction))
            val targetCenter = when {
                dragX != null -> dragX!!
                selected == NoiseControlMode.ADAPTIVE && strengthEnabled -> lerp(center(adaptiveIndex), strengthCenter(strengthFraction), adaptiveLayout)
                else -> center(selectedIndex)
            }
            val restWidth = if (selected == NoiseControlMode.ADAPTIVE) lerp(segWidth(selectedIndex), restPillWidth, adaptiveLayout) else segWidth(selectedIndex)
            val pillCenter by animateFloatAsState(targetCenter,
                if (dragX != null || draggingStrength) spring(stiffness = Spring.StiffnessHigh) else spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
                label = "pillCenter")
            val pillWidth by animateFloatAsState(if (draggingStrength || dragX != null) restWidth * 1.25f else restWidth,
                spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium), label = "pillWidth")

            val height = AppleMetrics.SegmentHeight
            val inset = AppleMetrics.SegmentInset
            val held = draggingStrength || dragX != null
            val pillColor = when {
                held -> if (dark) Color(0xFF636366).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.72f)
                selected == NoiseControlMode.ADAPTIVE && strengthEnabled && dark -> Color(0xFFE0E0E0)
                dark -> Color.Black
                else -> Color.White
            }
            val adaptivePillLight = selected == NoiseControlMode.ADAPTIVE && strengthEnabled && dark && !held

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .background(trackColor, AppleShape(height / 2))
                    .pointerInput(modes, strengthEnabled) {
                        detectTapGestures { offset ->
                            val g = geo
                            val i = (0 until n).firstOrNull { offset.x < g.lefts[it] + g.width(it, adaptiveIndex) } ?: (n - 1)
                            val target = modes[i]
                            if (target == NoiseControlMode.ADAPTIVE && latestMode == NoiseControlMode.ADAPTIVE && strengthEnabled) {
                                val f = ((offset.x - g.subLeft - g.restPill / 2) / (g.sub - g.restPill)).coerceIn(0f, 1f)
                                setStrength?.invoke(adaptiveLevel(f))
                            } else pick(target)
                        }
                    }
                    .pointerInput(modes, strengthEnabled) {
                        var lastSent = -1
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                val g = geo
                                val onAdaptive = latestMode == NoiseControlMode.ADAPTIVE && strengthEnabled &&
                                    offset.x in g.subLeft..(g.subLeft + g.sub)
                                if (onAdaptive) { draggingStrength = true; liveStrength = g.strength; lastSent = adaptiveLevel(g.strength) }
                                else dragX = offset.x.coerceIn(g.side / 2, size.width - g.side / 2)
                            },
                            onDragEnd = {
                                if (draggingStrength) {
                                    val value = adaptiveLevel(liveStrength ?: 0.5f)
                                    if (value != lastSent) setStrength?.invoke(value)
                                    draggingStrength = false
                                    liveStrength = null
                                } else dragX?.let { x ->
                                    val g = geo
                                    val i = (0 until n).minByOrNull { abs(g.lefts[it] + g.width(it, adaptiveIndex) / 2 - x) } ?: 0
                                    dragX = null
                                    pick(modes[i])
                                }
                            },
                            onDragCancel = { draggingStrength = false; liveStrength = null; dragX = null }
                        ) { change, delta ->
                            change.consume()
                            val g = geo
                            if (draggingStrength) {
                                val f = ((liveStrength ?: 0.5f) + delta / (g.sub - g.restPill)).coerceIn(0f, 1f)
                                liveStrength = f
                                // The AirPods follow the drag one level at a time, with a tick per level.
                                val value = adaptiveLevel(f)
                                if (value != lastSent) {
                                    setStrength?.invoke(value); lastSent = value
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                }
                            } else dragX = ((dragX ?: 0f) + delta).coerceIn(g.side / 2, size.width - g.side / 2)
                        }
                    }
            ) {
                // Adaptive slider track and its end rings.
                if (adaptiveLayout > 0.01f) {
                    Box(
                        Modifier
                            .offset { androidx.compose.ui.unit.IntOffset(subLeft.roundToInt(), 0) }
                            .width(with(density) { subWidth.toDp() })
                            .height(height)
                            .background(subTrackColor.copy(alpha = subTrackColor.alpha * adaptiveLayout), AppleShape(height / 2))
                    )
                    val ringInset = with(density) { 23.5.dp.toPx() }
                    Ring(x = subLeft + ringInset, dotted = true, color = ringColor.copy(alpha = adaptiveLayout), height = height)
                    Ring(x = subLeft + subWidth - ringInset, dotted = false, color = ringColor.copy(alpha = adaptiveLayout), height = height)
                }
                // Selection pill.
                Box(
                    Modifier
                        // The held pill is wider; keep it inside the track at the ends.
                        .offset { androidx.compose.ui.unit.IntOffset((pillCenter - pillWidth / 2).coerceIn(0f, width - pillWidth).roundToInt(), 0) }
                        .width(with(density) { pillWidth.toDp() })
                        .height(height)
                        .padding(inset)
                        .background(pillColor, AppleShape(height / 2))
                        .then(if (dark && !adaptivePillLight) Modifier.border(Dp.Hairline, Color.White.copy(alpha = 0.16f), AppleShape(height / 2))
                            else if (held) Modifier.border(Dp.Hairline, Color.White.copy(alpha = 0.6f), AppleShape(height / 2)) else Modifier)
                )
                // Mode icons; Adaptive's rides on its pill while the slider shows.
                modes.forEachIndexed { i, m ->
                    val x = if (i == adaptiveIndex && selected == NoiseControlMode.ADAPTIVE && strengthEnabled) lerp(center(i), pillCenter, adaptiveLayout) else center(i)
                    val label = stringResource(modeLabel(m))
                    Box(
                        Modifier
                            .offset { androidx.compose.ui.unit.IntOffset((x - with(density) { 20.dp.toPx() }).roundToInt(), 0) }
                            .size(40.dp, height)
                            .semantics {
                                role = Role.RadioButton
                                this.selected = m == selected
                                contentDescription = label
                                onClick { pick(m); true }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            bitmap = ImageBitmap.imageResource(modeIcon(m)), contentDescription = null,
                            tint = if (i == adaptiveIndex && adaptivePillLight) Color.Black else iconTint,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }
            // Labels under the modes, or the wear guidance in their place.
            AnimatedContent(guidance, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(220)) },
                label = "labels", modifier = Modifier.padding(top = height + 8.dp)) { message ->
                if (message != null) {
                    Text(message, style = AppleText.segmentLabel, color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                } else {
                    Box(Modifier.fillMaxWidth()) {
                        modes.forEachIndexed { i, m ->
                            val labelWidth = with(density) { segWidth(i).toDp() }
                            Text(stringResource(modeLabel(m)), style = AppleText.segmentLabel,
                                color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, maxLines = 2,
                                modifier = Modifier
                                    .offset { androidx.compose.ui.unit.IntOffset(lefts[i].roundToInt(), 0) }
                                    .width(labelWidth))
                        }
                    }
                }
            }
        }
    }
}

/**
 * The firmware keeps only three adaptive strengths. Measured by writing a value and reading it back
 * after a reconnect: 0 -> 0, 25/60/70 -> 50, 75/80/90 -> 100 (nearest level, halves up).
 */
internal fun adaptiveLevel(fraction: Float): Int = (kotlin.math.floor(fraction.coerceIn(0f, 1f) * 2 + 0.5f) * 50).toInt()

private class Geometry(
    val lefts: FloatArray, val side: Float, val sub: Float, val subLeft: Float, val restPill: Float, val strength: Float,
) {
    fun width(index: Int, adaptiveIndex: Int) = if (index == adaptiveIndex) sub else side
}

@Composable
private fun Ring(x: Float, dotted: Boolean, color: Color, height: Dp) {
    val density = LocalDensity.current
    val size = 21.dp
    val half = with(density) { size.toPx() / 2 }
    Canvas(
        Modifier
            .offset { androidx.compose.ui.unit.IntOffset((x - half).roundToInt(), with(density) { ((height - size) / 2).toPx() }.roundToInt()) }
            .size(size)
    ) {
        val r = this.size.minDimension / 2 - 1.5.dp.toPx()
        if (dotted) {
            val dots = 20
            for (k in 0 until dots) {
                val a = 2 * PI * k / dots
                drawCircle(color, radius = 1.2.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat()))
            }
        } else drawCircle(color, radius = r, style = Stroke(width = 1.6.dp.toPx()))
    }
}

private fun modeLabel(mode: NoiseControlMode) = when (mode) {
    NoiseControlMode.OFF -> R.string.off
    NoiseControlMode.TRANSPARENCY -> R.string.transparency
    NoiseControlMode.ADAPTIVE -> R.string.adaptive
    NoiseControlMode.NOISE_CANCELLATION -> R.string.noise_cancellation
}

private fun modeIcon(mode: NoiseControlMode) = when (mode) {
    NoiseControlMode.OFF, NoiseControlMode.NOISE_CANCELLATION -> R.drawable.noise_cancellation
    NoiseControlMode.TRANSPARENCY -> R.drawable.transparency
    NoiseControlMode.ADAPTIVE -> R.drawable.adaptive
}
