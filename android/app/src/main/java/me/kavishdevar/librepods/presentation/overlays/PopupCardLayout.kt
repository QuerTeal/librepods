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

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Card container for the connection popup that turns vertical drags into callbacks, including
 * drags that start on its close button. Positions are tracked in raw screen coordinates since
 * the card itself moves while being dragged.
 */
class PopupCardLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    interface Listener {
        fun onTouchStarted()
        fun onDragStarted()
        fun onDragged(dy: Float)
        fun onDragEnded(velocityY: Float)
        fun onTouchEnded()
    }

    var listener: Listener? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val maxFlingVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity.toFloat()
    private var downX = 0f
    private var downY = 0f
    private var dragOriginY = 0f
    private var dragging = false
    private var velocityTracker: VelocityTracker? = null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> startGesture(ev)
            MotionEvent.ACTION_MOVE -> {
                track(ev)
                if (!dragging && isVerticalDrag(ev)) beginDrag(ev)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (!dragging) endGesture()
        }
        return dragging
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        // ACTION_DOWN always passes through onInterceptTouchEvent first, which starts the gesture.
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                track(ev)
                if (!dragging && isVerticalDrag(ev)) beginDrag(ev)
                if (dragging) listener?.onDragged(ev.rawY - dragOriginY)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                track(ev)
                if (dragging) {
                    val tracker = velocityTracker
                    tracker?.computeCurrentVelocity(1000, maxFlingVelocity)
                    listener?.onDragEnded(if (ev.actionMasked == MotionEvent.ACTION_UP) tracker?.yVelocity ?: 0f else 0f)
                }
                endGesture()
            }
        }
        return true
    }

    private fun startGesture(ev: MotionEvent) {
        downX = ev.rawX
        downY = ev.rawY
        dragging = false
        velocityTracker?.recycle()
        velocityTracker = VelocityTracker.obtain()
        track(ev)
        listener?.onTouchStarted()
    }

    private fun beginDrag(ev: MotionEvent) {
        dragging = true
        dragOriginY = ev.rawY
        parent?.requestDisallowInterceptTouchEvent(true)
        listener?.onDragStarted()
    }

    private fun endGesture() {
        dragging = false
        velocityTracker?.recycle()
        velocityTracker = null
        listener?.onTouchEnded()
    }

    private fun isVerticalDrag(ev: MotionEvent): Boolean {
        val dy = abs(ev.rawY - downY)
        return dy > touchSlop && dy > abs(ev.rawX - downX)
    }

    private fun track(ev: MotionEvent) {
        val tracker = velocityTracker ?: return
        val screenEvent = MotionEvent.obtain(ev).apply { setLocation(ev.rawX, ev.rawY) }
        tracker.addMovement(screenEvent)
        screenEvent.recycle()
    }
}
