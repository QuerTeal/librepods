package me.kavishdevar.librepods

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import android.widget.TextView
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import kotlin.math.abs
import me.kavishdevar.librepods.presentation.overlays.IslandType
import me.kavishdevar.librepods.presentation.overlays.IslandWindow

/** Real overlay UI checks with sample values and injected app actions; no Bluetooth or preference writes. */
class IslandUiInstrumentation : Instrumentation() {
    private var capture = false
    private var captureType = IslandType.CONNECTED
    private var captureFontScale = 1f
    private var captureLongName = false
    private var captureExpanded = false
    private var captureLandscape = false
    private var capturePreview: Activity? = null
    private val checks = mutableListOf<String>()
    private val windows = mutableListOf<IslandWindow>()

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        capture = arguments?.getString("capture") == "true"
        captureType = IslandType.valueOf(arguments?.getString("type") ?: "CONNECTED")
        captureFontScale = arguments?.getString("font_scale")?.toFloatOrNull()?.coerceIn(1f, 2f) ?: 1f
        captureLongName = arguments?.getString("long_name") == "true"
        captureExpanded = arguments?.getString("expanded") == "true"
        captureLandscape = arguments?.getString("landscape") == "true"
        start()
    }

    override fun onStart() {
        var result = Activity.RESULT_OK
        try {
            uiAutomation.serviceInfo = uiAutomation.serviceInfo.apply {
                flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
            check(Settings.canDrawOverlays(targetContext)) { "User overlay permission is required" }
            if (capture) captureOverlay() else {
                checkExistingLifecycle()
                checkGesturesAndActions()
                checkLargeTextLayout()
            }
        } catch (failure: Throwable) {
            result = Activity.RESULT_CANCELED
            checks += "FAILED: $failure\n${failure.stackTraceToString()}"
        } finally {
            runOnMainSync { windows.forEach { it.forceClose() }; capturePreview?.finish() }
        }
        finish(result, Bundle().apply {
            putString("stream", "\n${checks.joinToString("\n")}\n${checks.count { it.startsWith("PASS") }} Island UI checks passed\n")
        })
    }

    private fun captureOverlay() {
        capturePreview = startActivitySync(Intent(targetContext, NativeUiPreviewActivity::class.java)
            .putExtra("scenario", "navigation").putExtra("design", "apple")
            .putExtra("landscape", captureLandscape)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        waitForIdleSync()
        settle()
        val context = CapturingContext(fontContext(captureFontScale))
        val island = createIsland(context, timeout = 60_000L)
        val name = if (captureLongName) LONG_NAME else "AirPods Pro 3"
        onMain { check(island.show(name, 85, context, captureType, otherDeviceName = "예시 기기")) }
        settle()
        val root = context.root()
        if (captureExpanded) {
            onMain { check(root.performAccessibilityAction(AccessibilityNodeInfo.ACTION_EXPAND, null)) }
            settle()
        }
        val suffix = "${captureType.name}-font$captureFontScale-long$captureLongName-expanded$captureExpanded-landscape$captureLandscape"
        val file = File(targetContext.getExternalFilesDir("review"), "island-$suffix.png")
        file.parentFile!!.mkdirs()
        val screenshot = uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
        file.outputStream().use { check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        screenshot.recycle()
        checks += "CAPTURE: ${file.absolutePath}"
        checks += "OVERLAY: ${onMain { root.width }}x${onMain { root.height }}"
        onMain {
            for (id in listOf(R.id.island_summary, R.id.island_product_image, R.id.island_connected_text,
                R.id.island_device_name, R.id.island_battery_container, R.id.island_details, R.id.island_open_button)) {
                val view = root.findViewById<View>(id)
                val rect = Rect(0, 0, view.width, view.height)
                (root as ViewGroup).offsetDescendantRectToMyCoords(view, rect)
                checks += "LAYOUT: ${root.resources.getResourceEntryName(id)} $rect shown=${view.isShown} alpha=${view.alpha}"
            }
        }
    }

    /** Preserve the original fourteen regression checks, including an intentional addView failure. */
    private fun checkExistingLifecycle() {
        val context = CapturingContext(targetContext)
        val island = createIsland(context)
        onMain {
            verify("overlay attaches without a connection", island.show("Island UI QA", 85, context))
            verify("duplicate show does not replace an attached view", !island.show("Duplicate QA", 85, context))
        }
        settle()
        verify("name and battery are visible", findText("Island UI QA") && findText("85%"))
        onMain { island.forceClose(); island.forceClose() }
        verify("immediate close is idempotent", !onMain { island.isVisible })
        onMain { verify("same instance reopens", island.show("Island UI QA", 0, context)) }
        settle()
        verify("zero battery remains visible as zero", findText("0%"))
        onMain { island.close(); island.forceClose() }
        verify("force close removes an animating dismissal", !onMain { island.isVisible })
        onMain { verify("unknown battery does not prevent opening", island.show("Island UI QA", -1, context)) }
        settle()
        verify("unknown battery is explicit", findText("?"))
        onMain { island.close() }
        await("animated close removes the window") { !onMain { island.isVisible } }
        verify("animated close removes the window", true)
        val timed = createIsland(context, timeout = 100L)
        onMain { verify("timed overlay attaches", timed.show("Island UI QA", 85, context)) }
        await("timeout removes the window even during entry animation") { !onMain { timed.isVisible } }
        verify("timeout removes the window even during entry animation", true)
        val failingContext = CapturingContext(targetContext, failFirstAttachment = true)
        val retry = createIsland(failingContext)
        onMain {
            verify("failed window attachment leaves no visible state", !retry.show("Island UI QA", 85, failingContext) && !retry.isVisible)
            verify("failed attachment can retry immediately", retry.show("Island UI QA", 85, failingContext))
            retry.forceClose()
        }
    }

    private fun checkGesturesAndActions() {
        val context = CapturingContext(targetContext)
        var opened = 0
        val island = createIsland(context, timeout = 60_000L, onOpen = { opened++ })
        onMain { check(island.show("Island gesture QA", 85, context)) }
        settle()
        val root = context.root()
        val image = onMain { root.findViewById<ImageView>(R.id.island_product_image) }
        val animation = onMain { image.drawable as? AnimatedImageDrawable }
        verify("Pro 3 animation is a drawable rather than a video surface", animation != null)
        verify("product animation runs while visible", onMain { animation?.isRunning == true })
        onMain {
            val actions = root.createAccessibilityNodeInfo().actionList
            verify("popup has a named accessible open action", actions.any { it.id == AccessibilityNodeInfo.ACTION_CLICK && !it.label.isNullOrBlank() })
            verify("popup has accessible dismiss and expand actions", actions.any { it.id == AccessibilityNodeInfo.ACTION_DISMISS } && actions.any { it.id == AccessibilityNodeInfo.ACTION_EXPAND })
        }
        checkRenderedHeader(root, "compact")
        gesture(root, distanceDp = 90f, ending = MotionEvent.ACTION_CANCEL)
        settle()
        onMain {
            verify("cancelled downward drag restores the compact position", abs(root.translationY) <= 1f && root.findViewById<View>(R.id.island_details).visibility == View.GONE)
            verify("cancelled drag does not open the app", opened == 0)
        }
        gesture(root, distanceDp = 90f)
        settle()
        checkRenderedHeader(root, "expanded")
        onMain {
            verify("downward swipe expands details without opening the app", root.findViewById<View>(R.id.island_details).visibility == View.VISIBLE && opened == 0)
            verify("expanded details expose open and close controls", root.findViewById<View>(R.id.island_open_button).isShown && root.findViewById<View>(R.id.island_close_button).isShown)
            verify("expanded popup exposes accessible collapse", root.createAccessibilityNodeInfo().actionList.any { it.id == AccessibilityNodeInfo.ACTION_COLLAPSE })
            check(root.performAccessibilityAction(AccessibilityNodeInfo.ACTION_COLLAPSE, null))
        }
        settle()
        verify("accessible collapse restores the compact layout", onMain { root.findViewById<View>(R.id.island_details).visibility == View.GONE })
        val downTime = SystemClock.uptimeMillis()
        touch(root, MotionEvent.ACTION_DOWN, downTime, downTime, 0f)
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout().toLong() + 150L)
        touch(root, MotionEvent.ACTION_UP, downTime, SystemClock.uptimeMillis(), 0f)
        settle()
        verify("long press expands details without also clicking open", onMain { root.findViewById<View>(R.id.island_details).visibility == View.VISIBLE && opened == 0 })
        onMain { island.forceClose() }
        settle()
        verify("forced close stops the product animation", onMain { animation?.isRunning == false })
        verify("forced close during expanded state cannot run an app action later", opened == 0 && !onMain { island.isVisible })
        onMain { check(island.show("Island gesture QA", 85, context)) }
        settle()
        verify("reopening clears previous expanded state and transforms", onMain {
            root.alpha == 1f && abs(root.translationY) <= 1f && root.scaleX == 1f && root.scaleY == 1f &&
                image.isShown && root.findViewById<View>(R.id.island_details).visibility == View.GONE
        })
        val swipeAnimation = onMain { image.drawable as AnimatedImageDrawable }
        gesture(root, distanceDp = -90f)
        await("upward swipe removes the overlay") { !onMain { island.isVisible } }
        verify("upward swipe dismisses without opening the app", opened == 0)
        verify("swipe dismissal stops the product animation", onMain { !swipeAnimation.isRunning })
        onMain { check(island.show("Island gesture QA", 85, context)) }
        settle()
        verify("reopening after swipe is fully visible", onMain { root.isShown && root.alpha == 1f && image.isShown && (image.drawable as AnimatedImageDrawable).isRunning })
        onMain { check(root.performAccessibilityAction(AccessibilityNodeInfo.ACTION_DISMISS, null)) }
        await("accessible dismiss removes the overlay") { !onMain { island.isVisible } }
        verify("accessible dismiss removes the overlay", true)
        onMain { check(island.show("Island gesture QA", 85, context)) }
        settle()
        onMain { check(root.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null)) }
        await("accessible open removes the overlay") { !onMain { island.isVisible } }
        verify("accessible open invokes the injected app action once", opened == 1)
        onMain { check(island.show("Island gesture QA", 85, context)); check(root.performAccessibilityAction(AccessibilityNodeInfo.ACTION_EXPAND, null)) }
        settle()
        onMain { root.findViewById<View>(R.id.island_close_button).performClick() }
        await("detail close removes the overlay") { !onMain { island.isVisible } }
        verify("detail close leaves the app unopened", opened == 1)
        onMain { check(island.show("Island gesture QA", 85, context)); check(root.performAccessibilityAction(AccessibilityNodeInfo.ACTION_EXPAND, null)) }
        settle()
        onMain { root.findViewById<View>(R.id.island_open_button).performClick() }
        await("detail open removes the overlay") { !onMain { island.isVisible } }
        verify("detail open invokes the injected app action once", opened == 2)

        onMain { check(island.show("Island closing touch QA", 85, context)) }
        settle()
        onMain { island.close() }
        gesture(root, distanceDp = 90f)
        await("touch release during dismissal still removes the overlay") { !onMain { island.isVisible } }
        verify("touch during dismissal cannot reopen the popup or invoke the app", opened == 2)

        onMain { check(island.show("Island closing cancel QA", 85, context)) }
        settle()
        onMain { island.close() }
        gesture(root, distanceDp = 90f, ending = MotionEvent.ACTION_CANCEL)
        await("touch cancellation during dismissal still removes the overlay") { !onMain { island.isVisible } }
        verify("cancelled touch during dismissal preserves window cleanup", opened == 2)

        val shortLived = createIsland(context, timeout = 1400L)
        onMain { check(shortLived.show("Island short drag QA", 85, context)) }
        settle()
        gesture(context.root(), distanceDp = 12f)
        verify("small drag keeps the popup visible without expanding", onMain { shortLived.isVisible && context.root().findViewById<View>(R.id.island_details).visibility == View.GONE })
        await("small drag resumes the automatic dismissal timer") { !onMain { shortLived.isVisible } }
        verify("small drag resumes the automatic dismissal timer", true)
    }

    private fun checkLargeTextLayout() {
        val context = CapturingContext(fontContext(1.8f))
        val island = createIsland(context, timeout = 60_000L)
        onMain { check(island.show(LONG_NAME, 100, context, IslandType.MOVED_TO_OTHER_DEVICE, otherDeviceName = "이름이 아주 긴 예시 태블릿")) }
        settle()
        val root = context.root()
        onMain {
            verify("large text fixture uses its own font scale", abs(root.resources.configuration.fontScale - 1.8f) < .01f)
            verify("compact popup stays inside the display", root.width > 0 && root.width <= context.resources.displayMetrics.widthPixels && root.height <= context.resources.displayMetrics.heightPixels)
            verify("large text compact content stays inside the popup", listOf(R.id.island_product_image, R.id.island_connected_text, R.id.island_device_name, R.id.island_action_button).all { boundsInside(root, root.findViewById(it)) })
            val name = root.findViewById<TextView>(R.id.island_device_name)
            val status = root.findViewById<TextView>(R.id.island_connected_text)
            verify("compact long name and status have bounded lines", name.maxLines == 1 && status.maxLines <= 2)
            check(root.performAccessibilityAction(AccessibilityNodeInfo.ACTION_EXPAND, null))
        }
        settle()
        onMain {
            verify("expanded details retain the full long device name", root.findViewById<TextView>(R.id.island_details_text).text.contains(LONG_NAME))
            verify("large text detail controls stay inside the popup", boundsInside(root, root.findViewById(R.id.island_open_button)) && boundsInside(root, root.findViewById(R.id.island_close_button)))
            verify("large text detail controls have usable touch heights", root.findViewById<View>(R.id.island_open_button).height >= 44f * context.resources.displayMetrics.density && root.findViewById<View>(R.id.island_close_button).height >= 44f * context.resources.displayMetrics.density)
            island.forceClose()
        }
        onMain { check(island.show(LONG_NAME, 100, context)) }
        settle()
        onMain {
            val percentage = root.findViewById<TextView>(R.id.island_battery_text)
            val available = percentage.width - percentage.paddingLeft - percentage.paddingRight
            verify("large text 100 percent stays on one line inside the ring",
                percentage.layout.lineCount == 1 && percentage.paint.measureText(percentage.text.toString()) <= available + 1f)
            island.forceClose()
        }
    }

    private fun createIsland(context: Context, timeout: Long = 4500L, onOpen: (() -> Unit)? = null): IslandWindow = onMain {
        IslandWindow(context, observeDeviceEvents = false, autoCloseMillis = timeout, onOpenApp = onOpen).also(windows::add)
    }

    private fun fontContext(scale: Float): Context = targetContext.createConfigurationContext(
        Configuration(targetContext.resources.configuration).apply { fontScale = scale }
    )

    private fun gesture(root: View, distanceDp: Float, ending: Int = MotionEvent.ACTION_UP) {
        val start = SystemClock.uptimeMillis()
        touch(root, MotionEvent.ACTION_DOWN, start, start, 0f)
        touch(root, MotionEvent.ACTION_MOVE, start, start + 140L, distanceDp)
        touch(root, ending, start, start + 300L, distanceDp)
    }

    private fun touch(root: View, action: Int, downTime: Long, time: Long, distanceDp: Float) = onMain {
        val x = root.width / 2f
        val y = root.height / 3f + distanceDp * root.resources.displayMetrics.density
        val event = MotionEvent.obtain(downTime, time, action, x, y, 0)
        try { check(root.dispatchTouchEvent(event)) { "Popup did not receive action $action" } } finally { event.recycle() }
    }

    private fun boundsInside(root: View, view: View?): Boolean {
        if (view == null || !view.isShown || view.width <= 0 || view.height <= 0) return false
        val rect = Rect(0, 0, view.width, view.height)
        (root as ViewGroup).offsetDescendantRectToMyCoords(view, rect)
        return rect.left >= -1 && rect.top >= -1 && rect.right <= root.width + 1 && rect.bottom <= root.height + 1
    }

    /** Read one screenshot per state; retain no screenshot or personalized screen data. */
    private fun checkRenderedHeader(root: View, state: String) {
        val regions = onMain {
            listOf(R.id.island_device_name, R.id.island_connected_text, R.id.island_battery_container).associateWith { id ->
                val view = root.findViewById<View>(id)
                check(view != null && view.isShown && view.width > 0 && view.height > 0) {
                    "$state header region ${root.resources.getResourceEntryName(id)} is missing"
                }
                val position = IntArray(2)
                view.getLocationOnScreen(position)
                Rect(position[0], position[1], position[0] + view.width, position[1] + view.height)
            }
        }
        val screenshot = uiAutomation.takeScreenshot() ?: error("$state screenshot unavailable")
        try {
            val name = hasHeaderPixels(screenshot, regions.getValue(R.id.island_device_name), "$state name") {
                Color.red(it) > 180 && Color.green(it) > 180 && Color.blue(it) > 180
            }
            val status = hasHeaderPixels(screenshot, regions.getValue(R.id.island_connected_text), "$state status") {
                val red = Color.red(it)
                val green = Color.green(it)
                val blue = Color.blue(it)
                minOf(red, green, blue) >= 120 && maxOf(red, green, blue) - minOf(red, green, blue) <= 35
            }
            val battery = hasHeaderPixels(screenshot, regions.getValue(R.id.island_battery_container), "$state battery") {
                Color.green(it) > 120 && Color.red(it) < 110 && Color.blue(it) < 150
            }
            verify("$state header draws actual name, status and battery pixels", name && status && battery)
        } finally { screenshot.recycle() }
    }

    private fun hasHeaderPixels(bitmap: Bitmap, rect: Rect, label: String, matches: (Int) -> Boolean): Boolean {
        check(rect.left >= 0 && rect.top >= 0 && rect.right <= bitmap.width && rect.bottom <= bitmap.height &&
            rect.width() > 0 && rect.height() > 0) { "$label region is outside the screenshot: $rect" }
        var matched = 0
        for (y in rect.top until rect.bottom) {
            for (x in rect.left until rect.right) {
                if (matches(bitmap.getPixel(x, y)) && ++matched >= 8) return true
            }
        }
        return false
    }

    private fun findText(value: String): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= 34) uiAutomation.clearCache()
        fun contains(node: AccessibilityNodeInfo): Boolean = node.text?.toString() == value ||
            (0 until node.childCount).any { node.getChild(it)?.let(::contains) == true }
        return uiAutomation.windows.any { it.root?.let(::contains) == true }
    }

    private fun settle() { SystemClock.sleep(850L) }

    private fun await(name: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5000L
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(50L)
        }
        error(name)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> onMain(block: () -> T): T {
        var value: T? = null
        runOnMainSync { value = block() }
        return value as T
    }

    private fun verify(name: String, passed: Boolean) {
        check(passed) { name }
        checks += "PASS: $name"
    }

    /** Captures the actual added overlay while forwarding every WindowManager call. */
    private inner class CapturingContext(base: Context, failFirstAttachment: Boolean = false) : ContextWrapper(base) {
        private var failNext = failFirstAttachment
        private var capturedRoot: View? = null
        private val manager: WindowManager by lazy {
            val delegate = targetContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            Proxy.newProxyInstance(WindowManager::class.java.classLoader, arrayOf(WindowManager::class.java)) { _, method, args ->
                if (method.name == "addView" && failNext) {
                    failNext = false
                    throw WindowManager.BadTokenException("Intentional UI test failure")
                }
                try {
                    method.invoke(delegate, *(args ?: emptyArray())).also {
                        if (method.name == "addView") capturedRoot = args!![0] as View
                    }
                } catch (failure: InvocationTargetException) { throw failure.targetException }
            } as WindowManager
        }
        override fun getSystemService(name: String): Any? = when (name) {
            Context.WINDOW_SERVICE -> manager
            Context.LAYOUT_INFLATER_SERVICE -> LayoutInflater.from(baseContext).cloneInContext(this)
            else -> super.getSystemService(name)
        }
        fun root(): View = capturedRoot ?: error("No overlay was attached")
    }

    companion object {
        private const val LONG_NAME = "한글 이름이 아주 긴 사용자의 AirPods Pro 3 연결 테스트용 이름"
    }
}
