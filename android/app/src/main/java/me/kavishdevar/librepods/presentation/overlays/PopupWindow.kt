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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.ImageDecoder
import android.graphics.PixelFormat
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.text.style.ScaleXSpan
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.data.AirPodsBase
import me.kavishdevar.librepods.data.AirPodsNotifications
import me.kavishdevar.librepods.data.AirPodsPro2Lightning
import me.kavishdevar.librepods.data.AirPodsPro2USBC
import me.kavishdevar.librepods.data.Battery
import me.kavishdevar.librepods.data.BatteryComponent
import me.kavishdevar.librepods.data.BatteryStatus
import me.kavishdevar.librepods.data.hasReportedLevel
import me.kavishdevar.librepods.data.combinedBudsBattery
import me.kavishdevar.librepods.presentation.theme.AppFonts
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Bottom connection popup, laid out like the iOS AirPods card (measured from iOS 26 and an iPadOS 27
 * recording): the name with a close button, the AirPods beside their case, and a battery ring with
 * a percentage under each. It springs up from the bottom edge over a dimmed screen and can be
 * closed, tapped away, or flung down.
 *
 * Like iOS, it has two states. While both buds sit in the case it plays the spinning buds-and-case
 * render with one combined ring for the buds; once a bud is taken out it shows a still of the buds
 * apart and splits the ring into one per bud, their percentages marked L and R and the case's with
 * its symbol. Switching fades the whole content out and, after a beat, slowly back in. The renders
 * are [model]'s, and their case light glows green or amber as the real one would.
 */
