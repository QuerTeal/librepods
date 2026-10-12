package me.kavishdevar.librepods

import android.app.Activity
import android.app.Instrumentation
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.telecom.TelecomManager
import androidx.lifecycle.ViewModelProvider
import me.kavishdevar.librepods.bluetooth.AACPManager.Companion.ControlCommandIdentifiers
import me.kavishdevar.librepods.bluetooth.ControlConnectionPhase
import me.kavishdevar.librepods.bluetooth.SettingPhase
import me.kavishdevar.librepods.data.hasReportedLevel
import me.kavishdevar.librepods.data.BatteryComponent
import me.kavishdevar.librepods.data.BatteryStatus
import me.kavishdevar.librepods.presentation.viewmodel.AirPodsViewModel
import me.kavishdevar.librepods.services.ServiceManager

/** Debug-only live protocol checks. Settings are exercised only with an explicit runner argument. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class DeviceSmokeInstrumentation : Instrumentation() {
    private var exerciseSettings = false
    private var baselineOnly = false
    private val checks = mutableListOf<String>()
    private lateinit var model: AirPodsViewModel

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        exerciseSettings = arguments?.getString("exerciseSettings") == "true"
        baselineOnly = arguments?.getString("baselineOnly") == "true"
        start()
    }

    override fun onStart() {
        var activity: MainActivity? = null
        var result = Activity.RESULT_OK
        try {
            activity = startActivitySync(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
            runOnMainSync { model = ViewModelProvider(activity)[AirPodsViewModel::class.java] }
            check(await(10_000) { model.isReady && ServiceManager.getService() != null }) { "App service did not initialize" }
            verify("Actual activity ViewModel initialized", model.isReady)
            val service = ServiceManager.getService()!!
            await(10_000) { service.controlConnectionState.value.phase == ControlConnectionPhase.READY }
            checks += "OBSERVED connection=${service.controlConnectionState.value.phase}; reason=${service.controlConnectionState.value.reason}"
            checks += "OBSERVED battery=" + model.uiState.value.battery.joinToString { battery ->
                "${battery.getComponentName()}:${if (battery.hasReportedLevel()) battery.level.toString() else "unknown"}/${battery.getStatusName()}"
            }
            checks += "OBSERVED vendorIdHook=${model.uiState.value.vendorIdHook}; confirmed=${model.uiState.value.commandStates.values.count { it.phase == SettingPhase.CONFIRMED }}"
            for (identifier in listOf(ControlCommandIdentifiers.LISTENING_MODE, ControlCommandIdentifiers.LISTENING_MODE_CONFIGS)) {
                val state = model.uiState.value
                checks += "OBSERVED ${identifier.name}=${state.controlStates[identifier]?.firstOrNull()?.toInt()?.and(0xff)}; phase=${state.commandStates[identifier.value]?.phase}"
            }
            if (service.controlConnectionState.value.phase != ControlConnectionPhase.READY) {
                checks += "SKIP live writes: control connection is not READY"
            } else {
                verify("Live AACP session is ready", model.uiState.value.isLocallyConnected)
                if (exerciseSettings) {
                    val telecom = targetContext.getSystemService(TelecomManager::class.java)
                    if (targetContext.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
                        checks += "SKIP live writes: phone state permission unavailable"
                    else if (telecom.isInCall) checks += "SKIP live writes: a call is active"
                    else if (model.uiState.value.battery.none { it.hasReportedLevel() }) {
                        checks += "SKIP live writes: AirPods component availability is unknown"
                        if (baselineOnly) checkNoiseMode()
                    }
                    else {
                        checkRapidChoices()
                        checkNoiseMode()
                    }
                }
                verify("Session remains ready after checks", service.controlConnectionState.value.phase == ControlConnectionPhase.READY)
            }
        } catch (error: Throwable) {
            result = Activity.RESULT_CANCELED
            checks += "FAILED: ${error.stackTraceToString()}"
        } finally {
            runOnMainSync { activity?.finish() }
        }
        finish(result, Bundle().apply { putString("stream", "\n${checks.joinToString("\n")}\n${checks.count { it.startsWith("PASS") }} live device checks passed\n") })
    }

    @android.annotation.SuppressLint("UseKtx") // Check that restoration was durably committed.
    private fun checkRapidChoices() {
        val identifier = ControlCommandIdentifiers.LISTENING_MODE_CONFIGS
        val original = model.uiState.value.controlStates[identifier]?.firstOrNull()?.toInt()?.and(0xff)
        if (original == null || (original and 0x0f).countOneBits() < 2) {
            checks += "SKIP rapid choices: no valid confirmed cycle configuration"
            return
        }
        // These are the two UI choices exposed by Adaptive Audio models.
        if (model.uiState.value.capabilities.none { it.name == "ADAPTIVE_AUDIO" }) {
            checks += "SKIP rapid choices: Adaptive Audio unavailable"
            return
        }
        fun toggle(value: Int, bit: Int): Int {
            if (value and bit == 0) return value or bit
            val removed = value and bit.inv()
            return if ((removed and 0x0f).countOneBits() >= 2) removed else value
        }
        val expected = toggle(toggle(original, 0x08), 0x01)
        if (expected == original) {
            checks += "SKIP rapid choices: this baseline prevents both removals"
            return
        }
        val prefs = targetContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val hadPreference = prefs.contains("long_press_byte")
        val savedPreference = prefs.getInt("long_press_byte", 0)
        try {
            val before = model.uiState.value.commandStates[identifier.value]?.revision ?: 0
            runOnMainSync {
                model.toggleListeningMode(0x08)
                model.toggleListeningMode(0x01)
            }
            verify("Rapid choices receive combined hardware echo (0x${expected.toString(16)})", awaitEcho(identifier, expected, before))
        } finally {
            val before = model.uiState.value.commandStates[identifier.value]?.revision ?: 0
            runOnMainSync { model.setControlCommandByte(identifier, original.toByte()) }
            val restored = awaitEcho(identifier, original, before)
            val editor = prefs.edit()
            if (hadPreference) editor.putInt("long_press_byte", savedPreference) else editor.remove("long_press_byte")
            check(editor.commit()) { "Preference restoration failed" }
            verify("Original listening cycle restored and confirmed", restored)
        }
    }

    private fun checkNoiseMode() {
        val identifier = ControlCommandIdentifiers.LISTENING_MODE
        val original = model.uiState.value.controlStates[identifier]?.firstOrNull()?.toInt()?.and(0xff)
        if (original !in 1..4) {
            checks += "SKIP noise mode: no confirmed baseline"
            return
        }
        val buds = model.uiState.value.battery.filter { it.component != BatteryComponent.CASE && it.hasReportedLevel() }
        val charging = buds.isNotEmpty() && buds.all { it.status in listOf(BatteryStatus.CHARGING, BatteryStatus.OPTIMIZED_CHARGING) }
        val desired = if (baselineOnly || charging) original!! else if (original == 2) 3 else 2
        if (baselineOnly) {
            checks += "CHECK same-value requests only: preserve confirmed noise mode"
            val before = model.uiState.value.commandStates[identifier.value]?.revision ?: 0
            repeat(2) { index ->
                runOnMainSync { model.setControlCommandByte(identifier, original!!.toByte()) }
                SystemClock.sleep(350)
                val state = model.uiState.value
                val command = state.commandStates[identifier.value]
                verify("Confirmed noise mode request ${index + 1} preserves state without a new pending write",
                    command?.revision == before && command.phase == SettingPhase.CONFIRMED &&
                        state.controlStates[identifier]?.firstOrNull()?.toInt()?.and(0xff) == original)
            }
            return
        }
        if (charging) checks += "SKIP active noise change: buds are charging; verify same-value readback instead"
        try {
            val before = model.uiState.value.commandStates[identifier.value]?.revision ?: 0
            runOnMainSync { model.setControlCommandByte(identifier, desired.toByte()) }
            verify("Noise mode receives fresh hardware confirmation ($desired)", awaitEcho(identifier, desired, before))
        } finally {
            val before = model.uiState.value.commandStates[identifier.value]?.revision ?: 0
            runOnMainSync { model.setControlCommandByte(identifier, original!!.toByte()) }
            verify("Original noise mode restored and freshly confirmed", awaitEcho(identifier, original!!, before))
        }
    }

    private fun awaitEcho(identifier: ControlCommandIdentifiers, value: Int, afterRevision: Long): Boolean = await(6_000) {
        val state = model.uiState.value
        val command = state.commandStates[identifier.value]
        command != null && command.revision > afterRevision && command.phase == SettingPhase.CONFIRMED &&
            (command.verifiedAtMillis ?: -1) >= (command.sentAtMillis ?: Long.MAX_VALUE) &&
            state.controlStates[identifier]?.firstOrNull()?.toInt()?.and(0xff) == value
    }

    private fun await(timeout: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < deadline) {
            var matched = false
            runOnMainSync { matched = condition() }
            if (matched) return true
            SystemClock.sleep(100)
        }
        return false
    }

    private fun verify(name: String, condition: Boolean) {
        check(condition) { name }
        checks += "PASS: $name"
    }
}
