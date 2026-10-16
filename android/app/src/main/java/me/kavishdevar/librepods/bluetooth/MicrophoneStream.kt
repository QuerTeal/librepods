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
 * The AirPods' own microphone over AACP (opcode 0x58), outside the Bluetooth call profile.
 * The buds send AAC-ELD access units (64 kHz mono, 7.5 ms each) while the stream is open. Packet layouts
 * follow upstream's rewrite.
 */
object MicrophoneStream {
    const val OPCODE: Byte = 0x58

    /** Opens the stream; opcode and payload, ready for [AACPManager.sendDataPacket]. */
    fun startPacket(): ByteArray = byteArrayOf(
        OPCODE, 0x00,
        0x00, 0x00, 0x09, 0x00,
        0x00, 0x01, 0x82.toByte(), 0x00, 0x00, 0x00, 0x04, 0x96.toByte(), 0x00
    )

    fun stopPacket(): ByteArray = byteArrayOf(OPCODE, 0x00, 0x00, 0x00, 0x02, 0x00, 0x03, 0x01)

    /**
     * The audio access units in a received stream packet: after a 22-byte header, each is a
     * 4-byte timestamp, a 1-byte length and the data. Other 0x58 packets (status) carry none.
     */
    fun frames(packet: ByteArray): List<ByteArray> {
        if (packet.size < 22 || packet[4] != OPCODE || packet[6] != 0x01.toByte() || packet[7] != 0x00.toByte())
            return emptyList()
        val frames = mutableListOf<ByteArray>()
        var offset = 22
        while (offset + 5 <= packet.size) {
            val start = offset + 5
            val end = start + (packet[offset + 4].toInt() and 0xFF)
            if (end > packet.size) break
            frames += packet.copyOfRange(start, end)
            offset = end
        }
        return frames
    }
}
