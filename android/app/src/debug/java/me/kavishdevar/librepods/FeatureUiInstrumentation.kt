package me.kavishdevar.librepods

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo

/** Renders the real status screen using fixtures. No Bluetooth writes or permission grants. */
class FeatureUiInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        var activity: Activity? = null
        try {
            activity = startActivitySync(Intent(targetContext, NativeUiPreviewActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("scenario", "features"))
            waitForIdleSync()
            val seen = mutableSetOf<String>()
            fun visit(node: AccessibilityNodeInfo?) {
                if (node == null) return
                node.text?.toString()?.let(seen::add)
                repeat(node.childCount) { visit(node.getChild(it)) }
            }
            fun scroll(node: AccessibilityNodeInfo?): Boolean {
                if (node == null) return false
                if (node.isScrollable && node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true
                return (0 until node.childCount).any { scroll(node.getChild(it)) }
            }
            val deadline = SystemClock.uptimeMillis() + 5000
            while (uiAutomation.rootInActiveWindow == null && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
            repeat(20) { visit(uiAutomation.rootInActiveWindow); scroll(uiAutomation.rootInActiveWindow); SystemClock.sleep(250) }
            fun has(resource: Int) = seen.any { it.contains(targetContext.getString(resource)) }
            check(has(R.string.diagnostics_codec_unknown)) { "Unknown codec must stay unknown" }
            check(has(R.string.feature_reason_att_access_required)) { "Stock ATT restriction missing" }
            check(has(R.string.feature_reason_protocol_unverified)) { "Unverified sensors incorrectly exposed" }
            check(has(R.string.feature_reason_apple_service_required)) { "Apple service restriction missing" }
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "\n4 feature UI checks passed; fixture callbacks only\n") })
        } catch (error: Throwable) {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "\nFAILED: ${error.message}\n") })
        } finally { activity?.let { runOnMainSync { it.finish() } } }
    }
}
