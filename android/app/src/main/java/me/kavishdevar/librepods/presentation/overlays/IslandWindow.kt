/*
 * LibrePods - AirPods liberated from Apple's ecosystem
 * Copyright (C) 2025 LibrePods contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package me.kavishdevar.librepods.presentation.overlays

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.PixelFormat
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import me.kavishdevar.librepods.MainActivity
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.data.AirPodsNotifications
import me.kavishdevar.librepods.data.Battery
import me.kavishdevar.librepods.data.BatteryComponent
import me.kavishdevar.librepods.data.BatteryStatus
import me.kavishdevar.librepods.data.combinedBudsBattery
import me.kavishdevar.librepods.data.hasReportedLevel
import me.kavishdevar.librepods.presentation.theme.AppFonts
import me.kavishdevar.librepods.services.ServiceManager
import kotlin.math.abs

enum class IslandType { CONNECTED, TAKING_OVER, MOVED_TO_REMOTE, MOVED_TO_OTHER_DEVICE }

/** Compact overlay with expandable details. Its touch area always follows the visible card. */
/** The island's user-set size and position, kept in the app settings. */
data class IslandLayout(val widthDp: Int, val offsetDp: Int) {
    companion object {
        const val WIDTH_KEY = "island_width_dp"
        const val OFFSET_KEY = "island_offset_dp"
        const val DEFAULT_WIDTH = 420
        const val DEFAULT_OFFSET = 4
        val WIDTH_RANGE = 240..420
        val OFFSET_RANGE = 0..160

        fun read(context: Context): IslandLayout {
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            return IslandLayout(
                prefs.getInt(WIDTH_KEY, DEFAULT_WIDTH).coerceIn(WIDTH_RANGE),
                prefs.getInt(OFFSET_KEY, DEFAULT_OFFSET).coerceIn(OFFSET_RANGE)
            )
        }
    }
}

