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
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withScale
import me.kavishdevar.librepods.presentation.theme.AppFonts
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Circular battery gauge used by the connection popup, drawn like iOS 26's: a thin ring over a
 * barely visible track, empty in the middle except for a lightning bolt while charging.
 *
 * While the buds are held at the Optimized Charge Limit it draws iOS's limit ring instead: the
 * track stops at a mark short of the top, where the ring turns inward into a short tick. Below the
 * limit the level fills as usual and the rest up to the mark stays track grey; once the limit is
 * reached the fill runs right up to the mark and down the tick.
 */
class BatteryRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val strokeWidth = STROKE_DP * resources.displayMetrics.density
    private val bounds = RectF()
    private val path = Path()
    private val tickPath = Path()

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = this@BatteryRingView.strokeWidth
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
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

    /** What the ring draws for the level, eased between states together with it. */
    private var shown = Shape(0f, 360f, 0f, 0f)

    private var charging: Boolean? = null
    /** How much of the bolt shows, 0–1; it pops in with an overshoot and shrinks away. */
    private var boltProgress = 0f
    private var boltAnimator: ValueAnimator? = null

    /**
     * Sets the level (0–100) and colors, filling from the previously shown level after [delayMs].
     * With [limit] (a level, 0–100) it draws the Optimized Charge Limit ring for that limit.
     */
    fun setLevel(
        level: Int,
        color: Int,
        trackColor: Int,
        animate: Boolean = true,
        delayMs: Long = 0,
        limit: Int? = null
    ) {
        progressPaint.color = color
        boltPaint.color = color
        trackPaint.color = ColorUtils.setAlphaComponent(trackColor, TRACK_ALPHA)
        val target = level.coerceIn(0, 100) / 100f
        val targetShape = shapeFor(target, limit)
        levelAnimator?.cancel()
        if (!animate || (target == shownFraction && targetShape == shown)) {
            shown = targetShape
            showFraction(target)
            return
        }
        val fromFraction = shownFraction
        val fromShape = shown
        val change = maxOf(abs(target - fromFraction), abs(targetShape.sweep - fromShape.sweep) / 360f)
        levelAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            // Longer for bigger changes, so filling a whole ring doesn't rush and a 1% step doesn't drag.
            duration = (420 + 480 * change).toLong()
            startDelay = delayMs
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener {
                val t = it.animatedValue as Float
                shown = fromShape.lerp(targetShape, t)
                showFraction(fromFraction + (target - fromFraction) * t)
            }
            start()
        }
    }

    /**
     * Below the limit the fill follows the level (79% ends at 79% of the turn); once the limit is
     * reached it runs to the mark, as iOS draws it.
     */
    private fun shapeFor(fraction: Float, limit: Int?): Shape {
        if (limit == null) return Shape(360f * fraction, 360f, 0f, 0f)
        val reached = fraction * 100 >= limit
        return Shape(
            sweep = if (reached) LIMIT_MARK_DEG else minOf(360f * fraction, LIMIT_MARK_DEG),
            trackEnd = LIMIT_MARK_DEG,
            tick = 1f,
            tickFilled = if (reached) 1f else 0f
        )
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
        shown = shown.copy(sweep = 0f, tickFilled = 0f)
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
        val shape = shown
        // The track: the whole ring, or from the fill up to the limit mark and down the tick.
        if (shape.tick <= 0f) {
            canvas.drawOval(bounds, trackPaint)
        } else {
            val trackAlpha = trackPaint.alpha
            val trackSweep = shape.trackEnd - shape.sweep
            if (trackSweep > 0f) canvas.drawArc(bounds, -90f + shape.sweep, trackSweep, false, trackPaint)
            trackPaint.alpha = (trackAlpha * shape.tick * (1f - shape.tickFilled)).toInt()
            tickPath.rewind()
            pointAt(shape.trackEnd, 1f) { x, y -> tickPath.moveTo(x, y) }
            appendTick(tickPath, shape.trackEnd, shape.tick)
            canvas.drawPath(tickPath, trackPaint)
            trackPaint.alpha = trackAlpha
        }
        if (shape.sweep > 0f && shape.tickFilled > 0f) {
            // Turns inward at the mark in the same stroke, so the corner is rounded.
            path.rewind()
            path.arcTo(bounds, -90f, shape.sweep, true)
            appendTick(path, shape.sweep, shape.tick * shape.tickFilled)
            canvas.drawPath(path, progressPaint)
        } else if (shape.sweep > 0f) {
            canvas.drawArc(bounds, -90f, shape.sweep, false, progressPaint)
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

    /** Continues [into] from the ring at [angle] (degrees from the top) toward the centre. */
    private fun appendTick(into: Path, angle: Float, length: Float) {
        pointAt(angle, 1f - (1f - TICK_INNER_RADIUS) * length) { x, y -> into.lineTo(x, y) }
    }

    /** The point at [angle] degrees clockwise from the top, [radius] of the way out to the ring's middle. */
    private inline fun pointAt(angle: Float, radius: Float, use: (Float, Float) -> Unit) {
        val r = bounds.width() / 2 * radius
        val radians = Math.toRadians(angle.toDouble())
        use(bounds.centerX() + r * sin(radians).toFloat(), bounds.centerY() - r * cos(radians).toFloat())
    }

    /**
     * [sweep] is the fill in degrees from the top and [trackEnd] where the track stops. [tick] is
     * how much of the limit tick shows (0–1); [tickFilled] turns it from track grey to the fill.
     */
    private data class Shape(val sweep: Float, val trackEnd: Float, val tick: Float, val tickFilled: Float) {
        fun lerp(to: Shape, t: Float) = Shape(
            sweep + (to.sweep - sweep) * t,
            trackEnd + (to.trackEnd - trackEnd) * t,
            tick + (to.tick - tick) * t,
            tickFilled + (to.tickFilled - tickFilled) * t
        )
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
        /** Measured from iPadOS 27: a 2.6 pt stroke on a 35.3 pt ring. */
        const val STROKE_DP = 2.6f
        /**
         * Where the Optimized Charge Limit mark sits, in degrees clockwise from the top. iOS puts it
         * here for the 80% limit rather than at 80% of the turn (measured from iPadOS 27).
         */
        const val LIMIT_MARK_DEG = 313f
        /** The tick runs from the ring's middle in to this fraction of its radius. */
        const val TICK_INNER_RADIUS = 0.57f
        /** The track is nearly invisible, as on iOS 26. */
        const val TRACK_ALPHA = 0x0F
        val popInterpolator = OvershootInterpolator(2.2f)
    }
}
