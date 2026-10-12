package me.kavishdevar.librepods.bluetooth

/**
 * A lid-open popup is only useful on a screen the user can see. An open seen while the screen is
 * dark is remembered and shown if the user unlocks shortly after, as when they open the case and
 * then pick up the phone.
 */
class PopupScreenGate(private val pendingWindowMillis: Long = 10_000L) {
    private var pendingOpenAt: Long? = null

    /** Returns true when the popup should be shown right away. */
    fun onLidOpened(interactive: Boolean, nowMillis: Long): Boolean {
        if (interactive) {
            pendingOpenAt = null
            return true
        }
        pendingOpenAt = nowMillis
        return false
    }

    fun onLidClosedOrUnknown() {
        pendingOpenAt = null
    }

    /** Returns true when an open deferred by a dark screen should be shown now. */
    fun onUserVisible(nowMillis: Long): Boolean {
        val openedAt = pendingOpenAt ?: return false
        pendingOpenAt = null
        return nowMillis - openedAt <= pendingWindowMillis
    }
}
