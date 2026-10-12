package me.kavishdevar.librepods

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import me.kavishdevar.librepods.bluetooth.ControlConnectionPhase
import me.kavishdevar.librepods.services.ServiceManager

/** Real preview gestures and service preference changes; no live AirPods setting writes. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class SettingsUiInstrumentation : Instrumentation() {
    private val checks = mutableListOf<String>()
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        var main: Activity? = null
        var preview: Activity? = null
        val prefs = targetContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val originalPhoneBattery = prefs.getBoolean("show_phone_battery_in_widget", true)
        var result = Activity.RESULT_OK
        try {
            main = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            await { ServiceManager.getService() != null }
            val service = ServiceManager.getService()!!
            // Let session initialization finish before comparing command requests.
            await { service.controlConnectionState.value.phase !in setOf(ControlConnectionPhase.CONNECTING, ControlConnectionPhase.INITIALIZING) }
            SystemClock.sleep(1500)
            val audio = targetContext.getSystemService(AudioManager::class.java)
            val originalVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            fun requests() = service.aacpManager.commandTracker.states.value
                .filterValues { it.revision > 0 }.mapValues { (_, value) -> value.revision to value.desired }
            val originalRequests = requests()
            val preferenceKeys = listOf("takeover_when_disconnected", "takeover_when_idle", "takeover_when_music",
                "takeover_when_call", "takeover_when_ringing_call", "takeover_when_media_start", "automatic_ear_detection")
            val originalPreferences = prefs.all.filterKeys { it in preferenceKeys }
            preview = startActivitySync(Intent(targetContext, QuickSettingsDialogActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(QuickSettingsDialogActivity.EXTRA_PREVIEW, true))
            await { text(root()).contains(targetContext.getString(R.string.control_preview_notice)) }
            verify("Preview is visibly identified", true)
            val mode = targetContext.getString(R.string.transparency)
            verify("Sample starts with a different selected mode", !target(mode).isChecked)
            verify("Preview mode accepts a real accessibility click", target(mode).performAction(AccessibilityNodeInfo.ACTION_CLICK))
            await { target(mode).isChecked }
            verify("Sample mode changes", target(mode).isChecked)
            val conversation = targetContext.getString(R.string.conversational_awareness)
            val previousConversation = target(conversation).isChecked
            target(conversation).performAction(AccessibilityNodeInfo.ACTION_CLICK)
            await { target(conversation).isChecked != previousConversation }
            verify("Sample conversation switch changes", true)
            val slider = find(root()) { it.rangeInfo != null } ?: error("Preview volume slider missing")
            verify("Preview volume accepts a real gesture action", slider.performAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,
                Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 0.2f) }))
            await { find(root()) { it.rangeInfo != null }?.rangeInfo?.current == 0.2f }
            verify("Sample volume reaches 20 percent", true)
            verify("Phone media volume remains unchanged", audio.getStreamVolume(AudioManager.STREAM_MUSIC) == originalVolume)
            verify("Preview sends no control requests", requests() == originalRequests)
            verify("Preview preserves app behavior preferences", prefs.all.filterKeys { it in preferenceKeys } == originalPreferences)
            runOnMainSync { preview?.finish() }
            preview = null
            val field = service.javaClass.getDeclaredField("phoneBatteryReceiverRegistered").apply { isAccessible = true }
            fun setPhoneBattery(enabled: Boolean) {
                runOnMainSync { prefs.edit().putBoolean("show_phone_battery_in_widget", enabled).apply() }
                await { service.widgetMobileBatteryEnabled == enabled && field.getBoolean(service) == enabled }
            }
            setPhoneBattery(false)
            verify("Disabling phone battery unregisters the receiver", !field.getBoolean(service))
            setPhoneBattery(true)
            verify("Enabling phone battery registers without a service restart", field.getBoolean(service) && ServiceManager.getService() === service)
            setPhoneBattery(true)
            verify("Repeated enable keeps the same service and receiver state", field.getBoolean(service) && ServiceManager.getService() === service)
            setPhoneBattery(originalPhoneBattery)
            verify("Original phone battery setting is restored", prefs.getBoolean("show_phone_battery_in_widget", !originalPhoneBattery) == originalPhoneBattery)
        } catch (error: Throwable) {
            result = Activity.RESULT_CANCELED
            checks += "FAILED: ${error.message}"
        } finally {
            runOnMainSync {
                prefs.edit().putBoolean("show_phone_battery_in_widget", originalPhoneBattery).apply()
                preview?.finish(); main?.finish()
            }
        }
        finish(result, Bundle().apply { putString("stream", "\n${checks.joinToString("\n")}\n${checks.count { it.startsWith("PASS") }} settings device checks passed\n") })
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
    private fun target(label: String): AccessibilityNodeInfo =
        find(root()) { it.isCheckable && text(it).contains(label) } ?: error("Control missing: $label")
    private fun await(condition: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < until) {
            if (runCatching(condition).getOrDefault(false)) return
            SystemClock.sleep(100)
        }
        error("Timed out waiting for settings UI")
    }
    private fun verify(name: String, success: Boolean) { check(success) { name }; checks += "PASS: $name" }
}
