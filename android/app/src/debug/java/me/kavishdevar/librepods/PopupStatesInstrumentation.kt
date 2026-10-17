package me.kavishdevar.librepods

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.provider.Settings
import me.kavishdevar.librepods.data.AirPodsPro3
import me.kavishdevar.librepods.data.Battery
import me.kavishdevar.librepods.data.BatteryComponent
import me.kavishdevar.librepods.data.BatteryStatus
import me.kavishdevar.librepods.presentation.overlays.PopupWindow
import me.kavishdevar.librepods.presentation.overlays.PopupWindow.OptimizedCharge

/**
 * Local UI fixture: shows the connection popup in a chosen state, or plays a timed script of
 * states, to compare with recordings of iOS. No Bluetooth, nothing persisted.
 *
 * Arguments: stage (incase|apart), charge (None|Message|MessageAndButton), level, case, ear, hold (s),
 * and script, e.g. "600:Message,2600:MessageAndButton,5000:apart,8000:incase:None", each step
 * "milliseconds after opening:stage, charge or close[:charge]".
 */
class PopupStatesInstrumentation : Instrumentation() {
    private var arguments: Bundle? = null

    override fun onCreate(arguments: Bundle?) {
        this.arguments = arguments
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val args = arguments ?: Bundle()
        val level = args.getString("level", "80").toInt()
        val caseLevel = args.getString("case", "83").toInt()
        val holdMs = (args.getString("hold", "6").toDouble() * 1000).toLong()
        var inCase = args.getString("stage", "incase") == "incase"
        var charge = OptimizedCharge.valueOf(args.getString("charge", "MessageAndButton"))
        // How the buds on charge report: "ble" (the case's advertisement) or "aacp" (the buds).
        var budStatus = BatteryStatus.CHARGING
        val script = args.getString("script", "").split(',').filter { it.isNotBlank() }.map { step ->
            val parts = step.split(':')
            parts[0].toLong() to parts.drop(1)
        }
        // Taken out: the left bud stays in the case on charge and the right one is out, as recorded.
        fun batteries() = listOf(
            Battery(BatteryComponent.LEFT, level, budStatus),
            Battery(BatteryComponent.RIGHT, level, if (inCase) budStatus else BatteryStatus.NOT_CHARGING),
            Battery(BatteryComponent.CASE, caseLevel, BatteryStatus.NOT_CHARGING)
        )
        // ear=false leaves out ear detection, as before the AirPods connect here.
        val withEar = args.getString("ear", "true").toBoolean()
        fun earStatus() = if (!withEar) null else if (inCase) byteArrayOf(0x02, 0x02) else byteArrayOf(0x02, 0x01)

        var popup: PopupWindow? = null
        var resultCode = Activity.RESULT_OK
        val log = mutableListOf<String>()
        try {
            check(Settings.canDrawOverlays(targetContext)) { "Overlay permission must already be granted by the user" }
            runOnMainSync {
                popup = PopupWindow(targetContext, {}, AirPodsPro3(), observeDeviceEvents = false,
                    optimizedCharge = { charge })
                popup!!.open("승우의 AirPods Pro", batteries(), earStatus())
            }
            log += "opened inCase=$inCase charge=$charge"
            var elapsed = 0L
            for ((at, actions) in script) {
                if (at > elapsed) Thread.sleep(at - elapsed)
                elapsed = maxOf(elapsed, at)
                var stageChanged = false
                var close = false
                for (action in actions) {
                    when (action) {
                        "incase" -> { inCase = true; stageChanged = true }
                        "apart" -> { inCase = false; stageChanged = true }
                        "close" -> close = true
                        "aacp" -> { budStatus = BatteryStatus.OPTIMIZED_CHARGING; stageChanged = true }
                        "ble" -> { budStatus = BatteryStatus.CHARGING; stageChanged = true }
                        else -> charge = OptimizedCharge.valueOf(action)
                    }
                }
                runOnMainSync {
                    when {
                        close -> popup!!.close()
                        stageChanged -> popup!!.update(batteries(), earStatus())
                        else -> popup!!.refreshOptimized()
                    }
                }
                log += "${at}ms inCase=$inCase charge=$charge"
            }
            if (holdMs > elapsed) Thread.sleep(holdMs - elapsed)
        } catch (e: Throwable) {
            resultCode = Activity.RESULT_CANCELED
            log += "FAILED: $e"
        } finally {
            runOnMainSync { popup?.dismissImmediately() }
        }
        finish(resultCode, Bundle().apply { putString("stream", log.joinToString("\n") + "\n") })
    }
}
