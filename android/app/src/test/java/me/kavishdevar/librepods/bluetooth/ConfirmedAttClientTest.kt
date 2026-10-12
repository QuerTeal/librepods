package me.kavishdevar.librepods.bluetooth

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test

class ConfirmedAttClientTest {
    @Test fun queuedOldSessionWriteCannotTargetNewDevice() {
        var opened = false
        ConfirmedAttClient { 0L }.use { c ->
            c.connected { Channel { null } }
            val previous = c.sessionId
            c.connected { opened = true; Channel { null } }
            assertFalse(c.write(ATTHandles.HEARING_AID, byteArrayOf(1), expectedSession = previous))
            assertFalse(opened)
        }
    }
    private class Channel(private val reply: (ByteArray) -> ByteArray?) : AttChannel {
        lateinit var sent: ByteArray
        var closed = false
        override fun connect() = Unit
        override fun send(pdu: ByteArray) { sent = pdu.copyOf() }
        override fun receive() = reply(sent)
        override fun close() { closed = true }
    }
    private fun client(reply: (ByteArray) -> ByteArray?) = ConfirmedAttClient { 100L }.apply {
        connected { Channel(reply) }
    }

    @Test fun writeRequiresReadBackAndIncludesPayload() {
        val packets = mutableListOf<List<Byte>>()
        client { pdu ->
            packets.add(pdu.toList())
            if (pdu[0] == 0x12.toByte()) byteArrayOf(0x13) else byteArrayOf(0x0B, 1)
        }.use { c ->
            assertTrue(c.write(ATTHandles.LOUD_SOUND_REDUCTION, byteArrayOf(1)))
            assertEquals(listOf<Byte>(0x12, 0x1B, 0, 1), packets.first())
            assertEquals(SettingPhase.CONFIRMED, c.states.value[ATTHandles.LOUD_SOUND_REDUCTION]?.phase)
        }
    }

    @Test fun rejectedWriteRetainsLastConfirmedValue() {
        client { pdu -> if (pdu[0] == 0x0A.toByte()) byteArrayOf(0x0B, 2) else byteArrayOf(1, 0x12, 0x1B, 0, 5) }
            .use { c ->
                assertArrayEquals(byteArrayOf(2), c.read(ATTHandles.LOUD_SOUND_REDUCTION))
                assertFalse(c.write(ATTHandles.LOUD_SOUND_REDUCTION, byteArrayOf(1)))
                assertArrayEquals(byteArrayOf(2), c.cached(ATTHandles.LOUD_SOUND_REDUCTION))
                assertEquals("write_failed", c.states.value[ATTHandles.LOUD_SOUND_REDUCTION]?.failure)
            }
    }

    @Test fun acceptedButDifferentReadBackDoesNotClaimSuccess() {
        client { pdu -> if (pdu[0] == 0x12.toByte()) byteArrayOf(0x13) else byteArrayOf(0x0B, 2) }
            .use { c ->
                assertFalse(c.write(ATTHandles.LOUD_SOUND_REDUCTION, byteArrayOf(1)))
                assertEquals("readback_mismatch", c.states.value[ATTHandles.LOUD_SOUND_REDUCTION]?.failure)
                assertArrayEquals(byteArrayOf(2), c.cached(ATTHandles.LOUD_SOUND_REDUCTION))
            }
    }

    @Test fun missingReadBackDoesNotUseDesiredAsCache() {
        client { pdu -> if (pdu[0] == 0x12.toByte()) byteArrayOf(0x13) else null }.use { c ->
            assertFalse(c.write(ATTHandles.TRANSPARENCY, byteArrayOf(1, 2)))
            assertNull(c.cached(ATTHandles.TRANSPARENCY))
            assertEquals("readback_failed", c.states.value[ATTHandles.TRANSPARENCY]?.failure)
        }
    }

    @Test fun returnedArraysCannotMutateCache() {
        client { byteArrayOf(0x0B, 1) }.use { c ->
            val read = c.read(ATTHandles.HEARING_AID)!!
            read[0] = 9
            val cached = c.cached(ATTHandles.HEARING_AID)!!
            cached[0] = 8
            assertArrayEquals(byteArrayOf(1), c.cached(ATTHandles.HEARING_AID))
        }
    }

    @Test fun reconnectClosesBlockedReadAndRejectsLateResponse() {
        ConfirmedAttClient { 100L }.use { c ->
            val started = CountDownLatch(1)
            val closed = CountDownLatch(1)
            c.connected {
                object : AttChannel {
                    override fun connect() = Unit
                    override fun send(pdu: ByteArray) = Unit
                    override fun receive(): ByteArray {
                        started.countDown()
                        assertTrue(closed.await(2, TimeUnit.SECONDS))
                        return byteArrayOf(0x0B, 9)
                    }
                    override fun close() { closed.countDown() }
                }
            }
            val worker = thread { assertNull(c.read(ATTHandles.HEARING_AID)) }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            c.connected { Channel { byteArrayOf(0x0B, 3) } }
            worker.join(2000)
            assertFalse(worker.isAlive)
            assertNull(c.cached(ATTHandles.HEARING_AID))
            assertArrayEquals(byteArrayOf(3), c.read(ATTHandles.HEARING_AID))
        }
    }

    @Test fun timeoutActuallyClosesBlockedTransport() {
        ConfirmedAttClient { 100L }.use { c ->
            val closed = CountDownLatch(1)
            c.connected {
                object : AttChannel {
                    override fun connect() { assertTrue(closed.await(2, TimeUnit.SECONDS)) }
                    override fun send(pdu: ByteArray) = Unit
                    override fun receive() = byteArrayOf(0x0B, 1)
                    override fun close() { closed.countDown() }
                }
            }
            assertNull(c.read(ATTHandles.TRANSPARENCY, 50))
            assertEquals(0L, closed.count)
            assertNull(c.cached(ATTHandles.TRANSPARENCY))
        }
    }

    @Test fun closedClientCannotReconnect() {
        val created = AtomicBoolean(false)
        val c = ConfirmedAttClient { 0L }
        c.close()
        c.connected { created.set(true); Channel { null } }
        assertFalse(c.write(ATTHandles.HEARING_AID, byteArrayOf(1)))
        assertFalse(created.get())
    }
}
