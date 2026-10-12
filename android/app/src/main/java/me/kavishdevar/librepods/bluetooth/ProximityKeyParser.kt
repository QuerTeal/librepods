package me.kavishdevar.librepods.bluetooth

/** Validated TLV parsing; malformed input never yields a partially trusted identity. */
object ProximityKeyParser {
    fun parse(packet: ByteArray): Map<Byte, ByteArray> {
        if (packet.size < 7 || !packet.take(6).toByteArray().contentEquals(
                byteArrayOf(4, 0, 4, 0, 0x31, 0))) return emptyMap()
        val keys = mutableMapOf<Byte, ByteArray>()
        var offset = 7
        repeat(packet[6].toInt() and 0xff) {
            if (offset + 4 > packet.size) return emptyMap()
            val type = packet[offset]
            val length = packet[offset + 2].toInt() and 0xff
            offset += 4
            if (length == 0 || offset + length > packet.size) return emptyMap()
            if (type == 1.toByte() || type == 4.toByte()) {
                if (length != 16 || keys.containsKey(type)) return emptyMap()
                keys[type] = packet.copyOfRange(offset, offset + length)
            }
            offset += length
        }
        return if (offset == packet.size) keys else emptyMap()
    }
}
