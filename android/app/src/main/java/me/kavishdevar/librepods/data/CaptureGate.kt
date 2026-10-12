package me.kavishdevar.librepods.data

/** Ignore duplicate shutter events and completions belonging to a closed camera. */
class CaptureGate(private val now: () -> Long) {
    private var generation = 0L
    private var active = false
    private var busy = false
    private var lastCapture = Long.MIN_VALUE
    @Synchronized fun opened(): Long {
        generation++; active = true; busy = false; lastCapture = Long.MIN_VALUE
        return generation
    }
    @Synchronized fun close() { generation++; active = false; busy = false }
    @Synchronized fun isCurrent(session: Long) = active && generation == session
    @Synchronized fun begin(session: Long): Boolean {
        if (!isCurrent(session) || busy || (lastCapture != Long.MIN_VALUE && now() - lastCapture < 800)) return false
        busy = true; lastCapture = now()
        return true
    }
    @Synchronized fun completed(session: Long) { if (isCurrent(session)) busy = false }
}