class IslandWindow(
    private val context: Context,
    private val observeDeviceEvents: Boolean = true,
    private val autoCloseMillis: Long = 4500L,
    private val onOpenApp: (() -> Unit)? = null
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val accessibilityManager = context.getSystemService(AccessibilityManager::class.java)
    @SuppressLint("InflateParams")
    private val islandView = LayoutInflater.from(context).inflate(R.layout.island_window, null) as LinearLayout
    private val containerView = FrameLayout(context).apply {
        addView(islandView, FrameLayout.LayoutParams(-1, -2))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        isClickable = true
        isLongClickable = true
        isFocusable = true
    }
    private val product = islandView.findViewById<ImageView>(R.id.island_product_image)
    private val nameView = islandView.findViewById<TextView>(R.id.island_device_name)
    private val statusView = islandView.findViewById<TextView>(R.id.island_connected_text)
    private val batteryText = islandView.findViewById<TextView>(R.id.island_battery_text)
    private val batteryProgress = islandView.findViewById<ProgressBar>(R.id.island_battery_progress)
    private val batteryContainer = islandView.findViewById<View>(R.id.island_battery_container)
    private val undoButton = islandView.findViewById<ImageButton>(R.id.island_action_button)
    private val details = islandView.findViewById<View>(R.id.island_details)
    private val detailsText = islandView.findViewById<TextView>(R.id.island_details_text)
    private val handler = Handler(Looper.getMainLooper())
    private var params: WindowManager.LayoutParams? = null
    private var transitionAnimator: Animator? = null
    private var heightAnimator: ValueAnimator? = null
    private var productAnimation: AnimatedImageDrawable? = null
    private var loadedAnimation = 0
    private var receiverRegistered = false
    private var configurationReceiverRegistered = false
    private var isClosing = false
    private var expanded = false
    private var autoCloseRunnable: Runnable? = null
    private var longPressRunnable: Runnable? = null
    private var tracker: VelocityTracker? = null
    private var downX = 0f
    private var downY = 0f
    private var dragged = false
    private var longPressed = false
    private var batteryLevel: Int? = null
    private var batteries: List<Battery>? = null
    private var previewLevel: Int? = null
    private var currentType = IslandType.CONNECTED
    private var reversed = false
    private var availableHeight = 0
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    val isVisible: Boolean get() = containerView.parent != null && containerView.visibility == View.VISIBLE

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AirPodsNotifications.BATTERY_DATA -> {
                    batteries = intent.getParcelableArrayListExtra("data", Battery::class.java) ?: return
                    renderBattery()
                }
                AirPodsNotifications.DISCONNECT_RECEIVERS -> forceClose()
            }
        }
    }
    private val configurationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (isVisible && !isClosing) {
                cancelHeightAnimation()
                updatePlacement()
                if (expanded) constrainDetails()
                updateWindow()
            }
        }
    }

    init {
        listOf(nameView, statusView, detailsText,
            islandView.findViewById<TextView>(R.id.island_open_button),
            islandView.findViewById<TextView>(R.id.island_close_button)
        ).forEach { it.typeface = AppFonts.typeface(context) }
        batteryText.typeface = AppFonts.typeface(context, 700)
        val badgeDp = (44 * context.resources.configuration.fontScale.coerceIn(1f, 1.4f)).toInt()
        batteryContainer.layoutParams = batteryContainer.layoutParams.apply {
            width = dp(badgeDp); height = dp(badgeDp)
        }
        val textInset = dp(badgeDp / 6 + 1)
        batteryText.setPadding(textInset, 0, textInset, 0)
        containerView.setOnClickListener { openApp() }
        containerView.setOnLongClickListener { setExpanded(!expanded); true }
        islandView.findViewById<View>(R.id.island_open_button).setOnClickListener { openApp() }
        islandView.findViewById<View>(R.id.island_close_button).setOnClickListener { close() }
        containerView.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = "android.widget.Button"
                info.addAction(AccessibilityNodeInfo.AccessibilityAction(
                    AccessibilityNodeInfo.ACTION_CLICK, context.getString(R.string.island_open_app)))
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS)
                info.addAction(if (expanded) AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE
                    else AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND)
            }
            override fun performAccessibilityAction(host: View, action: Int, arguments: android.os.Bundle?): Boolean =
                when (action) {
                    AccessibilityNodeInfo.ACTION_DISMISS -> { close(); true }
                    AccessibilityNodeInfo.ACTION_EXPAND -> { setExpanded(true); true }
                    AccessibilityNodeInfo.ACTION_COLLAPSE -> { setExpanded(false); true }
                    else -> super.performAccessibilityAction(host, action, arguments)
                }
        }
        installGestures()
    }

    /** A failed attachment never leaves a phantom open state in the host. */
    fun show(name: String, batteryPercentage: Int, context: Context, type: IslandType = IslandType.CONNECTED,
             reversed: Boolean = false, otherDeviceName: String? = null): Boolean {
        if (containerView.parent != null || isClosing) return false
        try {
            currentType = type
            this.reversed = reversed
            previewLevel = batteryPercentage.takeIf { it in 0..100 }
            batteries = if (observeDeviceEvents) ServiceManager.getService()?.getBattery() else null
            expanded = false
            details.visibility = View.GONE
            nameView.maxLines = 1
            nameView.text = name
            statusView.text = context.getString(when (type) {
                IslandType.CONNECTED -> R.string.island_connected_text
                IslandType.TAKING_OVER -> R.string.island_taking_over_text
                IslandType.MOVED_TO_REMOTE -> R.string.island_moved_to_remote_text
                IslandType.MOVED_TO_OTHER_DEVICE -> if (reversed) R.string.island_moved_to_other_device_reversed_text
                    else R.string.island_moved_to_other_device_text
            }, otherDeviceName?.takeIf { it.isNotBlank() } ?: context.getString(R.string.island_other_device))
            resetTransforms()
            applyShape()
            renderBattery()
            params = WindowManager.LayoutParams(-1, -2, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                title = "LibrePods Dynamic Island"
            }
            updatePlacement()
            windowManager.addView(containerView, params)
            this.context.applicationContext.registerReceiver(configurationReceiver,
                IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED), Context.RECEIVER_NOT_EXPORTED)
            configurationReceiverRegistered = true
            if (observeDeviceEvents) {
                this.context.registerReceiver(batteryReceiver, IntentFilter(AirPodsNotifications.BATTERY_DATA).apply {
                    addAction(AirPodsNotifications.DISCONNECT_RECEIVERS)
                }, Context.RECEIVER_NOT_EXPORTED)
                receiverRegistered = true
                ServiceManager.getService()?.sendBatteryBroadcast()
            }
            productAnimation?.start()
            animateEntry()
            resetAutoCloseTimer()
            return isVisible
        } catch (failure: Exception) {
            Log.e(TAG, "Unable to show island overlay", failure)
            cleanup()
            return false
        }
    }

    private fun updatePlacement() {
        val metrics = windowManager.maximumWindowMetrics
        val safe = metrics.windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        val usableWidth = (metrics.bounds.width() - safe.left - safe.right - dp(24)).coerceAtLeast(1)
        availableHeight = (metrics.bounds.height() - safe.top - safe.bottom - dp(8)).coerceAtLeast(1)
        val layout = IslandLayout.read(context)
        params?.apply {
            width = minOf(dp(layout.widthDp), usableWidth)
            height = WindowManager.LayoutParams.WRAP_CONTENT
            x = (safe.left - safe.right) / 2
            y = safe.top + dp(layout.offsetDp)
        }
    }

    /** Applies a changed [IslandLayout] to the island on screen, as the settings sliders move. */
    fun refreshPlacement() {
        if (params == null) return
        updatePlacement()
        updateWindow()
    }

    private fun renderBattery() {
        batteryLevel = if (observeDeviceEvents && batteries != null) batteries!!.combinedBudsBattery()?.level else previewLevel
        val showUndo = currentType == IslandType.MOVED_TO_OTHER_DEVICE && !reversed
        undoButton.visibility = if (showUndo) View.VISIBLE else View.GONE
        batteryContainer.visibility = if (showUndo) View.GONE else View.VISIBLE
        undoButton.setOnClickListener {
            if (observeDeviceEvents) ServiceManager.getService()?.takeOver("reverse")
            close()
        }
        val color = when {
            batteryLevel == null -> Color.rgb(152, 152, 157)
            batteryLevel!! <= 20 -> Color.rgb(255, 69, 58)
            else -> Color.rgb(48, 209, 88)
        }
        batteryText.text = batteryLevel?.let { context.getString(R.string.popup_battery_percent, it) } ?: "?"
        batteryText.setTextColor(color)
        batteryProgress.progressTintList = android.content.res.ColorStateList.valueOf(color)
        batteryProgress.progress = batteryLevel ?: 0
        val summary = if (!observeDeviceEvents) {
            batteryLevel?.let { context.getString(R.string.island_preview_battery, it) }
                ?: context.getString(R.string.battery_unavailable)
        } else {
            val known = batteries.orEmpty().filter { it.hasReportedLevel() }
            if (known.isEmpty()) context.getString(R.string.battery_unavailable) else known.joinToString("\n") { battery ->
                val label = context.getString(when (battery.component) {
                    BatteryComponent.LEFT -> R.string.left
                    BatteryComponent.RIGHT -> R.string.right
                    else -> R.string.case_alt
                })
                context.getString(if (battery.status == BatteryStatus.CHARGING) R.string.popup_battery_charging
                    else R.string.popup_battery_level, label, battery.level)
            }
        }
        // Expanded details preserve the full name when the compact title has been ellipsized.
        detailsText.text = nameView.text.toString() + "\n" + summary
        loadProductAnimation(if ((batteryLevel ?: 100) <= 20) R.raw.airpods_pro3_spin_amber else R.raw.airpods_pro3_spin_green)
    }

    private fun loadProductAnimation(resource: Int) {
        if (resource == loadedAnimation && productAnimation != null) return
        productAnimation?.stop()
        productAnimation = try {
            (ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.resources, resource)) { decoder, info, _ ->
                val width = minOf(info.size.width, dp(84))
                decoder.setTargetSize(width, (width * info.size.height / info.size.width).coerceAtLeast(1))
            } as? AnimatedImageDrawable)?.apply { repeatCount = AnimatedImageDrawable.REPEAT_INFINITE }
        } catch (failure: Exception) {
            Log.e(TAG, "Unable to decode Pro 3 animation", failure)
            null
        }
        loadedAnimation = resource
        if (productAnimation == null) product.setImageResource(R.drawable.airpods_pro3_apart_green)
        else product.setImageDrawable(productAnimation)
        if (isVisible) productAnimation?.start()
    }

    private fun animateEntry() {
        transitionAnimator = ObjectAnimator.ofPropertyValuesHolder(containerView,
            PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_X, .96f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, .96f, 1f),
            PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, -dp(8).toFloat(), 0f)).apply {
            duration = 280
            interpolator = DecelerateInterpolator(1.6f)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { refreshSummary() }
            })
            start()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun installGestures() {
        containerView.setOnTouchListener { _, event ->
            if (isClosing) return@setOnTouchListener true
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cancelAutoCloseTimer()
                    cancelTransition()
                    resetTransforms()
                    tracker?.recycle()
                    tracker = VelocityTracker.obtain().also { it.addMovement(event) }
                    downX = event.rawX; downY = event.rawY; dragged = false; longPressed = false
                    longPressRunnable = Runnable {
                        if (!dragged && !isClosing && isVisible) {
                            longPressed = true
                            containerView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            containerView.performLongClick()
                        }
                    }.also { handler.postDelayed(it, ViewConfiguration.getLongPressTimeout().toLong()) }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    tracker?.addMovement(event)
                    val dy = event.rawY - downY
                    if (abs(dy) > touchSlop || abs(event.rawX - downX) > touchSlop) {
                        dragged = true
                        cancelLongPress()
                        containerView.translationY = if (dy < 0) dy * .65f else dy.coerceAtMost(dp(60).toFloat()) * .3f
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    cancelLongPress()
                    recycleTracker()
                    restoreAfterDrag()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    cancelLongPress()
                    tracker?.addMovement(event)
                    tracker?.computeCurrentVelocity(1000)
                    val velocity = tracker?.yVelocity ?: 0f
                    val dy = event.rawY - downY
                    recycleTracker()
                    when {
                        dragged && (dy < -dp(44) || (dy < -dp(12) && velocity < -dp(900))) -> close()
                        dragged && dy > dp(44) -> { resetTransforms(); setExpanded(true) }
                        dragged || longPressed -> restoreAfterDrag()
                        else -> containerView.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun restoreAfterDrag() {
        transitionAnimator = ObjectAnimator.ofFloat(containerView, View.TRANSLATION_Y, containerView.translationY, 0f).apply {
            duration = 180
            interpolator = DecelerateInterpolator()
            start()
        }
        resetAutoCloseTimer()
    }

    private fun setExpanded(value: Boolean) {
        if (!isVisible || isClosing) return
        cancelHeightAnimation()
        val from = islandView.height
        expanded = value
        details.visibility = if (expanded) View.VISIBLE else View.GONE
        nameView.maxLines = 1
        if (expanded) constrainDetails()
        applyShape()
        islandView.measure(View.MeasureSpec.makeMeasureSpec(params!!.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val to = islandView.measuredHeight
        if (from > 0 && from != to) {
            heightAnimator = ValueAnimator.ofInt(from, to).apply {
                duration = 220
                interpolator = DecelerateInterpolator(1.4f)
                addUpdateListener {
                    islandView.layoutParams = islandView.layoutParams.apply { height = it.animatedValue as Int }
                    params?.height = it.animatedValue as Int
                    updateWindow()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        islandView.layoutParams = islandView.layoutParams.apply { height = -2 }
                        params?.height = -2
                        heightAnimator = null
                        updateWindow()
                        refreshSummary()
                    }
                })
                start()
            }
        }
        resetAutoCloseTimer()
    }

    /** Keep the actions visible; lengthy names and all three batteries scroll in the body. */
    private fun constrainDetails() {
        val width = (params!!.width - islandView.paddingLeft - islandView.paddingRight).coerceAtLeast(1)
        val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val summary = islandView.findViewById<View>(R.id.island_summary)
        val actions = islandView.findViewById<View>(R.id.island_detail_actions)
        val scroll = islandView.findViewById<View>(R.id.island_details_scroll)
        val summaryHeight = summary.height.takeIf { it > 0 } ?: summary.measuredHeight
        actions.measure(widthSpec, heightSpec)
        scroll.layoutParams = scroll.layoutParams.apply { height = -2 }
        scroll.measure(widthSpec, heightSpec)
        val bodyHeight = (availableHeight - islandView.paddingTop - islandView.paddingBottom -
            summaryHeight - details.paddingTop - actions.measuredHeight).coerceAtLeast(1)
        scroll.layoutParams = scroll.layoutParams.apply { height = minOf(scroll.measuredHeight, bodyHeight) }
    }

    private fun applyShape() {
        islandView.background = GradientDrawable().apply {
            setColor(Color.BLACK)
            cornerRadius = dp(if (expanded) 28 else 42).toFloat()
        }
        islandView.clipToOutline = true
    }
    /** Refresh static render nodes after resizing/fading an overlay with an independently animated image. */
    private fun refreshSummary() {
        if (!isVisible) return
        val summary = islandView.findViewById<View>(R.id.island_summary)
        listOf(summary, nameView, statusView, batteryContainer, batteryText, batteryProgress).forEach {
            it.requestLayout()
            it.invalidate()
        }
        islandView.invalidate()
        containerView.invalidate()
    }
    private fun updateWindow() {
        if (containerView.parent != null) runCatching { windowManager.updateViewLayout(containerView, params) }
            .onFailure { Log.e(TAG, "Unable to resize island", it) }
    }
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    private fun openApp() {
        if (!isVisible || isClosing) return
        try {
            if (onOpenApp != null) onOpenApp.invoke()
            else if (observeDeviceEvents) context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (failure: Exception) { Log.e(TAG, "Unable to open AirPods settings", failure) }
        finally { close() }
    }
    private fun resetAutoCloseTimer() {
        cancelAutoCloseTimer()
        val flags = AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_ICONS or
            AccessibilityManager.FLAG_CONTENT_CONTROLS
        val timeout = accessibilityManager?.getRecommendedTimeoutMillis(autoCloseMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), flags)
            ?.toLong() ?: autoCloseMillis
        autoCloseRunnable = Runnable { close() }.also { handler.postDelayed(it, timeout) }
    }
    private fun cancelAutoCloseTimer() { autoCloseRunnable?.let(handler::removeCallbacks); autoCloseRunnable = null }
    private fun cancelLongPress() { longPressRunnable?.let(handler::removeCallbacks); longPressRunnable = null }
    private fun recycleTracker() { tracker?.recycle(); tracker = null }
    private fun cancelTransition() {
        transitionAnimator?.removeAllListeners(); transitionAnimator?.cancel(); transitionAnimator = null
    }
    private fun cancelHeightAnimation() {
        heightAnimator?.removeAllListeners(); heightAnimator?.removeAllUpdateListeners(); heightAnimator?.cancel(); heightAnimator = null
        islandView.layoutParams = islandView.layoutParams.apply { height = -2 }
        params?.height = -2
    }
    private fun resetTransforms() {
        containerView.apply { alpha = 1f; scaleX = 1f; scaleY = 1f; translationY = 0f; visibility = View.VISIBLE }
        product.visibility = View.VISIBLE
    }
    fun close() {
        if (Looper.myLooper() != Looper.getMainLooper()) { handler.post { close() }; return }
        if (isClosing) return
        if (!isVisible) { cleanup(); return }
        isClosing = true
        cancelAutoCloseTimer(); cancelLongPress(); cancelHeightAnimation(); cancelTransition()
        transitionAnimator = ObjectAnimator.ofPropertyValuesHolder(containerView,
            PropertyValuesHolder.ofFloat(View.ALPHA, containerView.alpha, 0f),
            PropertyValuesHolder.ofFloat(View.SCALE_X, containerView.scaleX, .96f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, containerView.scaleY, .96f),
            PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, containerView.translationY, -dp(12).toFloat())).apply {
            duration = 200
            interpolator = AccelerateInterpolator(1.1f)
            addListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(animation: Animator) { cleanup() } })
            start()
        }
    }
    fun forceClose() {
        if (Looper.myLooper() != Looper.getMainLooper()) { handler.post { forceClose() }; return }
        cleanup()
    }
    private fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        autoCloseRunnable = null; longPressRunnable = null
        cancelTransition(); cancelHeightAnimation(); recycleTracker()
        productAnimation?.stop(); productAnimation = null; loadedAnimation = 0
        product.setImageDrawable(null)
        if (receiverRegistered) { receiverRegistered = false; runCatching { context.unregisterReceiver(batteryReceiver) } }
        if (configurationReceiverRegistered) {
            configurationReceiverRegistered = false
            runCatching { context.applicationContext.unregisterReceiver(configurationReceiver) }
        }
        if (containerView.parent != null) runCatching { windowManager.removeViewImmediate(containerView) }
            .onFailure { Log.e(TAG, "Unable to remove island", it) }
        containerView.visibility = View.GONE
        isClosing = false
    }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    private companion object { const val TAG = "IslandWindow" }
}
