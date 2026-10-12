// SPDX-License-Identifier: GPL-3.0-or-later
package me.kavishdevar.librepods.presentation.components

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.ContextThemeWrapper
import android.view.View
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.widget.SeslSeekBar
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import me.kavishdevar.librepods.presentation.theme.LocalOneUIColors
import kotlin.math.roundToInt
import me.kavishdevar.librepods.R

/** Isolate the SESL theme from Apple's Compose theme while respecting night mode and font size. */
@Composable
internal fun rememberSeslContext(theme: Int = R.style.Theme_LibrePods_OneUI9): Context {
    val base = LocalContext.current
    val density = LocalDensity.current
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return remember(base, density, dark, theme) {
        ContextThemeWrapper(base, theme).apply {
            applyOverrideConfiguration(Configuration().apply {
                fontScale = density.fontScale
                densityDpi = (density.density * 160).roundToInt()
                uiMode = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            })
        }
    }
}

/** The same themed drawable and fixed elevation used by SESL's floating toolbar projection. */
// These projection resources are internal to the pinned SESL9 version; keep access in this adapter.
@SuppressLint("PrivateResource")
@Composable
internal fun NativeOneUIToolbarSurface(progress: Float, modifier: Modifier) {
    val context = rememberSeslContext()
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    key(context) {
        AndroidView(modifier = modifier, factory = {
            View(context).apply {
                tag = "oneui-toolbar-surface"
                background = AppCompatResources.getDrawable(context, if (dark)
                    com.google.android.material.R.drawable.sesl_floating_appbar_round_background_dark
                else com.google.android.material.R.drawable.sesl_floating_appbar_round_background_light)
                elevation = resources.getDimension(com.google.android.material.R.dimen.sesl_floating_toolbar_projection_background_elevation)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                isClickable = false
                isFocusable = false
            }
        }, update = { it.alpha = progress.coerceIn(0f, 1f) })
    }
}

/** SESL9 widgets supply the One UI implementation on Samsung and other Android devices. */
@Composable
internal fun NativeOneUISwitch(checked: Boolean, enabled: Boolean, modifier: Modifier, onChange: ((Boolean) -> Unit)?) {
    val callback by rememberUpdatedState(onChange)
    val active = MaterialTheme.colorScheme.primary.toArgb()
    val inactive = LocalOneUIColors.current.switchOffTrack.toArgb()
    val density = LocalDensity.current.density
    val context = rememberSeslContext()
    key(context) {
    AndroidView(modifier = modifier.size(52.dp, 48.dp), factory = {
        SwitchCompat(context).apply {
            text = ""; showText = false; switchMinWidth = (48 * density).roundToInt()
            // The setting row emits user haptics once; SESL's setChecked also vibrates on updates.
            isHapticFeedbackEnabled = false
            setPadding(0, 0, 0, 0)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
    }, update = { view ->
        view.setOnCheckedChangeListener(null)
        view.isEnabled = enabled; view.isChecked = checked
        // Keep the library's animated thumb drawable; only tint the native track.
        view.trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(active, inactive))
        view.setOnCheckedChangeListener { _, next -> callback?.invoke(next) }
    })
    }
}

@Composable
internal fun NativeOneUISeekBar(value: Float, range: ClosedFloatingPointRange<Float>, enabled: Boolean, label: String?, modifier: Modifier, onChange: (Float) -> Unit, snapPoints: List<Float> = emptyList(), snapThreshold: Float = 0.05f) {
    val callback by rememberUpdatedState(onChange)
    val latestValue by rememberUpdatedState(value)
    val haptics = LocalHapticFeedback.current
    val detent = remember(range, snapPoints, snapThreshold) { SliderDetent(snapPoints, snapThreshold) }
    val span = range.endInclusive - range.start
    // Preserve fractional hearing/tone settings such as 0..1 and -1..1.
    val steps = span.roundToInt().coerceAtLeast(100)
    val progress = if (span <= 0f) 0 else (((value - range.start) / span).coerceIn(0f, 1f) * steps).roundToInt()
    val context = rememberSeslContext()
    key(context) {
    AndroidView(modifier = modifier.fillMaxWidth().height(48.dp), factory = {
        SeslSeekBar(context).apply {
            splitTrack = false; setPadding(0, 0, 0, 0)
        }
    }, update = { view ->
        view.setOnSeekBarChangeListener(null)
        detent.synchronize(value)
        view.max = steps; view.progress = progress
        view.keyProgressIncrement = (steps / 20).coerceAtLeast(1)
        view.isEnabled = enabled && span > 0f; view.contentDescription = label
        // Use the SESL theme's native drawable/tints rather than restricted library setters.
        view.setOnSeekBarChangeListener(object : SeslSeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeslSeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val result = detent.resolve(range.start + span * progress / steps)
                val snappedProgress = (((result.value - range.start) / span) * steps).roundToInt().coerceIn(0, steps)
                // Correct the native thumb immediately, even if the held value causes no recomposition.
                if (bar.progress != snappedProgress) bar.progress = snappedProgress
                if (result.entered) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                if (result.value != latestValue) callback(result.value)
            }
            override fun onStartTrackingTouch(bar: SeslSeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeslSeekBar) = Unit
        })
    })
    }
}
