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

package me.kavishdevar.librepods.presentation.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.data.HeartRateMonitor
import me.kavishdevar.librepods.data.HeartRateSample
import me.kavishdevar.librepods.data.HeartRateState
import me.kavishdevar.librepods.data.HeartRateStatus
import me.kavishdevar.librepods.presentation.components.OneUICard
import me.kavishdevar.librepods.presentation.components.StyledSlider
import me.kavishdevar.librepods.presentation.components.StyledToggle
import me.kavishdevar.librepods.presentation.components.screenBottomPadding
import me.kavishdevar.librepods.presentation.components.screenTopPadding
import me.kavishdevar.librepods.presentation.theme.AppleMetrics
import me.kavishdevar.librepods.presentation.theme.AppleShape
import me.kavishdevar.librepods.presentation.theme.DesignSystem
import me.kavishdevar.librepods.presentation.theme.LocalDesignSystem
import me.kavishdevar.librepods.presentation.theme.LocalOneUI
import me.kavishdevar.librepods.presentation.viewmodel.AirPodsViewModel
import kotlin.math.roundToInt

/** The iOS Health heart red. */
private val HeartRed = Color(0xFFFF2D55)

@Composable
fun HeartRateScreen(viewModel: AirPodsViewModel) {
    val backdrop = rememberLayerBackdrop()
    val state by viewModel.uiState.collectAsState()
    val heartRate by viewModel.heartRate.collectAsState()
    val connected = state.isLocallyConnected
    val context = LocalContext.current
    fun notificationsAllowed() =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var notificationsGranted by remember { mutableStateOf(notificationsAllowed()) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsGranted = it
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .layerBackdrop(backdrop)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(screenTopPadding(material = 0.dp)))

        HeartRateCard(heartRate)

        Spacer(modifier = Modifier.height(16.dp))
        StyledToggle(
            label = stringResource(R.string.heart_rate_measure),
            description = stringResource(if (connected) R.string.heart_rate_measure_description else R.string.heart_rate_connect),
            checked = heartRate.status != HeartRateStatus.OFF,
            enabled = connected,
            onCheckedChange = { if (it) viewModel.startHeartRate() else viewModel.stopHeartRate() }
        )

        Spacer(modifier = Modifier.height(16.dp))
        StyledToggle(
            label = stringResource(R.string.heart_rate_alert),
            description = stringResource(R.string.heart_rate_alert_description),
            checked = state.heartRateAlert,
            onCheckedChange = {
                viewModel.setHeartRateAlert(it)
                notificationsGranted = notificationsAllowed()
                if (it && !notificationsGranted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        )
        if (state.heartRateAlert && !notificationsGranted) {
            Text(
                text = stringResource(R.string.heart_rate_alert_no_permission),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = if (LocalOneUI.current) 18.dp else 20.dp, vertical = 4.dp)
            )
        }

        if (state.heartRateAlert) {
            Spacer(modifier = Modifier.height(16.dp))
            var threshold by remember { mutableFloatStateOf(state.heartRateAlertThreshold.toFloat()) }
            StyledSlider(
                label = stringResource(R.string.heart_rate_alert_threshold),
                description = "${threshold.roundToInt()} BPM",
                value = threshold,
                onValueChange = {
                    threshold = it
                    viewModel.setHeartRateAlertThreshold(it.roundToInt())
                },
                valueRange = HeartRateMonitor.ALERT_RANGE.first.toFloat()..HeartRateMonitor.ALERT_RANGE.last.toFloat(),
                startLabel = "${HeartRateMonitor.ALERT_RANGE.first}",
                endLabel = "${HeartRateMonitor.ALERT_RANGE.last}",
                independent = true
            )
        }

        Text(
            text = stringResource(R.string.heart_rate_alert_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = if (LocalOneUI.current) 18.dp else 20.dp, vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(screenBottomPadding(material = 0.dp)))
    }
}

/** The current rate, a heart that beats with it, and this session's trace. */
@Composable
private fun HeartRateCard(heartRate: HeartRateState) {
    val latest = heartRate.latest
    val beating = heartRate.status == HeartRateStatus.ACTIVE && latest != null
    val period = latest?.bpm?.let { (60_000 / it).coerceIn(300, 2000) } ?: 1000
    // Beat only while samples come in; an idle loop would keep redrawing the screen for nothing.
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(beating, period) {
        if (!beating) {
            pulse.animateTo(1f)
            return@LaunchedEffect
        }
        while (true) {
            pulse.animateTo(1.15f, tween(period / 2, easing = FastOutSlowInEasing))
            pulse.animateTo(1f, tween(period / 2, easing = FastOutSlowInEasing))
        }
    }

    CardSurface {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_heart_rate),
                contentDescription = null,
                tint = if (heartRate.status == HeartRateStatus.OFF) MaterialTheme.colorScheme.onSurfaceVariant else HeartRed,
                modifier = Modifier
                    .size(36.dp)
                    .scale(pulse.value)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                Text(
                    text = latest?.bpm?.toString() ?: "--",
                    fontSize = 56.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = " BPM",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            Text(
                text = when (heartRate.status) {
                    HeartRateStatus.OFF -> stringResource(R.string.off)
                    HeartRateStatus.MEASURING -> stringResource(R.string.heart_rate_waiting)
                    HeartRateStatus.ACTIVE -> stringResource(R.string.heart_rate_range, heartRate.min ?: 0, heartRate.max ?: 0)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (heartRate.status == HeartRateStatus.MEASURING) {
                Text(
                    text = stringResource(R.string.heart_rate_waiting_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (heartRate.samples.size >= 2) {
                Spacer(modifier = Modifier.height(16.dp))
                Trace(heartRate.samples.takeLast(120))
            }
        }
    }
}

/** A sparkline of recent samples, scaled to their own range. */
@Composable
private fun Trace(samples: List<HeartRateSample>) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        val low = samples.minOf { it.bpm } - 5
        val high = samples.maxOf { it.bpm } + 5
        val step = size.width / (samples.size - 1)
        val path = Path()
        samples.forEachIndexed { i, sample ->
            val point = Offset(i * step, size.height * (1f - (sample.bpm - low).toFloat() / (high - low)))
            if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        drawPath(path, HeartRed, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** A plain card in the active design. */
@Composable
internal fun CardSurface(content: @Composable () -> Unit) {
    if (LocalOneUI.current) {
        OneUICard { content() }
        return
    }
    val shape = if (LocalDesignSystem.current == DesignSystem.Material) RoundedCornerShape(24.dp) else AppleShape(AppleMetrics.CardRadius)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, shape)
    ) { content() }
}
