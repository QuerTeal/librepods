package me.kavishdevar.librepods.bluetooth

import java.io.Closeable
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

enum class ControlConnectionPhase { IDLE, CONNECTING, INITIALIZING, READY, BLOCKED, STOPPED }

data class ControlConnectionState(
    val phase: ControlConnectionPhase = ControlConnectionPhase.IDLE,
    val address: String? = null,
    val sessionId: Long = 0,
    val reason: String? = null
)

/** Owns one control session. A finished session cannot close or publish a newer session. */
class ConnectionCoordinator<T : Closeable> {
    data class Session(val id: Long, val address: String)

    private var generation = 0L
    private var current: Session? = null
    private var resource: T? = null
    private var manuallyDisconnected = false
    private var stopped = false

    var state = ControlConnectionState()
        private set

    @Synchronized
    fun begin(address: String, manual: Boolean = false): Session? {
        if (stopped || current != null) return null
        if (!manual && (manuallyDisconnected || state.phase == ControlConnectionPhase.BLOCKED)) return null
        if (manual) manuallyDisconnected = false
        return Session(++generation, address).also {
            current = it
            state = ControlConnectionState(ControlConnectionPhase.CONNECTING, address, it.id)
        }
    }

    @Synchronized
    fun attach(session: Session, socket: T): Boolean {
        if (current != session || resource != null) {
            if (resource !== socket) closeQuietly(socket)
            return false
        }
        resource = socket
        return true
    }

    @Synchronized
    fun isCurrent(session: Session): Boolean = current == session

    @Synchronized
    fun initializing(session: Session): Boolean {
        if (current != session || resource == null || state.phase != ControlConnectionPhase.CONNECTING) return false
        state = state.copy(phase = ControlConnectionPhase.INITIALIZING)
        return true
    }

    @Synchronized
    fun ready(session: Session): Boolean {
        if (current != session || state.phase != ControlConnectionPhase.INITIALIZING) return false
        state = state.copy(phase = ControlConnectionPhase.READY)
        return true
    }

    @Synchronized
    fun finish(session: Session, reason: String, blocked: Boolean = false): Boolean {
        if (current != session) return false
        detach()
        state = ControlConnectionState(
            if (blocked) ControlConnectionPhase.BLOCKED else ControlConnectionPhase.IDLE,
            session.address, session.id, reason
        )
        return true
    }

    @Synchronized
    fun disconnect(manual: Boolean = false, shutdown: Boolean = false) {
        manuallyDisconnected = manuallyDisconnected || manual
        stopped = stopped || shutdown
        val address = current?.address ?: state.address
        detach()
        state = ControlConnectionState(
            if (stopped) ControlConnectionPhase.STOPPED else ControlConnectionPhase.IDLE,
            address, ++generation
        )
    }

    private fun detach() {
        current = null
        val old = resource
        resource = null
        old?.let(::closeQuietly)
    }

    private fun closeQuietly(socket: T) {
        try { socket.close() } catch (_: Exception) { }
    }
}

/** Cancelling a coroutine cannot interrupt BluetoothSocket.connect/read; closing the socket can. */
class SocketDeadline(
    scheduler: ScheduledExecutorService,
    socket: Closeable,
    timeoutMillis: Long,
    onTimeout: () -> Unit = {}
) : Closeable {
    private val completed = AtomicBoolean(false)
    private val expired = AtomicBoolean(false)
    private val timeout = scheduler.schedule({
        if (completed.compareAndSet(false, true)) {
            expired.set(true)
            try { socket.close() } catch (_: Exception) { }
            onTimeout()
        }
    }, timeoutMillis, TimeUnit.MILLISECONDS)

    fun didExpire(): Boolean = expired.get()

    override fun close() {
        completed.compareAndSet(false, true)
        timeout.cancel(false)
    }
}
