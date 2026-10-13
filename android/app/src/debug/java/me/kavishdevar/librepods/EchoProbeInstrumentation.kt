package me.kavishdevar.librepods

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import me.kavishdevar.librepods.bluetooth.AACPManager.Companion.ControlCommandIdentifiers
import me.kavishdevar.librepods.bluetooth.ControlConnectionPhase
import me.kavishdevar.librepods.bluetooth.SettingPhase
import me.kavishdevar.librepods.presentation.viewmodel.AirPodsViewModel
import me.kavishdevar.librepods.services.ServiceManager

/**
 * Debug-only: reports which control settings the connected AirPods echo after a write.
 * Only reversible preference settings with a reported baseline are changed, and each is restored.
 * Settings without a baseline are listed, never written.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class EchoProbeInstrumentation : Instrumentation() {
    private val lines = mutableListOf<String>()
    private lateinit var model: AirPodsViewModel

    /** `-e set NAME=hex` writes one value without restoring it, to check persistence across a reconnect. */
    private var set: Pair<ControlCommandIdentifiers, ByteArray>? = null

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        set = arguments?.getString("set")?.split("=")?.let { (name, hex) ->
            ControlCommandIdentifiers.valueOf(name) to hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
        start()
    }

    override fun onStart() {
        var activity: MainActivity? = null
        var result = Activity.RESULT_OK
        try {
            activity = startActivitySync(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
            runOnMainSync { model = ViewModelProvider(activity)[AirPodsViewModel::class.java] }
            check(await(15_000) { model.isReady && ServiceManager.getService()?.controlConnectionState?.value?.phase == ControlConnectionPhase.READY }) {
                "AACP session not ready"
            }
            SystemClock.sleep(3000) // let the initial status burst finish
            val single = set
            if (single != null) {
                val (id, value) = single
                lines += "SET ${id.name}: ${hex(model.uiState.value.controlStates[id])} -> ${hex(value)}; echo=${write(id, value)}"
            } else {
                val unreported = ControlCommandIdentifiers.entries.filter { model.uiState.value.controlStates[it] == null }
                lines += "NO BASELINE: " + unreported.joinToString { it.name }
                for ((id, change) in probes) probe(id, change)
            }
        } catch (error: Throwable) {
            result = Activity.RESULT_CANCELED
            lines += "FAILED: ${error.stackTraceToString()}"
        } finally {
            runOnMainSync { activity?.finish() }
        }
        finish(result, Bundle().apply { putString("stream", "\n" + lines.joinToString("\n") + "\n") })
    }

    private fun probe(id: ControlCommandIdentifiers, change: (ByteArray) -> ByteArray) {
        val original = model.uiState.value.controlStates[id]?.copyOf() ?: run {
            lines += "SKIP ${id.name}: no baseline"; return
        }
        val desired = change(original.copyOf())
        val echoed = write(id, desired)
        val restored = write(id, original)
        lines += "${if (echoed) "ECHO" else "NO ECHO"} ${id.name}: ${hex(original)} -> ${hex(desired)}; " +
            "restore ${if (restored) "confirmed" else "unconfirmed"}; now ${hex(model.uiState.value.controlStates[id])}"
    }

    private fun write(id: ControlCommandIdentifiers, value: ByteArray): Boolean {
        val before = model.uiState.value.commandStates[id.value]?.revision ?: 0
        runOnMainSync { model.setControlCommandValue(id, value) }
        return await(4_000) {
            val state = model.uiState.value
            val command = state.commandStates[id.value]
            command != null && command.revision > before && command.phase == SettingPhase.CONFIRMED &&
                (command.verifiedAtMillis ?: -1) >= (command.sentAtMillis ?: Long.MAX_VALUE) &&
                state.controlStates[id]?.contentEquals(value) == true
        }
    }

    private fun await(timeout: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return true
            SystemClock.sleep(100)
        }
        return condition()
    }

    private fun hex(value: ByteArray?) = value?.joinToString(" ") { "%02x".format(it) } ?: "null"

    private companion object {
        fun flip12(v: ByteArray) = v.also { it[0] = if (it[0] == 1.toByte()) 2 else 1 }
        fun cycle3(v: ByteArray) = v.also { it[0] = ((it[0] + 1) % 3).toByte() }
        fun nudge(v: ByteArray) = v.also { it[0] = (if ((it[0].toInt() and 0xff) >= 100) it[0] - 1 else it[0] + 1).toByte() }

        val probes: List<Pair<ControlCommandIdentifiers, (ByteArray) -> ByteArray>> = listOf(
            ControlCommandIdentifiers.CONVERSATION_DETECT_CONFIG to ::flip12,
            ControlCommandIdentifiers.ADAPTIVE_VOLUME_CONFIG to ::flip12,
            ControlCommandIdentifiers.ONE_BUD_ANC_MODE to ::flip12,
            ControlCommandIdentifiers.SLEEP_DETECTION_CONFIG to ::flip12,
            ControlCommandIdentifiers.DYNAMIC_END_OF_CHARGE to ::flip12,
            ControlCommandIdentifiers.VOLUME_SWIPE_MODE to ::flip12,
            ControlCommandIdentifiers.HPS_GAIN_SWIPE to ::flip12,
            ControlCommandIdentifiers.CLICK_HOLD_INTERVAL to ::cycle3,
            ControlCommandIdentifiers.DOUBLE_CLICK_INTERVAL to ::cycle3,
            ControlCommandIdentifiers.VOLUME_SWIPE_INTERVAL to ::cycle3,
            ControlCommandIdentifiers.AUTO_ANC_STRENGTH to ::nudge,
            ControlCommandIdentifiers.CHIME_VOLUME to ::nudge,
            ControlCommandIdentifiers.CALL_MANAGEMENT_CONFIG to { v -> v.also { if (it.size > 1) it[1] = if (it[1] == 3.toByte()) 2 else 3 } },
        )
    }
}
