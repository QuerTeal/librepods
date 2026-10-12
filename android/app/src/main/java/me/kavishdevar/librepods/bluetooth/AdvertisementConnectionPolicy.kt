package me.kavishdevar.librepods.bluetooth

/** A case frame has no host/ear state. Only reconnect from it while both buds are seated. */
object AdvertisementConnectionPolicy {
    fun shouldConnect(
        connectionState: String,
        lidOpen: Boolean?,
        inEar: Boolean,
        fromCase: Boolean,
        bothBudsSeated: Boolean
    ): Boolean = if (fromCase) {
        lidOpen == true && bothBudsSeated
    } else {
        connectionState == "Disconnected" && (lidOpen == true || inEar)
    }
}
