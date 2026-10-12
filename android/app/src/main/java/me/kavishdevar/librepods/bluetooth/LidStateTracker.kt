package me.kavishdevar.librepods.bluetooth

enum class LidState { OPEN, CLOSED, UNKNOWN }

/** Only verified advertisements feed this tracker. Radio silence never becomes a confirmed close. */
class LidStateTracker(freshnessMillis: Long = 2500L) {
    /** How long one advertisement keeps the state alive; longer while the scanner runs at low duty. */
    var freshnessMillis: Long = freshnessMillis
    var state: LidState = LidState.UNKNOWN
        private set
    private var lastSeenMillis: Long? = null
    private var lastObservedState: LidState? = null
    private var lastOpenCounter: Int? = null

    fun observe(open: Boolean, seenMillis: Long, openCounter: Int? = null): LidState? {
        if (lastSeenMillis?.let { seenMillis < it } == true) return null
        lastSeenMillis = seenMillis
        val next = if (open) LidState.OPEN else LidState.CLOSED
        val sameCaseOpening = openCounter != null && next == LidState.OPEN &&
            lastObservedState == LidState.OPEN && lastOpenCounter == openCounter
        val missedCaseOpening = openCounter != null && next == LidState.OPEN &&
            lastOpenCounter != null && lastOpenCounter != openCounter
        val changed = next != state
        state = next
        lastObservedState = next
        if (openCounter != null) lastOpenCounter = openCounter
        // Sparse case broadcasts can expire while the lid remains open. The counter identifies
        // that same opening and also detects a close/reopen whose closed broadcast was missed.
        return next.takeIf { !sameCaseOpening && (changed || missedCaseOpening) }
    }

    fun expire(nowMillis: Long): Boolean {
        val seen = lastSeenMillis ?: return false
        if (state == LidState.UNKNOWN || nowMillis - seen < freshnessMillis) return false
        state = LidState.UNKNOWN
        return true
    }

    fun reset() {
        state = LidState.UNKNOWN
        lastSeenMillis = null
        lastObservedState = null
        lastOpenCounter = null
    }
}
