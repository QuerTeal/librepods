package me.kavishdevar.librepods.bluetooth

import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class ConnectionCoordinatorTest {
    private class FakeSocket : Closeable {
        var closes = 0
        override fun close() { closes++ }
    }

    @Test fun concurrentTriggersStartOnlyOneSession() {
        val owner = ConnectionCoordinator<FakeSocket>()
        val pool = Executors.newFixedThreadPool(8)
        val started = AtomicInteger()
        try {
            val futures = (1..100).map { pool.submit { if (owner.begin("paired") != null) started.incrementAndGet() } }
            futures.forEach { it.get(2, TimeUnit.SECONDS) }
            assertEquals(1, started.get())
            assertEquals(ControlConnectionPhase.CONNECTING, owner.state.phase)
        } finally { pool.shutdownNow() }
    }

    @Test fun eofClosesOldSocketAndLateCleanupCannotCloseReplacement() {
        val owner = ConnectionCoordinator<FakeSocket>()
        val first = owner.begin("paired")!!
        val firstSocket = FakeSocket()
        assertTrue(owner.attach(first, firstSocket))
        assertTrue(owner.finish(first, "EOF"))
        assertEquals(1, firstSocket.closes)
        val second = owner.begin("paired")!!
        val secondSocket = FakeSocket()
        assertTrue(owner.attach(second, secondSocket))
        assertFalse(owner.finish(first, "late EOF"))
        assertEquals(0, secondSocket.closes)
        assertTrue(owner.isCurrent(second))
    }

    @Test fun socketCreatedAfterCancellationIsClosedWithoutBeingAttached() {
        val owner = ConnectionCoordinator<FakeSocket>()
        val cancelled = owner.begin("paired")!!
        owner.disconnect()
        val replacement = owner.begin("paired")!!
        val lateSocket = FakeSocket()
        assertFalse(owner.attach(cancelled, lateSocket))
        assertEquals(1, lateSocket.closes)
        assertTrue(owner.isCurrent(replacement))
    }

    @Test fun transportConnectionDoesNotBecomeReadyBeforeInitialization() {
        val owner = ConnectionCoordinator<FakeSocket>()
        val session = owner.begin("paired")!!
        assertFalse(owner.ready(session))
        owner.attach(session, FakeSocket())
        assertFalse(owner.ready(session))
        assertTrue(owner.initializing(session))
        assertTrue(owner.ready(session))
        assertFalse(owner.ready(session))
        assertEquals(ControlConnectionPhase.READY, owner.state.phase)
        assertFalse(owner.initializing(session))
    }

    @Test fun manualDisconnectBlocksAutomaticTriggersUntilManualReconnect() {
        val owner = ConnectionCoordinator<FakeSocket>()
        owner.disconnect(manual = true)
        assertNull(owner.begin("paired"))
        assertNotNull(owner.begin("paired", manual = true))
    }

    @Test fun privilegeFailureDoesNotProduceAnAutomaticRetryLoop() {
        val owner = ConnectionCoordinator<FakeSocket>()
        val session = owner.begin("paired")!!
        owner.finish(session, "Permission denied", blocked = true)
        assertEquals(ControlConnectionPhase.BLOCKED, owner.state.phase)
        assertNull(owner.begin("paired"))
        assertNotNull(owner.begin("paired", manual = true))
    }

    @Test fun serviceShutdownPermanentlyStopsThisOwner() {
        val owner = ConnectionCoordinator<FakeSocket>()
        val session = owner.begin("paired")!!
        val socket = FakeSocket()
        owner.attach(session, socket)
        owner.disconnect(shutdown = true)
        assertEquals(1, socket.closes)
        assertFalse(owner.initializing(session))
        assertNull(owner.begin("paired", manual = true))
    }

    @Test fun deadlineClosesBlockingSocketWithoutWaitingForTheConnectThread() {
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val closed = CountDownLatch(1)
        val notified = CountDownLatch(1)
        try {
            val socket = Closeable { closed.countDown() }
            SocketDeadline(scheduler, socket, 10L) { notified.countDown() }.use { deadline ->
                assertTrue(closed.await(2, TimeUnit.SECONDS))
                assertTrue(notified.await(2, TimeUnit.SECONDS))
                assertTrue(deadline.didExpire())
            }
        } finally { scheduler.shutdownNow() }
    }

    @Test fun completedDeadlineDoesNotCloseSocketLater() {
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val closes = AtomicInteger()
        val drained = CountDownLatch(1)
        try {
            val deadline = SocketDeadline(scheduler, Closeable { closes.incrementAndGet() }, 100L)
            deadline.close()
            scheduler.schedule({ drained.countDown() }, 150L, TimeUnit.MILLISECONDS)
            assertTrue(drained.await(2, TimeUnit.SECONDS))
            assertFalse(deadline.didExpire())
            assertEquals(0, closes.get())
        } finally { scheduler.shutdownNow() }
    }
}
