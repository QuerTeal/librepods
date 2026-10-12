package me.kavishdevar.librepods.bluetooth

/** Retain only non-personal battery/wear/control diagnostics; other payloads stay private. */
object PacketDiagnostics {
    fun format(packet: ByteArray): String {
        val opcode = packet.getOrNull(4)?.toInt()?.and(0xff)
        val safe = opcode in setOf(0x04, 0x06, 0x09, 0x19, 0x4b, 0x63) && packet.size <= 32
        return if (safe) packet.joinToString(" ") { "%02X".format(it) }
        else "opcode=${opcode?.toString(16) ?: "unknown"} bytes=${packet.size} [redacted]"
    }
}
