/*
    LibrePods - AirPods liberated from Apple’s ecosystem
    Copyright (C) 2025 LibrePods contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package me.kavishdevar.librepods.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class HeartRateStatus { OFF, MEASURING, ACTIVE }

data class HeartRateSample(val bpm: Int, val atMillis: Long)

data class HeartRateState(
    val status: HeartRateStatus = HeartRateStatus.OFF,
    /** This session's samples, oldest first, at most [HeartRateMonitor.MAX_SAMPLES]. */
    val samples: List<HeartRateSample> = emptyList()
) {
    val latest: HeartRateSample? get() = samples.lastOrNull()
    val min: Int? get() = samples.minOfOrNull { it.bpm }
    val max: Int? get() = samples.maxOfOrNull { it.bpm }
}

/** Live heart rate from the buds, kept by the service while measuring and shown by the UI. */
object HeartRateMonitor {
    const val MAX_SAMPLES = 300
    const val DEFAULT_ALERT_THRESHOLD = 150
    val ALERT_RANGE = 120..200

    private val _state = MutableStateFlow(HeartRateState())
    val state = _state.asStateFlow()

    fun measuring() = _state.update { HeartRateState(HeartRateStatus.MEASURING) }

    fun add(bpm: Int, atMillis: Long = System.currentTimeMillis()) = _state.update {
        if (it.status == HeartRateStatus.OFF) it
        else HeartRateState(HeartRateStatus.ACTIVE, (it.samples + HeartRateSample(bpm, atMillis)).takeLast(MAX_SAMPLES))
    }

    fun off() = _state.update { HeartRateState() }
}
