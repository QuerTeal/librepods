package me.kavishdevar.librepods.bluetooth

import java.io.Closeable
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ATTHandles(val value: Int) {
    TRANSPARENCY(0x18), LOUD_SOUND_REDUCTION(0x1B), HEARING_AID(0x2A)
}

enum class SettingPhase { UNKNOWN, PENDING, CONFIRMED, FAILED }

data class AttSettingState(
    val desired: List<Byte>? = null,
    val confirmed: List<Byte>? = null,
    val phase: SettingPhase = SettingPhase.UNKNOWN,
    val failure: String? = null,
    val verifiedAtMillis: Long? = null
)

/** A short-lived ATT transport. Closing must interrupt both connect and read. */
interface AttChannel : Closeable {
    fun connect()
    fun send(pdu: ByteArray)
    fun receive(): ByteArray?
}

/** Values belong to one connection generation; only an actual read can confirm a write. */
class ConfirmedAttClient(private val now: () -> Long) : Closeable {
    private val lifecycle = Any()
    private val operations = Any()
    private var generation = 0L
    private var revision = 0L
    private var factory: (() -> AttChannel)? = null
    private var active: AttChannel? = null
    private var closed = false
    private val revisions = mutableMapOf<ATTHandles, Long>()
    private val mutableStates = MutableStateFlow<Map<ATTHandles, AttSettingState>>(emptyMap())
    val states = mutableStates.asStateFlow()
    val sessionId: Long get() = synchronized(lifecycle) { generation }
    private val watchdog = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "ATT-Timeout").apply { isDaemon = true }
    }

    fun connected(channelFactory: () -> AttChannel) = synchronized(lifecycle) {
        if (!closed) {
            invalidate()
            factory = channelFactory
        }
    }

    fun disconnected() = synchronized(lifecycle) { invalidate() }

    private fun invalidate() {
        generation++
        factory = null
        active?.let { runCatching { it.close() } }
        active = null
        revisions.clear()
        mutableStates.value = emptyMap()
    }

    fun cached(handle: ATTHandles): ByteArray? = synchronized(lifecycle) {
        mutableStates.value[handle]?.confirmed?.toByteArray()
    }

    fun read(handle: ATTHandles, timeoutMillis: Long = 3000): ByteArray? {
        val session = synchronized(lifecycle) { generation }
        return synchronized(operations) {
            val value = readValue(session, handle, timeoutMillis)
            synchronized(lifecycle) state@{
                if (session != generation || factory == null) return@state null
                val old = mutableStates.value[handle] ?: AttSettingState()
                mutableStates.value = mutableStates.value + (handle to if (value != null) {
                    old.copy(confirmed = value.toList(),
                        phase = if (old.phase == SettingPhase.PENDING) old.phase else SettingPhase.CONFIRMED,
                        failure = null, verifiedAtMillis = now())
                } else old.copy(phase = SettingPhase.FAILED, failure = "read_failed"))
                value
            }
        }
    }

    fun write(handle: ATTHandles, data: ByteArray, timeoutMillis: Long = 3000,
              expectedSession: Long? = null): Boolean {
        if (data.isEmpty()) return false
        val desired = data.copyOf()
        val token = synchronized(lifecycle) {
            if (factory == null || closed || (expectedSession != null && expectedSession != generation)) return false
            val next = ++revision
            revisions[handle] = next
            val old = mutableStates.value[handle] ?: AttSettingState()
            mutableStates.value = mutableStates.value + (handle to old.copy(
                desired = desired.toList(), phase = SettingPhase.PENDING, failure = null))
            generation to next
        }
        return synchronized(operations) {
            if (!isCurrent(token, handle)) return@synchronized false
            val ack = request(token.first, byteArrayOf(0x12, handle.value.toByte(), 0) + desired, 0x13, timeoutMillis)
            val readBack = if (ack?.size == 1) readValue(token.first, handle, timeoutMillis) else null
            synchronized(lifecycle) state@{
                if (!isCurrent(token, handle)) return@state false
                val matches = readBack?.contentEquals(desired) == true
                val old = mutableStates.value[handle] ?: AttSettingState()
                mutableStates.value = mutableStates.value + (handle to old.copy(
                    confirmed = readBack?.toList() ?: old.confirmed,
                    phase = if (matches) SettingPhase.CONFIRMED else SettingPhase.FAILED,
                    failure = when { matches -> null; ack == null -> "write_failed";
                        readBack == null -> "readback_failed"; else -> "readback_mismatch" },
                    verifiedAtMillis = if (readBack != null) now() else old.verifiedAtMillis))
                matches
            }
        }
    }

    private fun isCurrent(token: Pair<Long, Long>, handle: ATTHandles) = synchronized(lifecycle) {
        token.first == generation && token.second == revisions[handle] && factory != null && !closed
    }

    private fun readValue(session: Long, handle: ATTHandles, timeoutMillis: Long): ByteArray? {
        val response = request(session, byteArrayOf(0x0A, handle.value.toByte(), 0), 0x0B, timeoutMillis)
        return response?.takeIf { it.size > 1 }?.copyOfRange(1, response.size)
    }

    private fun request(session: Long, pdu: ByteArray, opcode: Byte, timeoutMillis: Long): ByteArray? {
        require(timeoutMillis > 0)
        val create = synchronized(lifecycle) { if (session == generation) factory else null } ?: return null
        val channel = runCatching { create() }.getOrNull() ?: return null
        synchronized(lifecycle) {
            if (session != generation || factory == null || closed) {
                runCatching { channel.close() }
                return null
            }
            active = channel
        }
        val deadline = SocketDeadline(watchdog, channel, timeoutMillis)
        return try {
            channel.connect()
            channel.send(pdu)
            var result: ByteArray? = null
            while (true) {
                val response = channel.receive() ?: break
                if (response.isEmpty()) break
                if (response[0] == 0x01.toByte()) break
                if (response[0] == opcode) { result = response; break }
            }
            synchronized(lifecycle) {
                if (session == generation && factory != null && !deadline.didExpire()) result else null
            }
        } catch (_: Exception) { null } finally {
            deadline.close()
            synchronized(lifecycle) { if (active === channel) active = null }
            runCatching { channel.close() }
        }
    }

    override fun close() = synchronized(lifecycle) {
        closed = true
        invalidate()
        watchdog.shutdownNow()
        Unit
    }
}
