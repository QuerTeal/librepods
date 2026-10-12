package me.kavishdevar.librepods.data

import me.kavishdevar.librepods.bluetooth.AACPManager

enum class CustomEqBand { LOW, MID, HIGH }

data class CustomEq(val state: Int, val low: Int, val mid: Int, val high: Int) {
    companion object {
        fun fromPacket(packet: ByteArray): CustomEq? {
            if (packet.size != 13 || !packet.copyOfRange(0, 6).contentEquals(
                    byteArrayOf(4, 0, 4, 0, 0x63, 0))) return null
            if (packet[6] != 5.toByte() || packet[7] != 0.toByte() || packet[8] != 1.toByte()) return null
            val values = packet.copyOfRange(9, 13).map { it.toInt() and 0xFF }
            if (values[0] !in 1..2 || values.drop(1).any { it !in 0..100 }) return null
            return CustomEq(values[0], values[1], values[2], values[3])
        }
    }

    fun isEnabled(): Boolean {
        return state == 2
    }

    fun toPacket(): ByteArray {
        return byteArrayOf(
            AACPManager.Companion.Opcodes.CUSTOM_EQ, 0x00,
            0x05, 0x00, // length (LE)
            0x01, state.toByte(),
            low.toByte(), mid.toByte(), high.toByte()
        )
    }

    init {
        require(state in 1..2) { "state must be disabled (1) or enabled (2)" }
        require(low in 0..100) { "low must be between 0 and 100, was $low" }
        require(mid in 0..100) { "mid must be between 0 and 100, was $mid" }
        require(high in 0..100) { "high must be between 0 and 100, was $high" }
    }
}
