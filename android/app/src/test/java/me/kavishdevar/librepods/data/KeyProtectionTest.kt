package me.kavishdevar.librepods.data

import javax.crypto.KeyGenerator
import me.kavishdevar.librepods.bluetooth.PacketDiagnostics
import me.kavishdevar.librepods.bluetooth.ProximityKeyParser
import org.junit.Assert.*
import org.junit.Test

class KeyProtectionTest {
    private fun packet(vararg types: Int): ByteArray = byteArrayOf(4, 0, 4, 0, 0x31, 0, types.size.toByte()) +
        types.flatMap { listOf(it.toByte(), 0, 16, 0).map { n -> n.toByte() } + List(16) { it.toByte() } }.toByteArray()

    @Test fun parserAcceptsCompleteKeysAndCopiesPayload() {
        val bytes = packet(1, 4)
        val keys = ProximityKeyParser.parse(bytes)
        assertEquals(setOf<Byte>(1, 4), keys.keys)
        bytes.fill(0)
        assertEquals(1.toByte(), keys.getValue(1)[1])
    }
    @Test fun parserRejectsTruncationAtEveryBoundary() {
        val bytes = packet(1, 4)
        for (length in 0 until bytes.size) assertTrue(ProximityKeyParser.parse(bytes.copyOf(length)).isEmpty())
    }
    @Test fun parserRejectsDuplicateInvalidLengthAndTrailingData() {
        assertTrue(ProximityKeyParser.parse(packet(1, 1)).isEmpty())
        assertTrue(ProximityKeyParser.parse(packet(1).also { it[9] = 15 }).isEmpty())
        assertTrue(ProximityKeyParser.parse(packet(1) + byteArrayOf(0)).isEmpty())
    }
    @Test fun envelopeUsesFreshNonceAndRoundTrips() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val plain = ByteArray(16) { it.toByte() }
        val first = KeyEnvelope.encrypt(key, "device:IRK", plain)
        val second = KeyEnvelope.encrypt(key, "device:IRK", plain)
        assertFalse(first.contentEquals(second))
        assertArrayEquals(plain, KeyEnvelope.decrypt(key, "device:IRK", first))
    }
    @Test fun envelopeRejectsOtherDeviceKindTamperingAndVersion() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val bytes = KeyEnvelope.encrypt(key, "device:IRK", ByteArray(16))
        assertTrue(runCatching { KeyEnvelope.decrypt(key, "other:IRK", bytes) }.isFailure)
        assertTrue(runCatching { KeyEnvelope.decrypt(key, "device:ENC_KEY", bytes) }.isFailure)
        assertTrue(runCatching { KeyEnvelope.decrypt(key, "device:IRK", bytes.copyOf().also { it[20]++ }) }.isFailure)
        assertTrue(runCatching { KeyEnvelope.decrypt(key, "device:IRK", bytes.copyOf().also { it[0] = 2 }) }.isFailure)
    }
    @Test fun diagnosticsRedactKeysMetadataRoutingAndUnknownPayloads() {
        for (opcode in listOf(0x31, 0x1d, 0x1a, 0x2e, 0x0e, 0x53, 0xff)) {
            val formatted = PacketDiagnostics.format(byteArrayOf(4, 0, 4, 0, opcode.toByte(), 0, 0x5a))
            assertTrue(formatted.endsWith("[redacted]"))
            assertFalse(formatted.contains("5A"))
        }
        assertEquals("04 00 04 00 06 00 00 01", PacketDiagnostics.format(byteArrayOf(4, 0, 4, 0, 6, 0, 0, 1)))
    }
    @Test fun hearingAndTransparencyRejectTruncatedAndNonFiniteReadbacks() {
        for (size in 0..99) assertNull(parseTransparencySettingsResponse(ByteArray(size)))
        assertNull(parseTransparencySettingsResponse(ByteArray(103)))
        val nonfinite = java.nio.ByteBuffer.allocate(104).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .putFloat(4, Float.NaN).array()
        assertNull(parseTransparencySettingsResponse(nonfinite))
        assertNull(parseHearingAidSettingsResponse(nonfinite))
    }
}
