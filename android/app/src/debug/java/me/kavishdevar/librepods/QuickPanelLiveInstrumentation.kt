package me.kavishdevar.librepods

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import me.kavishdevar.librepods.bluetooth.AACPManager.Companion.ControlCommandIdentifiers
import me.kavishdevar.librepods.bluetooth.ControlConnectionPhase
import me.kavishdevar.librepods.bluetooth.ShownControl
import me.kavishdevar.librepods.services.ServiceManager

/**
 * Debug-only: toggles conversation awareness in the live quick settings panel (current app design)
 * and checks that the panel follows the delivered write, then restores the original value.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class QuickPanelLiveInstrumentation : Instrumentation() {
    private val checks = mutableListOf<String>()
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    override fun onStart() {
        var main: Activity? = null
        var panel: Activity? = null
        var result = Activity.RESULT_OK
        val id = ControlCommandIdentifiers.CONVERSATION_DETECT_CONFIG.value
        try {
            main = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            await { ServiceManager.getService()?.controlConnectionState?.value?.phase == ControlConnectionPhase.READY }
            val service = ServiceManager.getService()!!
            fun shown() = ShownControl.boolean(service.aacpManager.commandTracker.states.value[id])
            await { shown() != null }
            SystemClock.sleep(1500)
            val original = shown()!!
            runOnMainSync { main?.finish() }
            main = null
            panel = startActivitySync(Intent(targetContext, QuickSettingsDialogActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val label = targetContext.getString(R.string.conversational_awareness)
            // One UI exposes a checkable switch; the Apple panel a plain clickable circle.
            fun control(): AccessibilityNodeInfo =
                find(root()) { it.isCheckable && text(it).contains(label) }
                    ?: find(root()) { it.contentDescription == "Conversational Awareness" }?.let { icon ->
                        generateSequence(icon) { it.parent }.firstOrNull { it.isClickable } }
                    ?: error("Conversation control missing")
            await { runCatching { control() }.isSuccess }
            val checkable = control().isCheckable
            checks += "OBSERVED panel=${if (checkable) "checkable switch" else "clickable circle"}; original=$original"
            if (checkable) verify("Panel switch starts at the AirPods value", control().isChecked == original)
            verify("Tap is accepted", control().performAction(AccessibilityNodeInfo.ACTION_CLICK))
            await { shown() == !original }
            if (checkable) await { control().isChecked == !original }
            SystemClock.sleep(6000) // past the 5 s confirmation timeout
            verify("Panel keeps the delivered value after the confirmation timeout",
                shown() == !original && (!checkable || control().isChecked == !original))
            verify("No failure notice", !text(root()).contains(targetContext.getString(R.string.setting_failed_notice)))
            control().performAction(AccessibilityNodeInfo.ACTION_CLICK)
            await { shown() == original }
            if (checkable) await { control().isChecked == original }
            verify("Original value restored", shown() == original)
        } catch (error: Throwable) {
            result = Activity.RESULT_CANCELED
            checks += "FAILED: ${error.message}"
        } finally {
            runOnMainSync { panel?.finish(); main?.finish() }
        }
        finish(result, Bundle().apply { putString("stream", "\n${checks.joinToString("\n")}\n${checks.count { it.startsWith("PASS") }} quick panel live checks passed\n") })
    }

    private fun root(): AccessibilityNodeInfo {
        if (android.os.Build.VERSION.SDK_INT >= 34) uiAutomation.clearCache()
        return (uiAutomation.rootInActiveWindow ?: error("No active UI")).apply { refresh() }
    }
    private fun text(node: AccessibilityNodeInfo): String = buildString {
        append(node.text ?: ""); append(node.contentDescription ?: "")
        repeat(node.childCount) { node.getChild(it)?.let { append(text(it)) } }
    }
    private fun find(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        repeat(node.childCount) { node.getChild(it)?.let { child -> find(child, predicate)?.let { return it } } }
        return null
    }
    private fun await(condition: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < until) {
            if (runCatching(condition).getOrDefault(false)) return
            SystemClock.sleep(100)
        }
        error("Timed out waiting for the quick panel")
    }
    private fun verify(name: String, success: Boolean) { check(success) { name }; checks += "PASS: $name" }
}
