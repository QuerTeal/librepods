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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Local UI fixtures only: no Bluetooth commands, pairing or permission grants. */
class ConnectionUiInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val checks = mutableListOf<String>()
        var popup: PopupWindow? = null
        var resultCode = Activity.RESULT_OK
        try {
            check(Settings.canDrawOverlays(targetContext)) { "Overlay permission must already be granted by the user" }
            val batteries = listOf(
                Battery(BatteryComponent.LEFT, 0, BatteryStatus.CHARGING),
                Battery(BatteryComponent.RIGHT, 65, BatteryStatus.NOT_CHARGING),
                Battery(BatteryComponent.CASE, 0, BatteryStatus.DISCONNECTED)
            )
            var callbacks = 0
            runOnMainSync {
                popup = PopupWindow(targetContext, { callbacks++ }, AirPodsPro3())
                popup!!.open("Connection QA", batteries)
                check(popup!!.isOpen)
                popup!!.update(batteries)
                popup!!.dismissImmediately()
                check(!popup!!.isOpen)
                check(callbacks == 1)
                popup!!.dismissImmediately()
                check(callbacks == 1)
            }
            checks += "PASS rapid open/update/lid-close removes the overlay once"
            waitForIdleSync()
            val closed = CountDownLatch(1)
            runOnMainSync {
                popup = PopupWindow(targetContext, { closed.countDown() }, AirPodsPro3())
                popup!!.open("Connection QA", batteries, byteArrayOf(2, 2))
            }
            waitForIdleSync()
            runOnMainSync { popup!!.close() }
            check(closed.await(5, TimeUnit.SECONDS)) { "Animated popup never closed" }
            runOnMainSync {
                check(!popup!!.isOpen)
                popup!!.open("Connection QA", batteries)
                check(popup!!.isOpen)
                popup!!.dismissImmediately()
            }
            checks += "PASS animated dismissal and reopening recover correctly"
            val registration = targetContext.packageManager.getActivityInfo(
                android.content.ComponentName(targetContext, CompanionRegistrationActivity::class.java), 0)
            check(!registration.exported)
            checks += "PASS device registration is an internal user-initiated activity"
        } catch (e: Throwable) {
            resultCode = Activity.RESULT_CANCELED
            checks += "FAILED: $e"
        } finally {
            runOnMainSync { popup?.dismissImmediately() }
        }
        finish(resultCode, Bundle().apply { putString("stream", checks.joinToString("\n")) })
    }
}
