package me.kavishdevar.librepods

import me.kavishdevar.librepods.presentation.components.ValueSlider
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.kavishdevar.librepods.presentation.theme.LibrePodsTheme
import me.kavishdevar.librepods.utils.AudioMonitor

class AudioMonitorActivity : ComponentActivity() {
    private lateinit var monitor: AudioMonitor
    private var requested by mutableStateOf(false)
    private var status by mutableIntStateOf(R.string.monitor_note)
    private var gain by mutableFloatStateOf(0.25f)
    private var foreground = false
    private var permissionStartPending = false
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionStartPending = it
        if (it && foreground) startMonitoring()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        monitor = AudioMonitor(this) { running, failed ->
            requested = running
            status = if (failed) R.string.monitor_failed else if (running) R.string.monitor_running else R.string.monitor_note
        }
        setContent { LibrePodsTheme {
            Column(Modifier.fillMaxSize().padding(24.dp)) {
                Text(stringResource(R.string.feature_live_listen), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 32.dp, bottom = 16.dp))
                Text(stringResource(R.string.monitor_note))
                Text(stringResource(status), modifier = Modifier.padding(vertical = 16.dp))
                Text(stringResource(R.string.monitor_gain))
                ValueSlider(value = gain, onValueChange = { gain = it; monitor.gain = it }, valueRange = 0f..1f)
                Button(onClick = {
                    if (requested) { monitor.stop(); requested = false; status = R.string.monitor_note }
                    else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startMonitoring()
                    else permissionRequest.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text(stringResource(if (requested) R.string.monitor_stop else R.string.monitor_start)) }
                Button(onClick = ::finish) { Text(stringResource(R.string.last_location_close)) }
            }
        } }
    }
    override fun onResume() {
        super.onResume(); foreground = true
        if (permissionStartPending) startMonitoring()
    }
    override fun onPause() { foreground = false; monitor.stop(); requested = false; status = R.string.monitor_note; super.onPause() }
    override fun onDestroy() { monitor.close(); super.onDestroy() }
    private fun startMonitoring() {
        if (!foreground) return
        permissionStartPending = false
        requested = true
        status = R.string.monitor_connecting
        monitor.start(getSharedPreferences("settings", MODE_PRIVATE).getString("mac_address", "") ?: "")
    }
}
