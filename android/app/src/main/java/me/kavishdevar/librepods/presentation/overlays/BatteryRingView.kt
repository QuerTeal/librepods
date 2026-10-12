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

package me.kavishdevar.librepods.presentation.overlays

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withScale
import me.kavishdevar.librepods.presentation.theme.AppFonts
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Circular battery gauge used by the connection popup, drawn like iOS 26's: a thin ring over a
 * barely visible track, empty in the middle except for a lightning bolt while charging.
 */
class BatteryRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val strokeWidth = 3f * resources.displayMetrics.density
    private val bounds = RectF()

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@BatteryRingView.strokeWidth
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = this@BatteryRingView.strokeWidth
    }
    private val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = AppFonts.typeface(context)
    }

    /** Called with the level (0–100) the ring currently shows, on every frame while it fills. */
    var onLevelShown: ((Int) -> Unit)? = null

    private var shownFraction = 0f
    private var levelAnimator: ValueAnimator? = null

    private var charging: Boolean? = null
    /** How much of the bolt shows, 0–1; it pops in with an overshoot and shrinks away. */
    private var boltProgress = 0f
    private var boltAnimator: ValueAnimator? = null

    /** Sets the level (0–100) and colors, filling from the previously shown level after [delayMs]. */
    fun setLevel(level: Int, color: Int, trackColor: Int, animate: Boolean = true, delayMs: Long = 0) {
        progressPaint.color = color
        boltPaint.color = color
        trackPaint.color = ColorUtils.setAlphaComponent(trackColor, TRACK_ALPHA)
        val target = level.coerceIn(0, 100) / 100f
        levelAnimator?.cancel()
        if (!animate || target == shownFraction) {
            showFraction(target)
            return
        }
        levelAnimator = ValueAnimator.ofFloat(shownFraction, target).apply {
            // Longer for bigger changes, so filling a whole ring doesn't rush and a 1% step doesn't drag.
            duration = (420 + 480 * abs(target - shownFraction)).toLong()
            startDelay = delayMs
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { showFraction(it.animatedValue as Float) }
            start()
        }
    }

    /** Shows the bolt while [charging]; with [animate], it pops in or shrinks away. */
    fun setCharging(charging: Boolean, animate: Boolean) {
        if (charging == this.charging) return
        val first = this.charging == null
        this.charging = charging
        boltAnimator?.cancel()
        val target = if (charging) 1f else 0f
        if (!animate || first) {
            boltProgress = target
            invalidate()
            return
        }
        boltAnimator = ValueAnimator.ofFloat(boltProgress, target).apply {
            duration = if (charging) BOLT_IN_MS else BOLT_OUT_MS
            addUpdateListener {
                boltProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /** Empties the ring without animating, so the next [setLevel] fills it from zero. */
    fun reset() {
        levelAnimator?.cancel()
        showFraction(0f)
    }

    private fun showFraction(fraction: Float) {
        shownFraction = fraction
        onLevelShown?.invoke((fraction * 100).roundToInt())
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val inset = strokeWidth / 2
        bounds.set(inset, inset, width - inset, height - inset)
        canvas.drawOval(bounds, trackPaint)
        if (shownFraction > 0f) {
            canvas.drawArc(bounds, -90f, 360f * shownFraction, false, progressPaint)
        }
        if (boltProgress > 0f) {
            val scale = if (charging == true) popInterpolator.getInterpolation(boltProgress) else boltProgress
            val color = boltPaint.color
            boltPaint.alpha = (255 * boltProgress.coerceAtMost(1f)).toInt()
            boltPaint.textSize = height * BOLT_SCALE
            val metrics = boltPaint.fontMetrics
            val baseline = height / 2f - (metrics.ascent + metrics.descent) / 2f
            canvas.withScale(scale, scale, width / 2f, height / 2f) {
                drawText(BOLT, width / 2f, baseline, boltPaint)
            }
            boltPaint.color = color
        }
    }

    override fun onDetachedFromWindow() {
        levelAnimator?.cancel()
        boltAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        /** SF Symbol bolt.fill, drawn with SF Pro. */
        const val BOLT = "􀋦"
        const val BOLT_SCALE = 0.34f
        const val BOLT_IN_MS = 380L
        const val BOLT_OUT_MS = 200L
        /** The track is nearly invisible, as on iOS 26. */
        const val TRACK_ALPHA = 0x0F
        val popInterpolator = OvershootInterpolator(2.2f)
    }
}
