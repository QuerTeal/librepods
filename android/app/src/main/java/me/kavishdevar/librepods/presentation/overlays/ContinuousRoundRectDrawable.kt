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

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt

/**
 * A filled rounded rectangle with iOS's continuous corners, which ease into the straight sides
 * instead of meeting them at a circle's tangent. The curve is the one UIKit draws (as reverse
 * engineered by PaintCode); a corner of [radius] starts 1.53 × [radius] from the corner.
 */
class ContinuousRoundRectDrawable(@ColorInt color: Int, private val radius: Float) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private val path = Path()

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        buildPath(bounds)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(path, paint)
    }

    override fun getOutline(outline: Outline) {
        outline.setPath(path)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    private fun buildPath(b: Rect) {
        path.rewind()
        val l = b.left.toFloat()
        val t = b.top.toFloat()
        val r = b.right.toFloat()
        val bo = b.bottom.toFloat()
        val rad = minOf(radius, minOf(b.width(), b.height()) / 2f / EXTENT)
        // Corner-local points: x and y measured inward from that corner, in units of the radius.
        fun tl(x: Float, y: Float) = floatArrayOf(l + x * rad, t + y * rad)
        fun tr(x: Float, y: Float) = floatArrayOf(r - x * rad, t + y * rad)
        fun br(x: Float, y: Float) = floatArrayOf(r - x * rad, bo - y * rad)
        fun bl(x: Float, y: Float) = floatArrayOf(l + x * rad, bo - y * rad)
        fun move(p: FloatArray) = path.moveTo(p[0], p[1])
        fun line(p: FloatArray) = path.lineTo(p[0], p[1])
        fun curve(c1: FloatArray, c2: FloatArray, p: FloatArray) = path.cubicTo(c1[0], c1[1], c2[0], c2[1], p[0], p[1])

        // Along an edge into the corner, round it, and out along the next edge. [corner] maps the
        // shared shape, with x along the edge the path arrives on; [swap] flips it for corners the
        // path enters along a vertical side.
        fun corner(corner: (Float, Float) -> FloatArray, swap: Boolean) {
            fun p(a: Float, c: Float) = if (swap) corner(c, a) else corner(a, c)
            line(p(EXTENT, 0f))
            curve(p(1.08849323f, 0f), p(0.86840689f, 0.02194160f), p(0.66993427f, 0.06549600f))
            line(p(0.63149399f, 0.07491100f))
            curve(p(0.37282392f, 0.16905899f), p(0.16906013f, 0.37282401f), p(0.07491176f, 0.63149399f))
            curve(p(0.02194169f, 0.86840701f), p(0f, 1.08849299f), p(0f, EXTENT))
        }

        move(tl(EXTENT, 0f))
        corner(::tr, swap = false)
        corner(::br, swap = true)
        corner(::bl, swap = false)
        corner(::tl, swap = true)
        path.close()
    }

    private companion object {
        /** How far along each side a corner reaches, in units of its radius. */
        const val EXTENT = 1.52866483f
    }
}
