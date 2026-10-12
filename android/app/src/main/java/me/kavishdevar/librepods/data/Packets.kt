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

import android.os.Parcelable
import android.util.Log
import kotlinx.parcelize.Parcelize

// TODO: Remove everything but Battery-related stuff

enum class Enums(val value: ByteArray) {
    NOISE_CANCELLATION(byteArrayOf(0x0d)),
    PREFIX(byteArrayOf(0x04, 0x00, 0x04, 0x00)),
    SETTINGS(byteArrayOf(0x09, 0x00)),
    NOISE_CANCELLATION_PREFIX(PREFIX.value + SETTINGS.value + NOISE_CANCELLATION.value),
    CONVERSATION_AWARENESS_RECEIVE_PREFIX(PREFIX.value + byteArrayOf(0x4b, 0x00, 0x02, 0x00)),
}

object BatteryComponent {
    const val LEFT = 4
    const val RIGHT = 2
    const val CASE = 8
}

object BatteryStatus {
    const val CHARGING = 1
    const val NOT_CHARGING = 2
    const val DISCONNECTED = 4
    const val OPTIMIZED_CHARGING = 5
}

@Parcelize
data class Battery(val component: Int, val level: Int, val status: Int) : Parcelable {
    fun getComponentName(): String? {
        return when (component) {
            BatteryComponent.LEFT -> "LEFT"
            BatteryComponent.RIGHT -> "RIGHT"
            BatteryComponent.CASE -> "CASE"
            else -> null
        }
    }

    fun getStatusName(): String? {
        return when (status) {
            BatteryStatus.CHARGING -> "CHARGING"
            BatteryStatus.NOT_CHARGING -> "NOT_CHARGING"
            BatteryStatus.DISCONNECTED -> "DISCONNECTED"
            BatteryStatus.OPTIMIZED_CHARGING -> "OPTIMIZED_CHARGING"
            else -> null
        }
    }
}

enum class NoiseControlMode {
    OFF,  NOISE_CANCELLATION, TRANSPARENCY, ADAPTIVE
}

class AirPodsNotifications {
    companion object {
        const val AIRPODS_CONNECTED = "me.kavishdevar.librepods.AIRPODS_CONNECTED"
        const val AIRPODS_L2CAP_CONNECTED = "me.kavishdevar.librepods.AIRPODS_L2CAP_CONNECTED"
        const val AIRPODS_DATA = "me.kavishdevar.librepods.AIRPODS_DATA"
        const val EAR_DETECTION_DATA = "me.kavishdevar.librepods.EAR_DETECTION_DATA"
        const val ANC_DATA = "me.kavishdevar.librepods.ANC_DATA"
        const val BATTERY_DATA = "me.kavishdevar.librepods.BATTERY_DATA"
        const val CA_DATA = "me.kavishdevar.librepods.CA_DATA"
        const val AIRPODS_DISCONNECTED = "me.kavishdevar.librepods.AIRPODS_DISCONNECTED"
        const val AIRPODS_CONNECTION_DETECTED = "me.kavishdevar.librepods.AIRPODS_CONNECTION_DETECTED"
        const val DISCONNECT_RECEIVERS = "me.kavishdevar.librepods.DISCONNECT_RECEIVERS"
        const val EQ_DATA = "me.kavishdevar.librepods.HEADPHONE_ACCOMMODATION"
        const val AIRPODS_INFORMATION_UPDATED = "me.kavishdevar.librepods.AIRPODS_INFORMATION_UPDATED"
    }

    class EarDetection {
        private val notificationBit = 6.toByte()
        private val notificationPrefix = Enums.PREFIX.value + notificationBit

        var status: List<Byte> = listOf(0x01, 0x01)

        fun setStatus(data: ByteArray) {
            status = listOf(data[6], data[7])
        }

        fun isEarDetectionData(data: ByteArray): Boolean {
            if (data.size != 8) {
                return false
            }
            val prefixHex = notificationPrefix.joinToString("") { "%02x".format(it) }
            val dataHex = data.joinToString("") { "%02x".format(it) }
            return dataHex.startsWith(prefixHex)
        }
    }

