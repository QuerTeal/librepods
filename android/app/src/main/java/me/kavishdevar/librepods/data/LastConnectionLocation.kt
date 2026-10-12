package me.kavishdevar.librepods.data

data class PhoneLocationFix(val latitude: Double, val longitude: Double, val accuracyMeters: Float,
    val measuredAtMillis: Long, val elapsedAtMillis: Long) {
    fun isUsable(nowElapsed: Long): Boolean = latitude.isFinite() && longitude.isFinite() &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0 && accuracyMeters.isFinite() &&
        accuracyMeters >= 0f && nowElapsed - elapsedAtMillis in 0..600_000L
}

data class LastConnectionLocation(val deviceId: String, val connectedAtMillis: Long,
    val fix: PhoneLocationFix?)
