package me.kavishdevar.librepods.bluetooth

import org.junit.Assert.*
import org.junit.Test

class SensorDataWxTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    // The head tracking packets AACPManager already sends, which the buds are known to accept.
    @Test fun encodesTheKnownHeadTrackingPackets() {
        assertArrayEquals(
            bytes(0x17, 0, 0, 0, 0x10, 0, 0x10, 0, 0x08, 0xA1, 0x02, 0x42, 0x0B, 0x08, 0x0E, 0x10, 0x02, 0x1A, 0x05,
                0x01, 0x40, 0x9C, 0, 0),
            SensorDataWx.reportIntervalPacket(seq = 289, service = SensorDataWx.ACTIVITY, intervalMicros = 40_000)
        )
        assertArrayEquals(
            bytes(0x17, 0, 0, 0, 0x10, 0, 0x0F, 0, 0x08, 0x73, 0x42, 0x0B, 0x08, 0x10, 0x10, 0x02, 0x1A, 0x05,
                0x01, 0x40, 0x9C, 0, 0),
            SensorDataWx.reportIntervalPacket(seq = 0x73, service = SensorDataWx.DEVMOTION6, intervalMicros = 40_000)
        )
        assertArrayEquals(
            bytes(0x17, 0, 0, 0, 0x10, 0, 0x0F, 0, 0x08, 0x75, 0x42, 0x0B, 0x08, 0x10, 0x10, 0x02, 0x1A, 0x05,
                0x01, 0, 0, 0, 0),
            SensorDataWx.reportIntervalPacket(seq = 0x75, service = SensorDataWx.DEVMOTION6, intervalMicros = 0)
        )
    }

    @Test fun heartRateOncePerSecondUsesTheCommandService() {
        val packet = SensorDataWx.reportIntervalPacket(seq = 5, service = SensorDataWx.HEART_RATE_COMMAND, intervalMicros = 1_000_000)
        // service 84, setting 2, configuration 01 + 1,000,000 µs (0x000F4240) little-endian
        assertArrayEquals(bytes(0x42, 0x0B, 0x08, 0x54, 0x10, 0x02, 0x1A, 0x05, 0x01, 0x40, 0x42, 0x0F, 0x00),
            packet.copyOfRange(packet.size - 13, packet.size))
    }

    private fun received(service: Int, payload: ByteArray): ByteArray {
        val command = bytes(0x08, service) + bytes(0x1A, payload.size) + payload
        val message = bytes(0x08, 0x07) + bytes(0x3A, command.size) + command
        return bytes(0x04, 0, 0x04, 0, 0x17, 0, 0, 0, 0x10, 0, message.size, 0) + message
    }

    private fun heartRatePayload(bpm: Int, valid: Boolean = true) = ByteArray(18).apply {
        this[1] = bpm.toByte()
        this[17] = if (valid) 0 else 1
    }

    @Test fun readsAHeartRateSample() {
        val command = SensorDataWx.parseCommand(received(SensorDataWx.HEART_RATE_V2, heartRatePayload(72)))!!
        assertTrue(SensorDataWx.isHeartRate(command))
        assertEquals(72, SensorDataWx.heartRate(command.payload))
    }

    @Test fun ratesAbove127AreUnsigned() {
        val command = SensorDataWx.parseCommand(received(SensorDataWx.HEART_RATE, heartRatePayload(168)))!!
        assertEquals(168, SensorDataWx.heartRate(command.payload))
    }

    @Test fun skipsInvalidSamples() {
        assertNull(SensorDataWx.heartRate(heartRatePayload(72, valid = false)))
        assertNull(SensorDataWx.heartRate(heartRatePayload(0)))
        assertNull(SensorDataWx.heartRate(ByteArray(17)))
    }

    @Test fun headTrackingIsNotHeartRate() {
        val command = SensorDataWx.parseCommand(received(SensorDataWx.DEVMOTION6, ByteArray(58)))!!
        assertFalse(SensorDataWx.isHeartRate(command))
    }

    @Test fun ignoresOtherAndMalformedPackets() {
        assertNull(SensorDataWx.parseCommand(bytes(0x04, 0, 0x04, 0, 0x09, 0, 0x0D, 0x03)))
        val good = received(SensorDataWx.HEART_RATE, heartRatePayload(60))
        assertNull(SensorDataWx.parseCommand(good.copyOf(good.size - 3)))
        assertNull(SensorDataWx.parseCommand(good.copyOf().apply { this[8] = 0x20 }))
    }

    @Test fun picksTheServiceByFirmware() {
        assertEquals(SensorDataWx.HEART_RATE_COMMAND, SensorDataWx.heartRateService("9442752"))
        assertEquals(SensorDataWx.HEART_RATE, SensorDataWx.heartRateService("8A358"))
        assertEquals(SensorDataWx.HEART_RATE, SensorDataWx.heartRateService(""))
    }
}
