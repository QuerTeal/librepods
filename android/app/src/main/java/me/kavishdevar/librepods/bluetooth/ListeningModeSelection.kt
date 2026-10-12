package me.kavishdevar.librepods.bluetooth

/** Keeps edit intent separate from device confirmation while several mode choices are in flight. */
class ListeningModeSelection {
    private var requested: Int? = null

    @Synchronized fun request(value: Int) { requested = value and 0xff }

    @Synchronized fun toggle(confirmed: Int, modeBit: Int): Int {
        require(modeBit in setOf(1, 2, 4, 8))
        val current = requested ?: (confirmed and 0xff)
        val next = if (current and modeBit != 0) {
            val removed = current and modeBit.inv()
            if ((removed and 0x0f).countOneBits() >= 2) removed else current
        } else current or modeBit
        request(next)
        return next
    }

    @Synchronized fun receive(value: Int) { if (requested == (value and 0xff)) requested = null }
    @Synchronized fun failed(value: Int?) { if (requested == value) requested = null }
    @Synchronized fun reset() { requested = null }
}
