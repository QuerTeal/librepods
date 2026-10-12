package me.kavishdevar.librepods.bluetooth

import org.junit.Assert.*
import org.junit.Test

class AacpReadinessTest {
    private fun packet(opcode: Int, size: Int) = ByteArray(size).apply {
        byteArrayOf(4, 0, 4, 0, opcode.toByte(), 0).copyInto(this)
        if (size > 6 && opcode == 9) this[6] = 0x0d // listening mode
    }

    @Test fun completeStatusNotificationsCanEstablishReadiness() {
        assertTrue(isInitialControlStatus(packet(4, 22)))
        assertTrue(isInitialControlStatus(packet(6, 8)))
        assertTrue(isInitialControlStatus(packet(9, 11)))
    }

    @Test fun malformedAndTruncatedPacketsCannotEstablishReadiness() {
        assertFalse(isInitialControlStatus(byteArrayOf(4)))
        assertFalse(isInitialControlStatus(packet(4, 21)))
        assertFalse(isInitialControlStatus(packet(6, 7)))
        assertFalse(isInitialControlStatus(packet(9, 10)))
        assertFalse(isInitialControlStatus(packet(4, 22).apply { this[0] = 0 }))
        assertFalse(isInitialControlStatus(packet(9, 11).apply { this[6] = 0x7f }))
    }

    @Test fun unrelatedOrUnknownPacketsDoNotActivateControls() {
        assertFalse(isInitialControlStatus(packet(0x17, 22)))
        assertFalse(isInitialControlStatus(packet(0x7f, 22)))
    }
}