    class ANC {
        private val notificationPrefix = Enums.NOISE_CANCELLATION_PREFIX.value

        var status: Int = 1
            private set

        fun isANCData(data: ByteArray): Boolean {
            if (data.size != 11) {
                return false
            }
            val prefixHex = notificationPrefix.joinToString("") { "%02x".format(it) }
            val dataHex = data.joinToString("") { "%02x".format(it) }
            return dataHex.startsWith(prefixHex)
        }

        fun setStatus(data: ByteArray) {
            when (data.size) {
                // if the whole packet is given
                11 -> {
                    status = data[7].toInt()
                }
                // if only the data is given
                1 -> {
                    status = data[0].toInt()
                }
                // if the value of control command is given
                4 -> {
                    status = data[0].toInt()
                }
                else -> {
                    Log.d("ANC", "Invalid ANC data size: ${data.size}")
                }
            }
        }

        val name: String =
            when (status) {
                1 -> "OFF"
                2 -> "ON"
                3 -> "TRANSPARENCY"
                4 -> "ADAPTIVE"
                else -> "UNKNOWN"
            }

    }

    class BatteryNotification {
        private var first: Battery = Battery(BatteryComponent.LEFT, 0, BatteryStatus.DISCONNECTED)
        private var second: Battery = Battery(BatteryComponent.RIGHT, 0, BatteryStatus.DISCONNECTED)
        private var case: Battery = Battery(BatteryComponent.CASE, 0, BatteryStatus.DISCONNECTED)
        private val casePresence = mutableMapOf<Int, Boolean>()
        private val pendingCaseZero = mutableMapOf<Int, Long>()

        fun isBatteryData(data: ByteArray): Boolean {
            if (data.joinToString("") { "%02x".format(it) }.startsWith("040004000400")) {
                Log.d("BatteryNotification", "Battery data starts with 040004000400. Most likely is a battery packet.")
            } else {
                return false
            }
            if (data.size != 22) {
                Log.d("BatteryNotification", "Battery data size is not 22, probably being used with Airpods with fewer or more battery count.")
                return false
            }
            Log.d("BatteryNotification", data.joinToString("") { "%02x".format(it) }.startsWith("040004000400").toString())
            return data.joinToString("") { "%02x".format(it) }.startsWith("040004000400")
        }

        fun setBatteryDirect(
            leftLevel: Int?,
            leftCharging: Boolean,
            rightLevel: Int?,
            rightCharging: Boolean,
            caseLevel: Int?,
            caseCharging: Boolean
        ) {
            fun reading(component: Int, level: Int?, charging: Boolean): Battery =
                Battery(component, level ?: 0, if (level == null) BatteryStatus.DISCONNECTED
                    else if (charging) BatteryStatus.CHARGING else BatteryStatus.NOT_CHARGING)
            first = reading(BatteryComponent.LEFT, leftLevel, leftCharging)
            second = reading(BatteryComponent.RIGHT, rightLevel, rightCharging)
            case = reading(BatteryComponent.CASE, caseLevel, caseCharging)
        }

        /** Merge a verified case advertiser without replacing live AACP earbud readings. */
        fun updateCaseBattery(level: Int?, charging: Boolean): Boolean {
            if (level == null || level !in 0..100) return false
            val next = Battery(BatteryComponent.CASE, level,
                if (charging) BatteryStatus.CHARGING else BatteryStatus.NOT_CHARGING)
            if (next == case) return false
            case = next
            return true
        }

