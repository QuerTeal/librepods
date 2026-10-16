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

package me.kavishdevar.librepods

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.kavishdevar.librepods.presentation.theme.LibrePodsTheme

/** What Health Connect shows when the user asks how LibrePods uses heart rate data. */
class HealthPermissionsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LibrePodsTheme {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
                    Text(stringResource(R.string.health_connect_rationale_title), style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 32.dp, bottom = 16.dp))
                    Text(stringResource(R.string.health_connect_rationale), style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = ::finish, modifier = Modifier.padding(top = 24.dp)) {
                        Text(stringResource(R.string.last_location_close))
                    }
                }
            }
        } }
    }
}
