// SPDX-License-Identifier: GPL-3.0-or-later
package me.kavishdevar.librepods.presentation.components

import kotlin.math.abs

/** Holds a value near a reference point; a wider release band prevents repeated ticks. */
internal class SliderDetent(points: List<Float>, private val threshold: Float) {
    private val points = points.filter { it.isFinite() }.distinct().sorted()
    private var active: Float? = null
    private var previousRaw: Float? = null
    private var lastOutput: Float? = null

    data class Result(val value: Float, val entered: Boolean)

    /** Restoring/applying a value never requests feedback. Keep raw drag history for our own output. */
    fun synchronize(value: Float) {
        if (lastOutput == value) return
        lastOutput = value
        previousRaw = value
        active = points.firstOrNull { it == value }
    }

    fun resolve(raw: Float): Result {
        val previous = previousRaw
        previousRaw = raw
        val held = active
        if (held != null && abs(raw - held) <= threshold * 1.5f) {
            lastOutput = held
            return Result(held, false)
        }
        active = null
        val nearby = points.minByOrNull { abs(raw - it) }?.takeIf { abs(raw - it) <= threshold }
        // Catch a reference even if a fast drag skips its narrow capture band.
        // Leaving a held point must not immediately catch that same point again.
        val crossed = if (previous == null) null else points.filter {
            it != held && ((previous < it && raw >= it) || (previous > it && raw <= it))
        }.minByOrNull { abs(previous - it) }
        val point = nearby ?: crossed
        active = point
        val result = point ?: raw
        lastOutput = result
        return Result(result, point != null)
    }
}
