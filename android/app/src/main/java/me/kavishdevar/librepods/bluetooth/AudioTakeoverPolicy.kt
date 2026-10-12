package me.kavishdevar.librepods.bluetooth

data class AudioTakeoverOptions(
    val mediaStart: Boolean, val ringingCall: Boolean,
    val disconnected: Boolean, val idle: Boolean, val music: Boolean, val call: Boolean
)

/** All automatic entry points, including an existing control socket, share this decision. */
object AudioTakeoverPolicy {
    fun allows(trigger: String, hostState: String?, inEar: Boolean?, options: AudioTakeoverOptions): Boolean {
        if (inEar != true) return false
        val phoneAllows = when (trigger) {
            "music" -> options.mediaStart
            "call" -> options.ringingCall
            else -> false
        }
        val hostAllows = when (hostState) {
            "Disconnected" -> options.disconnected
            "Idle" -> options.idle
            "Music" -> options.music
            "Call", "Ringing", "Hanging Up" -> options.call
            else -> false
        }
        return phoneAllows && hostAllows
    }
}
