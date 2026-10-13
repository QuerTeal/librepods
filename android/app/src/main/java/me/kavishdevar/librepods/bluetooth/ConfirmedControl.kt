package me.kavishdevar.librepods.bluetooth

/** The requested value never substitutes for the last device report. */
object ConfirmedControl {
    fun boolean(state: CommandState?): Boolean? = when (state?.confirmed?.firstOrNull()?.toInt()) {
        1 -> true
        2 -> false
        else -> null
    }

    fun listeningMode(state: CommandState?): Int? =
        state?.confirmed?.firstOrNull()?.toInt()?.takeIf { it in 1..4 }
}

/**
 * AirPods Pro 3 apply most control writes without echoing them, so a delivered write is shown
 * until the AirPods report a newer value. A write that failed to send never shows.
 */
object ShownControl {
    fun value(state: CommandState?): List<Byte>? {
        if (state == null) return null
        val delivered = state.desired != null && state.phase != SettingPhase.CONFIRMED &&
            state.failure != "send_failed" &&
            (state.verifiedAtMillis ?: Long.MIN_VALUE) < (state.sentAtMillis ?: Long.MIN_VALUE)
        return if (delivered) state.desired else state.confirmed
    }

    fun boolean(state: CommandState?): Boolean? = ConfirmedControl.boolean(state?.copy(confirmed = value(state)))

    fun listeningMode(state: CommandState?): Int? = ConfirmedControl.listeningMode(state?.copy(confirmed = value(state)))
}
