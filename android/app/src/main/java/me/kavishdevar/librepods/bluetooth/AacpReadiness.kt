package me.kavishdevar.librepods.bluetooth

/** Initial status must have the AACP header and a complete supported notification body. */
fun isInitialControlStatus(packet: ByteArray): Boolean {
    if (packet.size < 6 || packet[0] != 4.toByte() || packet[1] != 0.toByte() ||
        packet[2] != 4.toByte() || packet[3] != 0.toByte() || packet[5] != 0.toByte()) return false
    return when (packet[4].toInt() and 0xff) {
        0x04 -> packet.size == 22 // battery
        0x06 -> packet.size == 8 // ear detection
        0x09 -> packet.size >= 11 &&
            AACPManager.Companion.ControlCommandIdentifiers.fromByte(packet[6]) != null
        else -> false
    }
}
