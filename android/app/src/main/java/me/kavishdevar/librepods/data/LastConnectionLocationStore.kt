package me.kavishdevar.librepods.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.SystemClock
import android.util.AtomicFile
import androidx.core.content.edit
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject

/** Optional local history of the phone location, never a live AirPods location. No network or backup. */
object LastConnectionLocationStore {
    private var phoneFix: PhoneLocationFix? = null
    private fun file(context: Context) = File(context.noBackupFilesDir, "last_airpods_connection.json")
    fun enabled(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        .getBoolean("last_connection_location_enabled", false)
    @Synchronized fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit {
            putBoolean("last_connection_location_enabled", enabled)
        }
        if (!enabled) { phoneFix = null; AtomicFile(file(context)).delete() }
    }

    /** Called only by a visible activity; no background-location permission or background request. */
    @SuppressLint("MissingPermission")
    @Synchronized fun refreshPhoneFix(context: Context) {
        if (!enabled(context) || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED) { phoneFix = null; return }
        val manager = context.getSystemService(LocationManager::class.java)
        phoneFix = runCatching {
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
                .maxByOrNull { it.elapsedRealtimeNanos }?.let { location ->
                    PhoneLocationFix(location.latitude, location.longitude, location.accuracy,
                        location.time, location.elapsedRealtimeNanos / 1_000_000L)
                        .takeIf { location.hasAccuracy() && it.isUsable(SystemClock.elapsedRealtime()) }
                }
        }.getOrNull()
    }

    @Synchronized fun recordConnection(context: Context, address: String) {
        if (!enabled(context) || address.isBlank()) return
        val selected = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("mac_address", "")
        if (!address.equals(selected, ignoreCase = true)) return
        val fix = phoneFix?.takeIf { it.isUsable(SystemClock.elapsedRealtime()) }
        val record = JSONObject().apply {
            put("device", id(address)); put("connectedAt", System.currentTimeMillis())
            fix?.let {
                put("latitude", it.latitude); put("longitude", it.longitude)
                put("accuracy", it.accuracyMeters); put("measuredAt", it.measuredAtMillis)
            }
        }
        val atomic = AtomicFile(file(context))
        var output: java.io.FileOutputStream? = null
        try {
            output = atomic.startWrite()
            output.write(record.toString().toByteArray(Charsets.UTF_8))
            atomic.finishWrite(output)
        } catch (_: Exception) { output?.let { atomic.failWrite(it) } }
    }

    @Synchronized fun read(context: Context, selectedAddress: String): LastConnectionLocation? {
        if (!enabled(context) || selectedAddress.isBlank()) return null
        return runCatching {
            val json = JSONObject(String(AtomicFile(file(context)).readFully(), Charsets.UTF_8))
            if (json.getString("device") != id(selectedAddress)) return null
            val fix = if (json.has("latitude")) PhoneLocationFix(json.getDouble("latitude"),
                json.getDouble("longitude"), json.getDouble("accuracy").toFloat(),
                json.getLong("measuredAt"), 0) else null
            LastConnectionLocation(json.getString("device"), json.getLong("connectedAt"), fix)
        }.getOrNull()
    }

    private fun id(address: String) = MessageDigest.getInstance("SHA-256")
        .digest(address.uppercase(java.util.Locale.ROOT).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