        /** A missing bud slot means out of the case, not an empty/disconnected battery. */
        fun updateFromCase(
            leftLevel: Int?, leftCharging: Boolean,
            rightLevel: Int?, rightCharging: Boolean,
            caseLevel: Int?, caseCharging: Boolean,
            seenMillis: Long? = null
        ): Boolean {
            if (listOf(leftLevel, rightLevel, caseLevel).any { it != null && it !in 0..100 }) return false
            val previous = getBattery()
            fun bud(current: Battery, level: Int?, charging: Boolean): Battery {
                val wasPresent = casePresence[current.component]
                casePresence[current.component] = level != null
                if (level != 0 || !charging) pendingCaseZero.remove(current.component)
                // The tested case reports 0 for about two seconds just after reseating, before
                // its actual level arrives. Confirm only this implausible drop from a known level;
                // unknown/previously empty batteries still report real zero immediately.
                if (seenMillis != null && level == 0 && charging && current.hasReportedLevel() &&
                    current.level > 0 && (wasPresent != true || pendingCaseZero.containsKey(current.component))) {
                    val began = pendingCaseZero.getOrPut(current.component) { seenMillis }
                    if (seenMillis - began < 3000L) return current.copy(status = BatteryStatus.CHARGING)
                    pendingCaseZero.remove(current.component)
                }
                return if (level != null) Battery(current.component, level,
                    if (charging) BatteryStatus.CHARGING else BatteryStatus.NOT_CHARGING)
                else if (current.hasReportedLevel()) current.copy(status = BatteryStatus.NOT_CHARGING)
                else current
            }
            first = bud(previous[0], leftLevel, leftCharging)
            second = bud(previous[1], rightLevel, rightCharging)
            updateCaseBattery(caseLevel, caseCharging)
            return previous != getBattery()
        }

        fun reset() {
            first = Battery(BatteryComponent.LEFT, 0, BatteryStatus.DISCONNECTED)
            second = Battery(BatteryComponent.RIGHT, 0, BatteryStatus.DISCONNECTED)
            case = Battery(BatteryComponent.CASE, 0, BatteryStatus.DISCONNECTED)
            casePresence.clear()
            pendingCaseZero.clear()
        }

        fun setBattery(data: ByteArray) {
            if (data.size != 22) {
                return
            }
//            first = if (data[10].toInt() == BatteryStatus.DISCONNECTED) {
//                Battery(first.component, first.level, data[10].toInt())
//            } else {
//                Battery(data[7].toInt(), data[9].toInt(), data[10].toInt())
//            }
//            second = if (data[15].toInt() == BatteryStatus.DISCONNECTED) {
//                Battery(second.component, second.level, data[15].toInt())
//            } else {
//                Battery(data[12].toInt(), data[14].toInt(), data[15].toInt())
//            }
//            case = if (data[20].toInt() == BatteryStatus.DISCONNECTED && case.status != BatteryStatus.DISCONNECTED) {
//                Battery(case.component, case.level, data[20].toInt())
//            } else {
//                Battery(data[17].toInt(), data[19].toInt(), data[20].toInt())
//            }
//            sometimes it shows battery as -1%, just skip all that and set it normally
            first = Battery(
                data[7].toInt(), data[9].toInt(), data[10].toInt()
            )
            second = Battery(
                data[12].toInt(), data[14].toInt(), data[15].toInt()
            )
            case = Battery(
                data[17].toInt(), data[19].toInt(), data[20].toInt()
            )
        }

        fun getBattery(): List<Battery> {
            val left = if (first.component == BatteryComponent.LEFT) first else second
            val right = if (first.component == BatteryComponent.LEFT) second else first
            return listOf(left, right, case)
        }
    }

    class ConversationalAwarenessNotification {
        @Suppress("PrivatePropertyName")
        private val NOTIFICATION_PREFIX = Enums.CONVERSATION_AWARENESS_RECEIVE_PREFIX.value

        var status: Byte = 0
            private set

        fun isConversationalAwarenessData(data: ByteArray): Boolean {
            if (data.size != 10) {
                return false
            }
            val prefixHex = NOTIFICATION_PREFIX.joinToString("") { "%02x".format(it) }
            val dataHex = data.joinToString("") { "%02x".format(it) }
            return dataHex.startsWith(prefixHex)
        }

        fun setData(data: ByteArray) {
            status = data[9]
        }
    }
}

fun isHeadTrackingData(data: ByteArray): Boolean {
    if (data.size <= 60) return false

    val prefixPattern = byteArrayOf(
        0x04, 0x00, 0x04, 0x00, 0x17, 0x00, 0x00, 0x00,
        0x10, 0x00
    )

    for (i in prefixPattern.indices) {
        if (data[i] != prefixPattern[i]) return false
    }

    if (data[10] != 0x44.toByte() && data[10] != 0x45.toByte()) return false

    if (data[11] != 0x00.toByte()) return false

    return true
}
