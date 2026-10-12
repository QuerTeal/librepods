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
