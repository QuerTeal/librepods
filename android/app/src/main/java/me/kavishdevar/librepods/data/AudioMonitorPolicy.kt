package me.kavishdevar.librepods.data

object AudioMonitorPolicy {
    fun canForward(targetId: Int, actualOutputIds: List<Int>, builtInMicrophone: Boolean): Boolean =
        builtInMicrophone && actualOutputIds == listOf(targetId)
    fun applyGain(samples: ShortArray, count: Int, gain: Float) {
        require(count in 0..samples.size && gain.isFinite() && gain in 0f..1f)
        for (i in 0 until count) samples[i] = (samples[i] * gain).toInt().coerceIn(-32768, 32767).toShort()
    }
}
