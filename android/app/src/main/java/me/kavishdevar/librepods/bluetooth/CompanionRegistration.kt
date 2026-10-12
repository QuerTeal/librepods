package me.kavishdevar.librepods.bluetooth

import android.annotation.SuppressLint
import android.companion.AssociationInfo
import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

object CompanionRegistration {
    fun selectedAssociation(context: Context): AssociationInfo? {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)) return null
        val address = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("mac_address", null)
            ?: return null
        return try {
            context.getSystemService(CompanionDeviceManager::class.java)?.myAssociations?.firstOrNull {
                it.deviceMacAddress?.toString()?.equals(address, ignoreCase = true) == true &&
                    (Build.VERSION.SDK_INT < 35 || !it.isSelfManaged)
            }
        } catch (e: Exception) {
            Log.w("CompanionRegistration", "Unable to read registered device", e)
            null
        }
    }

    @SuppressLint("MissingPermission") // Companion permissions are declared; association is checked above.
    @Suppress("DEPRECATION")
    fun observeSelected(context: Context): Boolean {
        val association = selectedAssociation(context) ?: return false
        val manager = context.getSystemService(CompanionDeviceManager::class.java) ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= 36) {
                manager.startObservingDevicePresence(ObservingDevicePresenceRequest.Builder()
                    .setAssociationId(association.id).build())
            } else {
                manager.startObservingDevicePresence(association.deviceMacAddress.toString())
            }
            true
        } catch (e: Exception) {
            Log.w("CompanionRegistration", "Presence observation unavailable", e)
            false
        }
    }
}
