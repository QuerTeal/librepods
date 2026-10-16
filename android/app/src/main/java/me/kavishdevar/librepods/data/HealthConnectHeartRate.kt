/*
    LibrePods - AirPods liberated from Apple’s ecosystem
    Copyright (C) 2025 LibrePods contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package me.kavishdevar.librepods.data

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Saves AirPods heart rate to Health Connect, where Samsung Health and other apps can read it.
 * Only when the user turned it on and granted the write permission. Samples are batched into
 * one record per minute instead of one per second.
 */
object HealthConnectHeartRate {
    const val ENABLED_KEY = "heart_rate_health_connect"
    val PERMISSION = HealthPermission.getWritePermission(HeartRateRecord::class)
    private const val TAG = "HealthConnectHeartRate"
    private const val BATCH = 60

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = mutableListOf<HeartRateRecord.Sample>()

    fun available(context: Context) =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun granted(context: Context): Boolean = available(context) && runCatching {
        PERMISSION in HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
    }.getOrDefault(false)

    private fun enabled(context: Context) =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean(ENABLED_KEY, false)

    @Volatile private var model: String? = null

    fun add(context: Context, bpm: Int, model: String?, at: Instant = Instant.now()) {
        if (!enabled(context)) return
        this.model = model
        val batch = synchronized(pending) {
            pending += HeartRateRecord.Sample(at, bpm.toLong())
            if (pending.size < BATCH) return
            pending.toList().also { pending.clear() }
        }
        save(context, batch)
    }

    /** Writes what is waiting, as measuring stops or the buds disconnect. */
    fun flush(context: Context) {
        val batch = synchronized(pending) { pending.toList().also { pending.clear() } }
        if (batch.isNotEmpty() && enabled(context)) save(context, batch)
    }

    private fun save(context: Context, samples: List<HeartRateRecord.Sample>) = scope.launch {
        if (!granted(context)) return@launch
        val start = samples.first().time
        // A record must span a moment; a single sample gets one second.
        val end = samples.last().time.let { if (it > start) it else start.plusSeconds(1) }
        val zone = ZoneId.systemDefault().rules
        val record = HeartRateRecord(
            startTime = start,
            startZoneOffset = zone.getOffset(start),
            endTime = end,
            endZoneOffset = zone.getOffset(end),
            samples = samples,
            // Health Connect has no earbud type; head-mounted is the closest.
            metadata = Metadata.autoRecorded(Device(type = Device.TYPE_HEAD_MOUNTED, manufacturer = "Apple", model = model))
        )
        runCatching { HealthConnectClient.getOrCreate(context).insertRecords(listOf(record)) }
            .onSuccess { Log.i(TAG, "Saved ${samples.size} heart rate samples") }
            .onFailure { Log.e(TAG, "Saving heart rate failed", it) }
    }
}
