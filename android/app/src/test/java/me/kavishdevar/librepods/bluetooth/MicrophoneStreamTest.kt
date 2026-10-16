package me.kavishdevar.librepods.bluetooth

import me.kavishdevar.librepods.audio.WavWriter
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MicrophoneStreamTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    private fun audioPacket(vararg units: ByteArray): ByteArray {
        val header = bytes(0x04, 0, 0x04, 0, 0x58, 0, 0x01, 0x00) + ByteArray(14)
        var body = ByteArray(0)
        units.forEachIndexed { i, unit -> body += bytes(i * 180, 0, 0, 0, unit.size) + unit }
        return header + body
    }

    @Test fun commandsMatchTheKnownSequence() {
        assertArrayEquals(bytes(0x58, 0, 0, 0, 0x09, 0, 0, 0x01, 0x82, 0, 0, 0, 0x04, 0x96, 0), MicrophoneStream.startPacket())
        assertArrayEquals(bytes(0x58, 0, 0, 0, 0x02, 0, 0x03, 0x01), MicrophoneStream.stopPacket())
    }

    @Test fun splitsAnAudioPacketIntoAccessUnits() {
        val frames = MicrophoneStream.frames(audioPacket(ByteArray(80) { 1 }, ByteArray(79) { 2 }, ByteArray(81) { 3 }))
        assertEquals(listOf(80, 79, 81), frames.map { it.size })
        assertEquals(3.toByte(), frames[2][0])
    }

    @Test fun ignoresStatusAndTruncatedUnits() {
        assertTrue(MicrophoneStream.frames(bytes(0x04, 0, 0x04, 0, 0x58, 0, 0, 0, 0x03, 0, 0x01, 0x01, 0x01)).isEmpty())
        val packet = audioPacket(ByteArray(80), ByteArray(80))
        assertEquals(1, MicrophoneStream.frames(packet.copyOf(packet.size - 10)).size)
    }

    @Test fun wavHeaderDescribesMono16BitPcm() {
        val header = ByteBuffer.wrap(WavWriter.header(64_000, 1280)).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(header.array(), 0, 4))
        assertEquals(36 + 1280, header.getInt(4))
        assertEquals(1, header.getShort(22).toInt()) // channels
        assertEquals(64_000, header.getInt(24))
        assertEquals(128_000, header.getInt(28)) // byte rate
        assertEquals(16, header.getShort(34).toInt())
        assertEquals(1280, header.getInt(40))
    }
}
