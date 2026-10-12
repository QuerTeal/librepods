package me.kavishdevar.librepods.services

import android.Manifest
import android.annotation.SuppressLint
import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.RequiresApi
import me.kavishdevar.librepods.bluetooth.CompanionRegistration

class AirPodsCompanionService : CompanionDeviceService() {
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        wakeFor(associationInfo)
    }

    @RequiresApi(36)
    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        if (event.event == DevicePresenceEvent.EVENT_BT_CONNECTED ||
            event.event == DevicePresenceEvent.EVENT_BLE_APPEARED) {
            val selected = CompanionRegistration.selectedAssociation(this) ?: return
            if (selected.id == event.associationId) wakeFor(selected)
        }
        // Disappearance alone is not proof that a healthy audio/control connection has ended.
    }

    @SuppressLint("MissingPermission")
    private fun wakeFor(association: AssociationInfo) {
        val selected = CompanionRegistration.selectedAssociation(this) ?: return
        if (selected.id != association.id || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED) return
        try {
            startForegroundService(Intent(this, AirPodsService::class.java).apply {
                action = ACTION_PRESENT
                putExtra("companion_address", selected.deviceMacAddress.toString())
            })
        } catch (e: Exception) {
            Log.w("AirPodsCompanion", "Background service start unavailable", e)
        }
    }

    companion object {
        const val ACTION_PRESENT = "me.kavishdevar.librepods.COMPANION_PRESENT"
    }
}
