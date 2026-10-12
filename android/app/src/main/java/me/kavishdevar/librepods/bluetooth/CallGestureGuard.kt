package me.kavishdevar.librepods.bluetooth

enum class CallPhase { IDLE, RINGING, ACTIVE }

/** A late gesture from an ended call or old AirPods session must not act on a new call. */
class CallGestureGuard {
    data class Token(val call: Long, val connection: Long)
    var phase = CallPhase.IDLE
        private set
    private var generation = 0L
    private var consumed = false
    @Synchronized fun update(next: CallPhase) {
        if (phase != next) { generation++; consumed = false; phase = next }
    }
    @Synchronized fun invalidate() { generation++; consumed = false }
    @Synchronized fun arm(connection: Long): Token? =
        if (phase == CallPhase.RINGING && !consumed) Token(generation, connection) else null
    @Synchronized fun consume(token: Token, connection: Long, ready: Boolean): Boolean {
        if (!ready || phase != CallPhase.RINGING || consumed || token.call != generation ||
            token.connection != connection) return false
        consumed = true
        return true
    }
}
