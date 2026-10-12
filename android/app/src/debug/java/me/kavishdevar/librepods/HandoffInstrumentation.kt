package me.kavishdevar.librepods

import android.Manifest
import android.app.Activity
import android.app.Instrumentation
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import me.kavishdevar.librepods.bluetooth.ConnectionIntent
import me.kavishdevar.librepods.services.ServiceManager

/** Run only after a real handoff. It never simulates an external disconnect or clears a hold. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class HandoffInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    @android.annotation.SuppressLint("MissingPermission")
    override fun onStart() {
        var main: Activity? = null
        val checks = mutableListOf<String>()
        var result = Activity.RESULT_OK
        try {
            check(targetContext.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
            main = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val deadline = SystemClock.elapsedRealtime() + 10_000
            while (ServiceManager.getService() == null && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
            val service = ServiceManager.getService() ?: error("Service unavailable")
            val prefs = targetContext.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
            check(prefs.getBoolean("control_handoff_paused", false) && service.automaticReconnectPaused.value) {
                "SKIP: a real handoff must establish the hold first"
            }
            checks += "PASS: real handoff hold survives process restart"
            val adapter = targetContext.getSystemService(BluetoothManager::class.java).adapter
            val target = adapter.bondedDevices.firstOrNull { it.address == prefs.getString("mac_address", null) }
                ?: error("Selected paired AirPods unavailable")
            val controlBefore = service.controlConnectionRequests
            val profilesBefore = service.profileConnectionRequests
            for (intent in listOf(ConnectionIntent.EXISTING_LINK, ConnectionIntent.ADVERTISEMENT, ConnectionIntent.RETRY, ConnectionIntent.TAKEOVER)) {
                runOnMainSync { service.connectToSocket(adapter, target, intent = intent) }
                waitForIdleSync()
                check(service.controlConnectionRequests == controlBefore)
                checks += "PASS: held control request blocked for $intent"
            }
            for (intent in ConnectionIntent.entries.filter { it != ConnectionIntent.USER }) {
                runOnMainSync { service.connectAudio(targetContext, target, intent) }
                waitForIdleSync()
                check(service.profileConnectionRequests == profilesBefore)
                checks += "PASS: held audio request blocked for $intent"
            }
            fun text(node: AccessibilityNodeInfo?): String = buildString {
                if (node != null) {
                    append(node.text ?: ""); append(node.contentDescription ?: "")
                    repeat(node.childCount) { append(text(node.getChild(it))) }
                }
            }
            val until = SystemClock.elapsedRealtime() + 5000
            var noticeVisible = false
            while (!noticeVisible && SystemClock.elapsedRealtime() < until) {
                if (android.os.Build.VERSION.SDK_INT >= 34) uiAutomation.clearCache()
                noticeVisible = text(uiAutomation.rootInActiveWindow).contains(targetContext.getString(R.string.handoff_paused_description))
                SystemClock.sleep(100)
            }
            check(noticeVisible) { "Handoff notice missing" }
            checks += "PASS: reconnect hold and manual return are explained on screen"
            check(service.automaticReconnectPaused.value && prefs.getBoolean("control_handoff_paused", false))
            checks += "PASS: blocked requests leave the real handoff hold intact"
        } catch (error: Throwable) {
            result = Activity.RESULT_CANCELED
            checks += "FAILED: ${error.message}"
        } finally { runOnMainSync { main?.finish() } }
        finish(result, Bundle().apply { putString("stream", "\n${checks.joinToString("\n")}\n${checks.count { it.startsWith("PASS") }} handoff boundary checks passed\n") })
    }
}
