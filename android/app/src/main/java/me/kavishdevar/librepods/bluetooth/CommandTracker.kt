package me.kavishdevar.librepods.bluetooth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CommandState(
    val desired: List<Byte>? = null,
    val confirmed: List<Byte>? = null,
    val phase: SettingPhase = SettingPhase.UNKNOWN,
    val failure: String? = null,
    val sentAtMillis: Long? = null,
    val verifiedAtMillis: Long? = null,
    val revision: Long = 0
)

/** No automatic retry: an uncertain ownership/call command must never be repeated. */
class CommandTracker(private val now: () -> Long, private val timeoutMillis: Long = 5000) {
    private var revision = 0L
    private val mutableStates = MutableStateFlow<Map<Byte, CommandState>>(emptyMap())
    val states = mutableStates.asStateFlow()

    /** A queued/in-flight or failed request must still allow restoration to the old baseline. */
    @Synchronized fun isConfirmedValue(id: Byte, value: ByteArray): Boolean {
        if (value.isEmpty() || value.size > 4) return false
        val state = mutableStates.value[id] ?: return false
        val confirmed = state.confirmed ?: return false
        return state.phase == SettingPhase.CONFIRMED &&
            (0 until 4).all { (value.getOrNull(it) ?: 0.toByte()) == (confirmed.getOrNull(it) ?: 0.toByte()) }
    }

    @Synchronized fun begin(id: Byte, desired: ByteArray): Long {
        require(desired.isNotEmpty() && desired.size <= 4)
        val old = mutableStates.value[id] ?: CommandState()
        val token = ++revision
        mutableStates.value = mutableStates.value + (id to old.copy(desired = desired.toList(),
            phase = SettingPhase.PENDING, failure = null, sentAtMillis = now(), revision = token))
        return token
    }

    @Synchronized fun sendFailed(id: Byte, token: Long) {
        val old = mutableStates.value[id] ?: return
        if (old.revision == token && old.phase == SettingPhase.PENDING) {
            mutableStates.value = mutableStates.value + (id to old.copy(phase = SettingPhase.FAILED,
                failure = "send_failed"))
        }
    }

    @Synchronized fun receive(id: Byte, value: ByteArray) {
        if (value.isEmpty()) return
        val old = mutableStates.value[id] ?: CommandState()
        val matches = old.desired?.let { desired ->
            (0 until 4).all { (value.getOrNull(it) ?: 0.toByte()) == (desired.getOrNull(it) ?: 0.toByte()) }
        } == true
        val waitingOrFailed = old.phase in setOf(SettingPhase.PENDING, SettingPhase.FAILED) && !matches
        mutableStates.value = mutableStates.value + (id to old.copy(confirmed = value.toList(),
            phase = if (waitingOrFailed) old.phase else SettingPhase.CONFIRMED,
            failure = if (waitingOrFailed) old.failure else null, verifiedAtMillis = now()))
    }

    @Synchronized fun expire() {
        val time = now()
        mutableStates.value = mutableStates.value.mapValues { (_, old) ->
            if (old.phase == SettingPhase.PENDING && time - (old.sentAtMillis ?: time) >= timeoutMillis)
                old.copy(phase = SettingPhase.FAILED, failure = "confirmation_timeout") else old
        }
    }

    @Synchronized fun reset() {
        revision++
        mutableStates.value = emptyMap()
    }
}