@SuppressLint("InflateParams")
class PopupWindow(
    context: Context,
    private val onCloseCallback: () -> Unit = {},
    model: AirPodsBase? = null,
    private val observeDeviceEvents: Boolean = true,
    /** Whether the buds are held at the Optimized Charge Limit right now. */
    private val optimizedChargeHeld: () -> Boolean = { false },
    private val onChargeFully: () -> Unit = {}
) {
    private enum class Stage { InCase, TakenOut }
    private enum class CaseLight { Green, Amber }

    private val renders = RenderSet.forModel(model)

    private val context = context.applicationContext
    private val windowManager = this.context.getSystemService(WindowManager::class.java)
    private val density = this.context.resources.displayMetrics.density

    private val root: View = LayoutInflater.from(this.context).inflate(R.layout.popup_window, null)
    private val scrim: View = root.findViewById(R.id.scrim)
    private val card: PopupCardLayout = root.findViewById(R.id.card)
    private val nameView: TextView = root.findViewById(R.id.name)
    private val content: View = root.findViewById(R.id.content)
    private val spinCase: ImageView = root.findViewById(R.id.spin_case)
    private val takenOut: ImageView = root.findViewById(R.id.taken_out)
    private var caseAnimation: AnimatedImageDrawable? = null
    @RawRes private var loadedSpin = 0

    private val budsColumn = BatteryColumn(root.findViewById(R.id.buds_column), "AirPods", renders.budsX)
    private val leftColumn = BatteryColumn(root.findViewById(R.id.left_column), this.context.getString(R.string.left), renders.leftX, symbol = LEFT_SYMBOL)
    private val rightColumn = BatteryColumn(root.findViewById(R.id.right_column), this.context.getString(R.string.right), renders.rightX, symbol = RIGHT_SYMBOL)
    private val caseColumn = BatteryColumn(root.findViewById(R.id.case_column), this.context.getString(R.string.case_alt), renders.caseX, renders.caseApartX)
    private val columns = listOf(budsColumn, leftColumn, rightColumn, caseColumn)
    private val optimizedTitle: TextView = root.findViewById(R.id.optimized_title)
    private val optimizedSubtitle: TextView = root.findViewById(R.id.optimized_subtitle)
    private val chargeFullyButton: TextView = root.findViewById(R.id.charge_fully)
    private val optimizedViews = listOf(optimizedTitle, optimizedSubtitle, chargeFullyButton)
    private var optimizedShown = false
    /** The limit the buds' rings currently mark, or null for plain rings. */
    private var ringLimit: Int? = null
    private var openedAtMillis = 0L
    private var heightAnimator: android.animation.ValueAnimator? = null
    private val optimizedRunnable = Runnable { refreshOptimized() }

    private val textColor = ContextCompat.getColor(this.context, R.color.popup_text)
    /** Every ring and bolt uses this one color, whatever the level or charging state. */
    private val batteryColor = ContextCompat.getColor(this.context, R.color.battery_level)

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT
    ).apply {
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        title = "LibrePods connection popup"
    }

    private val translationSpring = SpringAnimation(card, DynamicAnimation.TRANSLATION_Y).apply {
        spring = SpringForce(0f)
        addUpdateListener { _, _, _ -> syncScrim() }
    }

    private val autoCloseHandler = Handler(Looper.getMainLooper())
    private val autoCloseRunnable = Runnable { close() }
    private var receiver: BroadcastReceiver? = null

    /** Distance the card travels between fully shown (0) and fully hidden. */
    private var hiddenOffset = 0f
    private var dragStartTranslation = 0f
    /** Whether releasing the current drag would dismiss the card; crossing it ticks the haptics. */
    private var pastDismissPoint = false
    private var isShowing = false
    private var isClosing = false
    val isOpen: Boolean get() = isShowing && !isClosing

    private var batteries = emptyList<Battery>()
    /** From the AirPods' ear detection (both reporting "in case"); null until they report. */
    private var budsInCaseReported: Boolean? = null
    /** The state the AirPods are in; the content may still be fading over to it. */
    private var stage: Stage? = null
    private var caseLight: CaseLight? = null

    init {
        applyFonts()
        scrim.setOnClickListener { close() }
        root.findViewById<View>(R.id.close).setOnClickListener { close() }
        chargeFullyButton.setOnClickListener {
            onChargeFully()
            showOptimized(false)
            bindBatteries(animate = true)
            scheduleAutoClose(RESUME_DELAY_MS)
        }

        card.listener = object : PopupCardLayout.Listener {
            override fun onTouchStarted() = pauseAutoClose()
            override fun onTouchEnded() = scheduleAutoClose(RESUME_DELAY_MS)

            override fun onDragStarted() {
                translationSpring.cancel()
                dragStartTranslation = card.translationY
                pastDismissPoint = isPastDismissPoint()
            }

            override fun onDragged(dy: Float) {
                val target = dragStartTranslation + dy
                // Pulling up past the resting position meets increasing resistance.
                card.translationY = if (target >= 0f) target else -rubberBand(-target)
                syncScrim()
                val past = isPastDismissPoint()
                if (past != pastDismissPoint) {
                    pastDismissPoint = past
                    card.performHapticFeedback(if (past) THRESHOLD_ACTIVATE else THRESHOLD_DEACTIVATE)
                }
            }

            override fun onDragEnded(velocityY: Float) {
                val flungDown = velocityY > FLING_VELOCITY_DP * density
                val flungUp = velocityY < -FLING_VELOCITY_DP * density
                if ((flungDown || isPastDismissPoint()) && !flungUp) {
                    close(velocityY)
                } else {
                    animateTo(0f, velocityY, SETTLE_DAMPING, SETTLE_STIFFNESS)
                }
            }
        }
    }

    /**
     * [earStatus] is the AirPods' latest ear detection report (primary, secondary), when they are
     * connected; without it the popup infers "in the case" from both buds charging.
     */
    fun open(
        name: String = "AirPods Pro",
        batteryNotification: AirPodsNotifications.BatteryNotification,
        earStatus: ByteArray? = null
    ) = open(name, batteryNotification.getBattery(), earStatus)

    fun open(name: String, batteries: List<Battery>, earStatus: ByteArray? = null) {
        if (isShowing || isClosing) return
        try {
            nameView.text = name
            card.accessibilityPaneTitle = name
            budsInCaseReported = earStatus?.let(::bothInCase)
            positionCard()
            // The card arrives already showing its levels, as on iOS.
            update(batteries, animate = false)

            // Keep the card off-screen until its height is known.
            card.translationY = context.resources.displayMetrics.heightPixels.toFloat()
            windowManager.addView(root, params)
            isShowing = true

            card.doOnLayout {
                if (!isShowing || isClosing) return@doOnLayout
                hiddenOffset = card.height + (card.layoutParams as FrameLayout.LayoutParams).bottomMargin + 24 * density
                card.translationY = hiddenOffset
                syncScrim()
                animateTo(0f, 0f, OPEN_DAMPING, OPEN_STIFFNESS)
            }

            if (observeDeviceEvents) registerReceiver()
            scheduleAutoClose(AUTO_CLOSE_DELAY_MS)
            openedAtMillis = android.os.SystemClock.elapsedRealtime()
        } catch (e: Exception) {
            Log.e(TAG, "Error opening popup: ${e.message}")
            pauseAutoClose()
            unregisterReceiver()
            translationSpring.cancel()
            removeFromWindow()
        }
    }

    /** Applies new battery levels and, if known, ear detection; switches stage when the buds leave or return to the case. */
    fun update(batteries: List<Battery>, earStatus: ByteArray? = null, animate: Boolean = true) {
        this.batteries = batteries
        earStatus?.let { budsInCaseReported = bothInCase(it) }
        val newStage = resolveStage()
        if (newStage != stage) {
            val fade = animate && stage != null && isShowing
            stage = newStage
            if (fade) fadeToStage(newStage) else applyStage(newStage)
        }
        showCaseLight(resolveCaseLight())
        bindBatteries(animate)
        refreshOptimized()
    }

    /**
     * Shows or hides the Optimized Charge Limit message. Like iOS, the card first arrives with
     * just the levels and grows to add the message a moment later.
     */
    fun refreshOptimized() {
        autoCloseHandler.removeCallbacks(optimizedRunnable)
        if (!isShowing || isClosing) return
        val want = optimizedChargeHeld()
        if ((OPTIMIZED_LIMIT.takeIf { want }) != ringLimit) bindBatteries(animate = true)
        if (want == optimizedShown) return
        val wait = OPTIMIZED_REVEAL_DELAY_MS - (android.os.SystemClock.elapsedRealtime() - openedAtMillis)
        if (want && wait > 0) {
            autoCloseHandler.postDelayed(optimizedRunnable, wait)
            return
        }
        showOptimized(want)
    }

    private fun showOptimized(show: Boolean) {
        if (show == optimizedShown) return
        optimizedShown = show
        Log.i(TAG, "Optimized Charge Limit message ${if (show) "shown" else "hidden"}")
        val compact = context.resources.getDimensionPixelSize(R.dimen.popup_card_height)
        val expanded = ((RING_CENTER_Y_IN_CASE_DP + OPTIMIZED_CARD_BOTTOM_DP) * density).roundToInt()
        heightAnimator?.cancel()
        heightAnimator = android.animation.ValueAnimator.ofInt(card.height.takeIf { it > 0 } ?: compact,
            if (show) expanded else compact).apply {
            duration = OPTIMIZED_RESIZE_MS
            interpolator = android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f)
            addUpdateListener { card.updateLayoutParams<FrameLayout.LayoutParams> { height = it.animatedValue as Int } }
            start()
        }
        optimizedViews.forEach { view ->
            view.animate().cancel()
            if (show) {
                view.visibility = View.VISIBLE
                view.animate().alpha(1f).setStartDelay(OPTIMIZED_RESIZE_MS / 2).setDuration(OPTIMIZED_RESIZE_MS).start()
            } else {
                view.animate().alpha(0f).setStartDelay(0).setDuration(OPTIMIZED_RESIZE_MS / 2)
                    .withEndAction { if (!optimizedShown) view.visibility = View.GONE }.start()
            }
        }
    }

    fun updateBatteryStatus(batteryNotification: AirPodsNotifications.BatteryNotification) {
        update(batteryNotification.getBattery())
    }

    fun close(velocityY: Float = 0f) {
        if (!isShowing || isClosing) return
        isClosing = true
        pauseAutoClose()
        unregisterReceiver()

        if (hiddenOffset == 0f) {
            removeFromWindow()
            return
        }
        animateTo(hiddenOffset, velocityY.coerceAtLeast(0f), SpringForce.DAMPING_RATIO_NO_BOUNCY, CLOSE_STIFFNESS) {
            removeFromWindow()
        }
    }

    /** Physical lid close and service shutdown must remove the overlay without animation delay. */
    fun dismissImmediately() {
        pauseAutoClose()
        unregisterReceiver()
        translationSpring.cancel()
        if (isShowing || isClosing || root.parent != null) removeFromWindow()
    }

    private fun bothInCase(earStatus: ByteArray) = earStatus.size >= 2 && earStatus.all { it == EAR_IN_CASE }

    private fun resolveStage(): Stage {
        budsInCaseReported?.let { return if (it) Stage.InCase else Stage.TakenOut }
        val left = batteries.find { it.component == BatteryComponent.LEFT }
        val right = batteries.find { it.component == BatteryComponent.RIGHT }
        return if (left?.status == BatteryStatus.CHARGING && right?.status == BatteryStatus.CHARGING) {
            Stage.InCase
        } else {
            Stage.TakenOut
        }
    }

    /**
     * As on iOS, nothing slides: the content fades out, the layout changes while it is blank, and
     * after a beat it fades slowly back in. A change during the fade picks up from where it is.
     */
    private fun fadeToStage(stage: Stage) {
        content.animate().cancel()
        content.animate().alpha(0f)
            .setStartDelay(0).setDuration(FADE_OUT_MS).setInterpolator(LinearInterpolator())
            .withEndAction {
                applyStage(stage)
                content.animate().alpha(1f)
                    .setStartDelay(BLANK_MS).setDuration(FADE_IN_MS).setInterpolator(EASE_IN_OUT)
                    .start()
            }
            .start()
    }

    private fun applyStage(stage: Stage) {
        val inCase = stage == Stage.InCase
        // Only the in-case render moves; it plays while it is on screen and holds still otherwise.
        if (inCase) caseAnimation?.start() else caseAnimation?.stop()
        spinCase.isVisible = inCase
        takenOut.isVisible = !inCase
        budsColumn.view.isVisible = inCase
        leftColumn.view.isVisible = !inCase
        rightColumn.view.isVisible = !inCase
        // With the buds apart, the case's percentage gets its symbol too.
        caseColumn.symbol = if (inCase) null else CASE_SYMBOL
        columns.forEach { it.place(inCase) }
        bindBatteries(animate = false)
    }

    /**
     * The case's status light, as the renders show it, following Apple's rule for the real one on
     * current firmware: with buds inside it shows theirs, and with none inside the case's own; amber
     * while that is under 95%, green once it is (nearly) full.
     */
    private fun resolveCaseLight(): CaseLight {
        val case = batteries.find { it.component == BatteryComponent.CASE }
        val buds = batteries.filter { it.component == BatteryComponent.LEFT || it.component == BatteryComponent.RIGHT }
        // Both are inside while the popup shows them in the case; otherwise only a bud in the case charges.
        val inside = if (stage == Stage.InCase) buds else buds.filter { it.status == BatteryStatus.CHARGING }
        val shown = inside.ifEmpty { listOfNotNull(case) }
        val amber = shown.any { !it.isAvailable() || it.level < FULL_CHARGE_PERCENT }
        return if (amber) CaseLight.Amber else CaseLight.Green
    }

    private fun showCaseLight(light: CaseLight) {
        if (light == caseLight) return
        caseLight = light
        val green = light == CaseLight.Green
        takenOut.setImageResource(if (green) renders.apartGreen else renders.apartAmber)
        loadSpin(if (green) renders.spinGreen else renders.spinAmber)
    }

    private fun loadSpin(@RawRes res: Int) {
        if (res == loadedSpin) return
        loadedSpin = res
        caseAnimation?.stop()
        caseAnimation = try {
            (ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.resources, res)) as? AnimatedImageDrawable)
                ?.apply { repeatCount = AnimatedImageDrawable.REPEAT_INFINITE }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading animation: ${e.message}")
            null
        }
        spinCase.setImageDrawable(caseAnimation)
        if (stage == Stage.InCase) caseAnimation?.start()
    }

    private fun removeFromWindow() {
        autoCloseHandler.removeCallbacks(optimizedRunnable)
        heightAnimator?.cancel()
        content.animate().cancel()
        caseAnimation?.stop()
        caseAnimation = null
        loadedSpin = 0
        spinCase.setImageDrawable(null)
        try {
            if (root.parent != null) windowManager.removeView(root)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing popup: ${e.message}")
        } finally {
            isShowing = false
            isClosing = false
            stage = null
            onCloseCallback()
        }
    }

    private fun animateTo(
        position: Float,
        velocity: Float,
        damping: Float,
        stiffness: Float,
        onEnd: (() -> Unit)? = null
    ) {
        translationSpring.cancel()
        translationSpring.spring.apply {
            finalPosition = position
            dampingRatio = damping
            this.stiffness = stiffness
        }
        translationSpring.setStartVelocity(velocity)
        if (onEnd != null) {
            translationSpring.addEndListener(object : DynamicAnimation.OnAnimationEndListener {
                override fun onAnimationEnd(animation: DynamicAnimation<*>?, canceled: Boolean, value: Float, velocity: Float) {
                    animation?.removeEndListener(this)
                    if (!canceled) onEnd()
                }
            })
        }
        translationSpring.start()
    }

    /** The screen behind dims as far as the card has come up. */
    private fun syncScrim() {
        if (hiddenOffset <= 0f) return
        scrim.alpha = (1f - card.translationY / hiddenOffset).coerceIn(0f, 1f)
    }

    private fun isPastDismissPoint() = card.translationY > card.height * DISMISS_FRACTION

    private fun rubberBand(distance: Float): Float {
        val limit = MAX_OVERSCROLL_DP * density
        return limit * (1f - exp(-distance / limit))
    }

    /**
     * Sizes the card like iOS, a few points in from the screen's sides and bottom, and lays out the
     * render and the rings, whose places scale with the card's width.
     */
    private fun positionCard() {
        val metrics = windowManager.currentWindowMetrics
        val insets = metrics.windowInsets
            .getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout())
        val margin = (CARD_INSET_DP * density).roundToInt()
        // As on iOS the card sits over the gesture handle; a button navigation bar still pushes it up.
        val bottomInset = if (insets.bottom > BUTTON_NAV_MIN_DP * density) insets.bottom else 0
        val available = metrics.bounds.width() - 2 * margin - insets.left - insets.right
        val cardWidth = min(available, (MAX_CARD_WIDTH_DP * density).roundToInt())
        card.updateLayoutParams<FrameLayout.LayoutParams> {
            width = cardWidth
            bottomMargin = margin + bottomInset
        }

        // Both pictures share one horizontal scale; each is centered on its own line.
        val renderWidth = cardWidth * renders.width
        val renderStart = cardWidth * renders.start
        fun ImageView.placeRender(aspect: Float, centerYDp: Float) = updateLayoutParams<FrameLayout.LayoutParams> {
            val renderHeight = renderWidth * aspect
            width = renderWidth.roundToInt()
            height = renderHeight.roundToInt()
            leftMargin = renderStart.roundToInt()
            topMargin = (centerYDp * density - renderHeight / 2).roundToInt()
        }
        spinCase.placeRender(renders.spinAspect, renders.spinCenterY)
        takenOut.placeRender(renders.apartAspect, renders.apartCenterY)

        // With the buds apart, iOS sets the rings a little lower.
        val columnWidth = context.resources.getDimension(R.dimen.popup_column_width)
        val ringSize = context.resources.getDimension(R.dimen.popup_ring_size)
        columns.forEach { column ->
            column.startInCase = (cardWidth * column.x - columnWidth / 2).roundToInt()
            column.startTakenOut = (cardWidth * column.xApart - columnWidth / 2).roundToInt()
            column.topInCase = (RING_CENTER_Y_IN_CASE_DP * density - ringSize / 2).roundToInt()
            column.topTakenOut = (RING_CENTER_Y_APART_DP * density - ringSize / 2).roundToInt()
        }

        // The Optimized Charge Limit lines and button, measured from the ring centre (iPadOS 27).
        optimizedTitle.firstBaselineToTopHeight = ((RING_CENTER_Y_IN_CASE_DP + OPTIMIZED_TITLE_BASELINE_DP) * density).roundToInt()
        optimizedSubtitle.firstBaselineToTopHeight = ((RING_CENTER_Y_IN_CASE_DP + OPTIMIZED_SUBTITLE_BASELINE_DP) * density).roundToInt()
        chargeFullyButton.updateLayoutParams<FrameLayout.LayoutParams> {
            topMargin = ((RING_CENTER_Y_IN_CASE_DP + OPTIMIZED_BUTTON_TOP_DP) * density).roundToInt()
            val side = (cardWidth * OPTIMIZED_BUTTON_SIDE_FRACTION).roundToInt()
            leftMargin = side
            rightMargin = side
        }
    }

    private fun bindBatteries(animate: Boolean) {
        val left = batteries.find { it.component == BatteryComponent.LEFT }
        val right = batteries.find { it.component == BatteryComponent.RIGHT }
        val case = batteries.find { it.component == BatteryComponent.CASE }
        // The buds' rings mark the Optimized Charge Limit while they're held at it.
        val limit = OPTIMIZED_LIMIT.takeIf { optimizedChargeHeld() }
        ringLimit = limit
        leftColumn.bind(left, animate, limit)
        rightColumn.bind(right, animate, limit)
        caseColumn.bind(case, animate)
        // The combined reading shows the lower bud, like iOS, and only counts as charging if both are.
        // A bud held at the limit is still on charge, so the bolt stays (as on iOS).
        val onCharge = setOf(BatteryStatus.CHARGING, BatteryStatus.OPTIMIZED_CHARGING)
        val combined = batteries.combinedBudsBattery()?.let {
            if (left?.status in onCharge && right?.status in onCharge) it.copy(status = BatteryStatus.CHARGING) else it
        }
        budsColumn.bind(combined, animate, limit)
    }

    private fun Battery.isAvailable() = hasReportedLevel()

    private fun applyFonts() {
        nameView.typeface = AppFonts.pretendard(context, 700)
        columns.forEach { it.level.typeface = AppFonts.typeface(context, 400) }
        optimizedTitle.typeface = AppFonts.typeface(context, 600)
        optimizedSubtitle.typeface = AppFonts.typeface(context, 400)
        chargeFullyButton.typeface = AppFonts.typeface(context, 600)
        // The baseline offsets were worked out for the inflated font; redo them for these.
        nameView.firstBaselineToTopHeight = (TITLE_BASELINE_DP * density).roundToInt()
        columns.forEach { it.level.firstBaselineToTopHeight = (LEVEL_BASELINE_DP * density).roundToInt() }
    }

    private fun scheduleAutoClose(delayMs: Long) {
        autoCloseHandler.removeCallbacks(autoCloseRunnable)
        if (isShowing && !isClosing) autoCloseHandler.postDelayed(autoCloseRunnable, delayMs)
    }

    private fun pauseAutoClose() = autoCloseHandler.removeCallbacks(autoCloseRunnable)

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerReceiver() {
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    AirPodsNotifications.BATTERY_DATA -> {
                        val batteryList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableArrayListExtra("data", Battery::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableArrayListExtra("data")
                        }
                        if (batteryList != null) update(batteryList)
                    }
                    AirPodsNotifications.EAR_DETECTION_DATA ->
                        intent.getByteArrayExtra("data")?.let { update(batteries, earStatus = it) }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(AirPodsNotifications.BATTERY_DATA)
            addAction(AirPodsNotifications.EAR_DETECTION_DATA)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    private fun unregisterReceiver() {
        receiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver: ${e.message}")
            }
            receiver = null
        }
    }

    /**
     * One ring and percentage, centered at [x] across the card (a fraction of its width), or at
     * [xApart] while the buds are apart, with [symbol] (an SF Symbol) before the percentage.
     * [labelText] only names it for accessibility.
     */
    private inner class BatteryColumn(
        val view: View,
        val labelText: String,
        val x: Float,
        val xApart: Float = x,
        var symbol: String? = null
    ) {
        val ring: BatteryRingView = view.findViewById(R.id.ring)
        val level: TextView = view.findViewById(R.id.level)

        /** Top left on the card in each stage, in pixels; set by [positionCard]. */
        var startInCase = 0
        var startTakenOut = 0
        var topInCase = 0
        var topTakenOut = 0

        fun place(inCase: Boolean) = view.updateLayoutParams<FrameLayout.LayoutParams> {
            leftMargin = if (inCase) startInCase else startTakenOut
            topMargin = if (inCase) topInCase else topTakenOut
        }

        /** Whether the component reports a level; otherwise the ring empties and the text shows a dash. */
        private var available = false

        /** Final opacity once shown; dimmed when the component isn't reporting a level. */
        var availability = 1f
            set(value) {
                field = value
                if (view.isVisible) {
                    view.animate().alpha(value).setStartDelay(0).setDuration(250).setInterpolator(EASE_IN_OUT).start()
                }
            }

        init {
            // The percentage counts along with the ring when a level changes on screen.
            ring.onLevelShown = { shown ->
                if (available) level.text = withSymbol(context.getString(R.string.popup_battery_percent, shown))
            }
        }

        fun bind(battery: Battery?, animate: Boolean, limit: Int? = null) {
            val reporting = battery != null && battery.isAvailable()
            val charging = reporting &&
                (battery.status == BatteryStatus.CHARGING || battery.status == BatteryStatus.OPTIMIZED_CHARGING)
            val percent = if (reporting) battery.level else 0
            available = reporting
            if (!reporting) level.text = withSymbol("–")
            ring.setCharging(charging, animate)
            ring.setLevel(percent, batteryColor, textColor, animate, limit = limit.takeIf { reporting })
            view.contentDescription = when {
                !reporting -> context.getString(R.string.popup_battery_unavailable, labelText)
                charging -> context.getString(R.string.popup_battery_charging, labelText, percent)
                else -> context.getString(R.string.popup_battery_level, labelText, percent)
            }
            availability = if (reporting) 1f else UNAVAILABLE_ALPHA
            if (!animate) view.alpha = availability
        }

        private fun withSymbol(text: String): CharSequence {
            val symbol = symbol ?: return text
            return SpannableStringBuilder()
                .append(symbol, SymbolSpan(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                .append(" ", ScaleXSpan(SYMBOL_GAP_SCALE), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                .append(text)
        }
    }

    /**
     * Draws an SF Symbol at the smaller size iOS uses beside text, raised to stay centered on the
     * digits' cap height (the symbols here are centered on it at full size).
     */
    private class SymbolSpan : MetricAffectingSpan() {
        override fun updateDrawState(tp: TextPaint) = apply(tp)
        override fun updateMeasureState(tp: TextPaint) = apply(tp)

        private fun apply(tp: TextPaint) {
            val size = tp.textSize
            tp.textSize = size * SYMBOL_SCALE
            tp.baselineShift -= (size * CAP_CENTER * (1f - SYMBOL_SCALE)).roundToInt()
        }
    }

    /**
     * One AirPods model's renders (spinning while the buds are in the case, and still with them
     * apart, each with the case light green or amber) and where they and the rings sit on the card,
     * as fractions of its width; [spinCenterY] and [apartCenterY] are in dp.
     */
    private class RenderSet(
        @RawRes val spinGreen: Int,
        @RawRes val spinAmber: Int,
        @DrawableRes val apartGreen: Int,
        @DrawableRes val apartAmber: Int,
        val start: Float,
        val width: Float,
        val spinAspect: Float,
        val apartAspect: Float,
        val spinCenterY: Float,
        val apartCenterY: Float,
        val budsX: Float,
        val caseX: Float,
        val leftX: Float,
        val rightX: Float,
        val caseApartX: Float
    ) {
        companion object {
            /** Cut from iPadOS 27 recordings of the popup, card pixels over its 800 px width. */
            val PRO_3 = RenderSet(
                spinGreen = R.raw.airpods_pro3_spin_green,
                spinAmber = R.raw.airpods_pro3_spin_amber,
                apartGreen = R.drawable.airpods_pro3_apart_green,
                apartAmber = R.drawable.airpods_pro3_apart_amber,
                start = 0.1275f,
                width = 0.7362f,
                spinAspect = 0.4244f,
                apartAspect = 0.4244f,
                spinCenterY = 191.00f,
                apartCenterY = 191.00f,
                budsX = 0.2969f,
                caseX = 0.695f,
                leftX = 0.2094f,
                rightX = 0.3794f,
                caseApartX = 0.6744f
            )

            /** The earlier renders, of AirPods Pro 2 (the case light is fixed in them). */
            val PRO_2 = RenderSet(
                spinGreen = R.raw.airpods_spin,
                spinAmber = R.raw.airpods_spin,
                apartGreen = R.drawable.airpods_taken_out,
                apartAmber = R.drawable.airpods_taken_out,
                start = 0.124f,
                width = 0.794f,
                spinAspect = 354f / 1050f,
                apartAspect = 450f / 1050f,
                spinCenterY = 198f,
                apartCenterY = 198f,
                budsX = 0.2919f,
                caseX = 0.6927f,
                leftX = 0.2125f,
                rightX = 0.3822f,
                caseApartX = 0.679f
            )

            fun forModel(model: AirPodsBase?) =
                if (model is AirPodsPro2Lightning || model is AirPodsPro2USBC) PRO_2 else PRO_3
        }
    }

    private companion object {
        const val TAG = "PopupWindow"
        const val EAR_IN_CASE: Byte = 0x02
        const val AUTO_CLOSE_DELAY_MS = 10_000L
        const val RESUME_DELAY_MS = 6_000L
        const val UNAVAILABLE_ALPHA = 0.35f
        /** Below this the real case light pulses amber (Apple, current AirPods firmware). */
        const val FULL_CHARGE_PERCENT = 95

        // Card geometry, measured from iPadOS 27 (points taken as dp).
        const val CARD_INSET_DP = 6f
        const val MAX_CARD_WIDTH_DP = 440f
        const val BUTTON_NAV_MIN_DP = 32f
        const val TITLE_BASELINE_DP = 61f
        const val RING_CENTER_Y_IN_CASE_DP = 277.5f
        const val RING_CENTER_Y_APART_DP = 280f
        // Optimized Charge Limit, below the ring centre (measured on iPadOS 27).
        const val OPTIMIZED_TITLE_BASELINE_DP = 79f
        const val OPTIMIZED_SUBTITLE_BASELINE_DP = 100.5f
        const val OPTIMIZED_BUTTON_TOP_DP = 121f
        const val OPTIMIZED_CARD_BOTTOM_DP = 203.5f
        const val OPTIMIZED_BUTTON_SIDE_FRACTION = 0.125f
        const val OPTIMIZED_REVEAL_DELAY_MS = 700L
        /** The level AirPods stop at under Optimized Charge Limit. */
        const val OPTIMIZED_LIMIT = 80
        const val OPTIMIZED_RESIZE_MS = 400L
        const val LEVEL_BASELINE_DP = 21.5f


        // SF Symbols (l.circle.fill, r.circle.fill, the AirPods Pro case) and how iOS sets them in text.
        const val LEFT_SYMBOL = "􀀛"
        const val RIGHT_SYMBOL = "􀀧"
        const val CASE_SYMBOL = "􀹬"
        const val SYMBOL_SCALE = 0.72f
        /** Half of SF Pro's cap height, as a fraction of the font size. */
        const val CAP_CENTER = 0.352f
        /** The gap after a symbol is a little wider than a space (widened only, so the line keeps its height). */
        const val SYMBOL_GAP_SCALE = 1.37f

        // From an iPadOS 27 recording: a quick fade out, a beat of nothing, then a slow fade in.
        const val FADE_OUT_MS = 250L
        const val BLANK_MS = 250L
        const val FADE_IN_MS = 750L

        const val MAX_OVERSCROLL_DP = 28f
        const val FLING_VELOCITY_DP = 900f
        const val DISMISS_FRACTION = 0.3f
        const val OPEN_DAMPING = 0.86f
        const val OPEN_STIFFNESS = 400f
        const val SETTLE_DAMPING = 0.75f
        const val SETTLE_STIFFNESS = 500f
        const val CLOSE_STIFFNESS = 800f
        val EASE_IN_OUT = PathInterpolator(0.42f, 0f, 0.58f, 1f)
        val THRESHOLD_ACTIVATE = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
        val THRESHOLD_DEACTIVATE = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
    }
}
