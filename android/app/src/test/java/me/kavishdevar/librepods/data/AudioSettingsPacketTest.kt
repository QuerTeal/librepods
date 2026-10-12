package me.kavishdevar.librepods.data

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class AudioSettingsPacketTest {
    private val hearing = HearingAidSettings(FloatArray(8), FloatArray(8), 0.25f, 0.5f,
        0.2f, 0.4f, false, true, 0f, 0.1f, 0.375f, 0f, 0.3f)

    @Test fun hearingEncodePreservesConfirmedBufferAndUnknownTail() {
        val confirmed = ByteArray(108) { 7 }
        val original = confirmed.copyOf()
        val encoded = encodeHearingAidSettings(confirmed, hearing)!!
        assertArrayEquals(original, confirmed)
        assertArrayEquals(original.copyOfRange(104, 108), encoded.copyOfRange(104, 108))
        assertEquals(0.5f, ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN).getFloat(84), 0f)
    }
    @Test fun invalidHealthNumbersOrTruncatedResponseAreRejected() {
        assertNull(encodeHearingAidSettings(ByteArray(103), hearing))
        assertNull(encodeHearingAidSettings(ByteArray(104), hearing.copy(leftEQ = floatArrayOf(1f))))
        assertNull(encodeHearingAidSettings(ByteArray(104), hearing.copy(ownVoiceAmplification = Float.NaN)))
    }
    @Test fun customEqRejectsTruncatedOrOutOfRangeDeviceData() {
        val valid = byteArrayOf(4, 0, 4, 0, 0x63, 0, 5, 0, 1, 2, 60, 50, 40)
        assertEquals(CustomEq(2, 60, 50, 40), CustomEq.fromPacket(valid))
        assertNull(CustomEq.fromPacket(valid.copyOf(12)))
        assertNull(CustomEq.fromPacket(valid.copyOf().apply { this[10] = 101 }))
        assertNull(CustomEq.fromPacket(valid.copyOf().apply { this[9] = 0 }))
    }
    @Test fun transparencyEncodeChecksBandShapeAndOptionalVoiceField() {
        val settings = TransparencySettings(true, FloatArray(8), FloatArray(8),
            0.5f, 0.5f, 0.5f, 0.5f, false, false, 0f, 0f, 0.5f, 0f)
        assertEquals(100, encodeTransparencySettings(settings)!!.size)
        assertEquals(104, encodeTransparencySettings(settings.copy(ownVoiceAmplification = 0.2f))!!.size)
        assertNull(encodeTransparencySettings(settings.copy(leftEQ = FloatArray(9))))
        assertNull(encodeTransparencySettings(settings.copy(rightTone = Float.POSITIVE_INFINITY)))
    }
}
