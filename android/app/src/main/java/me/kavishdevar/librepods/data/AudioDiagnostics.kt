package me.kavishdevar.librepods.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.provider.Settings
import me.kavishdevar.librepods.bluetooth.CompanionRegistration

data class AudioDiagnostics(
    val bluetoothPermission: Boolean,
    val overlayPermission: Boolean,
    val companionAssociated: Boolean,
    val selectedMusicOutputAvailable: Boolean?,
    val selectedCommunicationOutput: Boolean?,
    val systemMusicActive: Boolean,
    val communicationMode: Boolean,
    val codec: String? = null
)

/** Public, read-only diagnostics. Available outputs do not establish the active music route. */
object AudioDiagnosticsReader {
    fun read(context: Context, selectedAddress: String): AudioDiagnostics {
        val audio = context.getSystemService(AudioManager::class.java)
        val permission = context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        val outputs = runCatching { audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList() }.getOrNull()
        val bluetoothOutputs = outputs?.filter { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            it.type == AudioDeviceInfo.TYPE_BLE_HEADSET }
        val communication = runCatching { audio.communicationDevice }.getOrNull()
        val musicAvailable = when {
            selectedAddress.isBlank() || !permission || bluetoothOutputs == null -> null
            bluetoothOutputs.any { it.address.equals(selectedAddress, ignoreCase = true) } -> true
            bluetoothOutputs.any { it.address.isBlank() } -> null
            else -> false
        }
        val selectedCommunication = when {
            communication == null -> false
            selectedAddress.isBlank() || !permission -> null
            communication.type !in setOf(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET) -> false
            communication.address.isBlank() -> null
            else -> communication.address.equals(selectedAddress, ignoreCase = true)
        }
        return AudioDiagnostics(permission, Settings.canDrawOverlays(context),
            CompanionRegistration.selectedAssociation(context) != null, musicAvailable,
            selectedCommunication, audio.isMusicActive,
            audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION)
    }
}
