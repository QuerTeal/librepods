package me.kavishdevar.librepods

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.Choreographer
import androidx.lifecycle.ViewModelProvider
import me.kavishdevar.librepods.bluetooth.AACPManager.Companion.ControlCommandIdentifiers
import me.kavishdevar.librepods.bluetooth.ControlConnectionPhase
import me.kavishdevar.librepods.data.Battery
import me.kavishdevar.librepods.presentation.viewmodel.AirPodsViewModel
import me.kavishdevar.librepods.services.ServiceManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Debug-only: how long a packet from the buds takes to reach the UI state and the next frame.
 * Packets are injected into the app's own parser; nothing is sent to the AirPods, and the
 * real values are injected back at the end.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class LatencyProbeInstrumentation : Instrumentation() {
    private val lines = mutableListOf<String>()
    private lateinit var model: AirPodsViewModel

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
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
            val service = ServiceManager.getService()!!
            check(await(10_000) { service.controlConnectionState.value.phase == ControlConnectionPhase.READY }) { "AirPods not connected" }
            await(5_000) { model.uiState.value.battery.any { it.level > 0 } }
            SystemClock.sleep(1500)
            probeListeningMode(service)
            probeBattery(service)
        } catch (error: Throwable) {
            result = Activity.RESULT_CANCELED
            lines += "FAILED: ${error.stackTraceToString()}"
        } finally {
            runOnMainSync { activity?.finish() }
        }
        finish(result, Bundle().apply { putString("stream", "\n${lines.joinToString("\n")}\n") })
    }

    private fun inject(service: me.kavishdevar.librepods.services.AirPodsService, packet: ByteArray) =
        runOnMainSync { service.aacpManager.receivePacket(packet) }

    /** Posts the packet to the main thread as the socket reader does, then times state and frame. */
    private fun measure(service: me.kavishdevar.librepods.services.AirPodsService, packet: ByteArray,
                        reached: () -> Boolean): Pair<Double, Double> {
        val start = System.nanoTime()
        var stateAt = 0L
        val framed = CountDownLatch(1)
        android.os.Handler(targetContext.mainLooper).post {
            service.aacpManager.receivePacket(packet)
        }
        // Poll on the main thread so the reading includes whatever is queued there.
        while (true) {
            var ok = false
            runOnMainSync { ok = reached() }
            if (ok) { stateAt = System.nanoTime(); break }
            if (System.nanoTime() - start > 3_000_000_000L) error("State never updated")
            SystemClock.sleep(1)
        }
        runOnMainSync { Choreographer.getInstance().postFrameCallback { framed.countDown() } }
        framed.await(2, TimeUnit.SECONDS)
        val frameAt = System.nanoTime()
        return (stateAt - start) / 1e6 to (frameAt - start) / 1e6
    }

    private fun probeListeningMode(service: me.kavishdevar.librepods.services.AirPodsService) {
        val id = ControlCommandIdentifiers.LISTENING_MODE
        val original = model.uiState.value.controlStates[id]?.firstOrNull()?.toInt()?.and(0xff) ?: return
        val other = if (original == 3) 2 else 3
        val results = mutableListOf<Pair<Double, Double>>()
        repeat(10) { i ->
            val value = if (i % 2 == 0) other else original
            results += measure(service, byteArrayOf(4, 0, 4, 0, 9, 0, id.value, value.toByte(), 0, 0, 0)) {
                model.uiState.value.controlStates[id]?.firstOrNull()?.toInt()?.and(0xff) == value
            }
            SystemClock.sleep(300)
        }
        inject(service, byteArrayOf(4, 0, 4, 0, 9, 0, id.value, original.toByte(), 0, 0, 0))
        report("listening mode", results)
    }

    private fun batteryPacket(list: List<Battery>): ByteArray {
        val out = mutableListOf<Byte>(4, 0, 4, 0, 4, 0, list.size.toByte())
        list.forEach { out += listOf(it.component.toByte(), 1, it.level.toByte(), it.status.toByte(), 1) }
        return out.toByteArray()
    }

    private fun probeBattery(service: me.kavishdevar.librepods.services.AirPodsService) {
        val real = model.uiState.value.battery
        if (real.size != 3) return
        val results = mutableListOf<Pair<Double, Double>>()
        repeat(10) { i ->
            val left = real[0].let { if (i % 2 == 0) it.copy(level = if (it.level > 50) 40 else 60) else it }
            val next = listOf(left, real[1], real[2])
            results += measure(service, batteryPacket(next)) { model.uiState.value.battery.firstOrNull()?.level == left.level }
            SystemClock.sleep(300)
        }
        inject(service, batteryPacket(real))
        report("battery", results)
    }

    private fun report(name: String, results: List<Pair<Double, Double>>) {
        fun median(v: List<Double>) = v.sorted()[v.size / 2]
        lines += "%s: state median %.1f ms (max %.1f), next frame median %.1f ms (max %.1f)".format(name,
            median(results.map { it.first }), results.maxOf { it.first },
            median(results.map { it.second }), results.maxOf { it.second })
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
}
