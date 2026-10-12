package me.kavishdevar.librepods

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.delay
import me.kavishdevar.librepods.data.LastConnectionLocation
import me.kavishdevar.librepods.data.LastConnectionLocationStore
import me.kavishdevar.librepods.presentation.theme.LibrePodsTheme
import java.text.DateFormat
import java.util.Date

class LastConnectionLocationActivity : ComponentActivity() {
    private var enabled by mutableStateOf(false)
    private var record by mutableStateOf<LastConnectionLocation?>(null)
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) enableRecording()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enabled = LastConnectionLocationStore.enabled(this)
        setContent {
            LibrePodsTheme {
                LaunchedEffect(Unit) { while (true) { load(); delay(1000) } }
                Column(Modifier.fillMaxSize().padding(24.dp)) {
                    Text(stringResource(R.string.feature_last_location), style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 32.dp, bottom = 16.dp))
                    Text(stringResource(R.string.last_location_note))
                    Switch(checked = enabled, onCheckedChange = { requested ->
                        if (!requested) {
                            LastConnectionLocationStore.setEnabled(this@LastConnectionLocationActivity, false)
                            enabled = false; record = null
                        } else if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                            enableRecording()
                        else permissionRequest.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    })
                    val snapshot = record
                    if (snapshot == null) Text(stringResource(R.string.last_location_empty))
                    else {
                        Text(stringResource(R.string.last_connection_time, date(snapshot.connectedAtMillis)))
                        snapshot.fix?.let { fix ->
                            Text(stringResource(R.string.last_location_time, date(fix.measuredAtMillis), fix.accuracyMeters.toInt()))
                            Button(onClick = {
                                val uri = "geo:0,0?q=${fix.latitude},${fix.longitude}".toUri()
                                runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }.onFailure {
                                    Toast.makeText(this@LastConnectionLocationActivity, R.string.last_location_map_missing, Toast.LENGTH_SHORT).show()
                                }
                            }) { Text(stringResource(R.string.last_location_open_map)) }
                        } ?: Text(stringResource(R.string.last_location_unknown))
                    }
                    Button(onClick = ::finish) { Text(stringResource(R.string.last_location_close)) }
                }
            }
        }
    }
    override fun onResume() { super.onResume(); LastConnectionLocationStore.refreshPhoneFix(this); load() }
    private fun enableRecording() {
        LastConnectionLocationStore.setEnabled(this, true)
        LastConnectionLocationStore.refreshPhoneFix(this)
        enabled = true
        load()
    }
    private fun load() {
        record = LastConnectionLocationStore.read(this, getSharedPreferences("settings", MODE_PRIVATE)
            .getString("mac_address", "") ?: "")
    }
    private fun date(time: Long) = DateFormat.getDateTimeInstance().format(Date(time))
}
