package me.kavishdevar.librepods.bluetooth

/** A resume intent is single-use and bound to the connection and wear event that created it. */
class DeferredPlaybackGate(private val now: () -> Long, private val timeoutMillis: Long = 5000) {
    private data class Intent(val session: Long, val address: String, val createdAt: Long)
    private var pending: Intent? = null
    @Synchronized fun arm(session: Long, address: String) {
        pending = Intent(session, address, now())
    }
    @Synchronized fun cancel() { pending = null }
    @Synchronized fun consume(session: Long, address: String, ready: Boolean, worn: Boolean,
                              inCall: Boolean): Boolean {
        val intent = pending ?: return false
        if (intent.address != address) return false
        pending = null
        return intent.session == session && intent.address == address && ready && worn && !inCall &&
            now() - intent.createdAt in 0..timeoutMillis
    }
}
