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

package me.kavishdevar.librepods.bluetooth

/**
 * The AirPods sensor channel: AACP opcode 0x17 carrying an RTBuddy frame whose descriptor is
 * SensorDataWX (0x00100000) and whose body is a small protobuf message.
 *
 * Head tracking and heart rate both ride on it, so only the few fields they need are encoded here
 * instead of pulling in a protobuf runtime. Field numbers follow upstream's rtbuddy.proto.
 */
object SensorDataWx {
    const val ACTIVITY = 14
    const val DEVMOTION6 = 16
    const val HEART_RATE = 19
    const val HEART_RATE_V2 = 20
    const val HEART_RATE_COMMAND = 84

    private const val OPCODE: Byte = 0x17
    private const val DESCRIPTOR = 0x00100000
    private const val HEADER_SIZE = 6 // AACP header (04 00 04 00) + opcode (17 00)
    private const val FRAME_HEADER_SIZE = 6 // descriptor (u32 LE) + length (u16 LE)

    private val heartRateServices = setOf(HEART_RATE, HEART_RATE_V2, HEART_RATE_COMMAND)

    /** A sensor sample: the service that produced it and its raw payload. */
    class Command(val service: Int, val payload: ByteArray)

    /**
     * Asks the buds to report [service] every [intervalMicros] µs; 0 stops it. Returns opcode and
     * frame, ready for [AACPManager.sendDataPacket].
     */
    fun reportIntervalPacket(seq: Int, service: Int, intervalMicros: Long): ByteArray {
        val configuration = byteArrayOf(0x01) + intervalMicros.toUInt32LE()
        val setting = varintField(1, service.toLong()) + varintField(2, 2) + bytesField(3, configuration)
        val message = varintField(1, seq.toLong()) + bytesField(8, setting)
        return byteArrayOf(OPCODE, 0x00) + DESCRIPTOR.toUInt32LE() +
            byteArrayOf((message.size and 0xFF).toByte(), (message.size shr 8 and 0xFF).toByte()) + message
    }

    /** Whether a received AACP packet is a SensorDataWX frame (a sample, or an acknowledgement). */
    fun isSensorFrame(packet: ByteArray): Boolean =
        packet.size >= HEADER_SIZE + FRAME_HEADER_SIZE && packet[4] == OPCODE &&
            packet.readUInt32LE(HEADER_SIZE) == DESCRIPTOR.toLong()

    /** The sensor sample in a received AACP packet, or null if it isn't one. */
    fun parseCommand(packet: ByteArray): Command? {
        if (!isSensorFrame(packet)) return null
        val length = (packet[HEADER_SIZE + 4].toInt() and 0xFF) or ((packet[HEADER_SIZE + 5].toInt() and 0xFF) shl 8)
        val start = HEADER_SIZE + FRAME_HEADER_SIZE
        if (packet.size < start + length) return null
        val command = fields(packet, start, start + length)?.get(7) as? ByteArray ?: return null
        val inner = fields(command, 0, command.size) ?: return null
        val service = (inner[1] as? Long)?.toInt() ?: return null
        return Command(service, inner[3] as? ByteArray ?: ByteArray(0))
    }

    fun isHeartRate(command: Command) = command.service in heartRateServices

    /**
     * Beats per minute from a heart rate payload: 18 bytes, the rate in byte 1 (unsigned), and a
     * nonzero last byte when the sample isn't valid (buds not worn, no lock yet).
     */
    fun heartRate(payload: ByteArray): Int? {
        if (payload.size != 18 || payload[17].toInt() != 0) return null
        return (payload[1].toInt() and 0xFF).takeIf { it in 1..250 }
    }

    /** Firmware 9 and later turns heart rate on through its command service. */
    fun heartRateService(firmware: String): Int =
        if ((firmware.firstOrNull()?.digitToIntOrNull() ?: 8) >= 9) HEART_RATE_COMMAND else HEART_RATE

    /** Top-level fields of a protobuf message: varints as Long, length-delimited as ByteArray. */
    private fun fields(data: ByteArray, from: Int, to: Int): Map<Int, Any>? {
        val result = mutableMapOf<Int, Any>()
        var i = from
        fun varint(): Long? {
            var value = 0L
            var shift = 0
            while (i < to && shift < 64) {
                val b = data[i++].toInt() and 0xFF
                value = value or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return value
                shift += 7
            }
            return null
        }
        while (i < to) {
            val key = varint() ?: return null
            val field = (key shr 3).toInt()
            when ((key and 7).toInt()) {
                0 -> result[field] = varint() ?: return null
                1 -> i += 8
                2 -> {
                    val size = varint()?.toInt() ?: return null
                    if (size < 0 || i + size > to) return null
                    result[field] = data.copyOfRange(i, i + size)
                    i += size
                }
                5 -> i += 4
                else -> return null
            }
        }
        return if (i == to) result else null
    }

    private fun varint(value: Long): ByteArray {
        val out = mutableListOf<Byte>()
        var v = value
        do {
            var b = (v and 0x7F).toInt()
            v = v ushr 7
            if (v != 0L) b = b or 0x80
            out += b.toByte()
        } while (v != 0L)
        return out.toByteArray()
    }

    private fun varintField(field: Int, value: Long) = varint((field shl 3).toLong()) + varint(value)

    private fun bytesField(field: Int, value: ByteArray) =
        varint((field shl 3 or 2).toLong()) + varint(value.size.toLong()) + value

    private fun Int.toUInt32LE() = toLong().toUInt32LE()

    private fun Long.toUInt32LE() = ByteArray(4) { (this shr (8 * it) and 0xFF).toByte() }

    private fun ByteArray.readUInt32LE(at: Int): Long =
        (0 until 4).fold(0L) { acc, k -> acc or ((this[at + k].toLong() and 0xFF) shl (8 * k)) }
}
