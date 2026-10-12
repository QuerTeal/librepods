package me.kavishdevar.librepods.bluetooth

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class AttWriteDebouncerTest {
    private val handle = ATTHandles.LOUD_SOUND_REDUCTION
    private val initial = AttSettingState(confirmed = listOf(0), phase = SettingPhase.CONFIRMED)

    @Test fun returnToOriginalSupersedesDebouncedChange() = runBlocking {
        val writes = mutableListOf<List<Byte>>()
        val completed = CompletableDeferred<Unit>()
        val writer = AttWriteDebouncer(this, 30)
        writer.submit(handle, byteArrayOf(1), initial) { writes.add(it.toList()) }
        writer.submit(handle, byteArrayOf(0), initial) { writes.add(it.toList()); completed.complete(Unit) }
        withTimeout(2000) { completed.await() }
        assertEquals(listOf(listOf<Byte>(0)), writes)
    }

    @Test fun cancellationBeforeDispatchCannotWriteAfterReconnect() = runBlocking {
        var writes = 0
        val writer = AttWriteDebouncer(this, 30)
        writer.submit(handle, byteArrayOf(1), initial) { writes++ }
        writer.cancelAll()
        delay(80)
        assertEquals(0, writes)
    }

    @Test fun unchangedConfirmedValueDoesNotStartAnotherTransaction() = runBlocking {
        var writes = 0
        val writer = AttWriteDebouncer(this, 10)
        writer.submit(handle, byteArrayOf(0), initial) { writes++ }
        delay(40)
        assertEquals(0, writes)
    }

    @Test fun latestPayloadIsCopiedBeforeDebounce() = runBlocking {
        val result = CompletableDeferred<ByteArray>()
        val desired = byteArrayOf(1)
        val writer = AttWriteDebouncer(this, 10)
        writer.submit(handle, desired, initial) { result.complete(it) }
        desired[0] = 0
        assertArrayEquals(byteArrayOf(1), withTimeout(2000) { result.await() })
    }

    @Test fun returnToOriginalRestoresAfterBlockingTransportWrite() = runBlocking {
        val stored = AtomicInteger(0)
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val restored = CompletableDeferred<Unit>()
        ConfirmedAttClient { 0L }.use { client ->
            client.connected {
                object : AttChannel {
                    private var response: ByteArray? = null
                    override fun connect() = Unit
                    override fun send(pdu: ByteArray) {
                        if (pdu[0] == 0x12.toByte()) {
                            if (pdu[3] == 1.toByte()) {
                                entered.complete(Unit)
                                check(release.await(2, TimeUnit.SECONDS))
                            }
                            stored.set(pdu[3].toInt())
                            response = byteArrayOf(0x13)
                        } else response = byteArrayOf(0x0b, stored.get().toByte())
                    }
                    override fun receive() = response
                    override fun close() = Unit
                }
            }
            client.read(handle)
            val writer = AttWriteDebouncer(this, 10)
            try {
                writer.submit(handle, byteArrayOf(1), client.states.value[handle]) {
                    withContext(Dispatchers.IO) { client.write(handle, it) }
                }
                withTimeout(2000) { entered.await() }
                assertEquals(SettingPhase.PENDING, client.states.value[handle]?.phase)
                writer.submit(handle, byteArrayOf(0), client.states.value[handle]) {
                    withContext(Dispatchers.IO) { assertTrue(client.write(handle, it)) }
                    restored.complete(Unit)
                }
                release.countDown()
                withTimeout(2000) { restored.await() }
                assertEquals(0, stored.get())
                assertEquals(listOf<Byte>(0), client.states.value[handle]?.confirmed)
            } finally {
                release.countDown()
                writer.cancelAll()
            }
        }
    }
}
