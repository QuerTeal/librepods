package me.kavishdevar.librepods

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import me.kavishdevar.librepods.bluetooth.CompanionRegistration
import me.kavishdevar.librepods.services.AirPodsService

/** Shows Android's consent UI only when the user taps device registration in settings. */
class CompanionRegistrationActivity : ComponentActivity() {
    private val chooser = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) finish()
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        // Management is a full app page; only the Android registration chooser uses the transparent host.
        if (CompanionRegistration.selectedAssociation(this) != null) setTheme(R.style.Theme_LibrePods)
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            // A callback from a dead Activity is not reusable; never launch a duplicate chooser.
            if (CompanionRegistration.selectedAssociation(this) != null) showRegistrationManagement() else finish()
            return
        }
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)) {
            complete(R.string.companion_unavailable)
            return
        }
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            complete(R.string.companion_pair_first)
            return
        }
        val address = getSharedPreferences("settings", MODE_PRIVATE).getString("mac_address", null)
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            complete(R.string.companion_pair_first)
            return
        }
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        val manager = getSystemService(CompanionDeviceManager::class.java)
        if (adapter == null || manager == null) {
            complete(R.string.companion_unavailable)
            return
        }
        try {
            if (!adapter.isEnabled || adapter.bondedDevices.none { it.address == address }) {
                complete(R.string.companion_pair_first)
                return
            }
            if (CompanionRegistration.selectedAssociation(this) != null) {
                showRegistrationManagement()
                return
            }
            val request = AssociationRequest.Builder()
                .addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(address).build())
                .setSingleDevice(true)
                .build()
            manager.associate(request, mainExecutor, object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(intentSender: IntentSender) {
                    if (isFinishing || isDestroyed) return
                    try { chooser.launch(IntentSenderRequest.Builder(intentSender).build()) }
                    catch (_: Exception) { complete(R.string.companion_registration_failed) }
                }

                override fun onAssociationCreated(associationInfo: AssociationInfo) {
                    if (associationInfo.deviceMacAddress?.toString()?.equals(address, ignoreCase = true) != true) {
                        complete(R.string.companion_registration_failed)
                        return
                    }
                    enablePresence()
                }

                override fun onFailure(error: CharSequence?) {
                    complete(R.string.companion_registration_failed)
                }
            })
        } catch (_: Exception) { complete(R.string.companion_registration_failed) }
    }

    private fun showRegistrationManagement() {
        enableEdgeToEdge()
        val design = me.kavishdevar.librepods.presentation.theme.AppDesign.fromPreferences(
            getSharedPreferences("settings", MODE_PRIVATE))
        setContent {
            me.kavishdevar.librepods.presentation.theme.LibrePodsTheme(design = design) {
                me.kavishdevar.librepods.presentation.screens.CompanionManagementContent(
                    onEnable = ::enablePresence, onRemove = ::removeRegistration, onBack = ::finish)
            }
        }
    }

    private fun removeRegistration() {
        val association = CompanionRegistration.selectedAssociation(this)
        try {
            if (association != null) getSystemService(CompanionDeviceManager::class.java).disassociate(association.id)
            complete(R.string.companion_removed)
        } catch (_: Exception) { complete(R.string.companion_registration_failed) }
    }

    private fun enablePresence() {
        val observing = CompanionRegistration.observeSelected(this)
        getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("companion_observation_failed", !observing).apply()
        if (!observing) {
            complete(R.string.companion_registration_failed)
            return
        }
        // We are foreground here; later restarts come from the companion service's presence event.
        try { startForegroundService(Intent(this, AirPodsService::class.java)) } catch (_: Exception) { }
        complete(R.string.companion_registered)
    }

    private fun complete(message: Int) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        finish()
    }
}
