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

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.presentation.theme.AppleShape
import me.kavishdevar.librepods.presentation.components.screenBottomPadding
import me.kavishdevar.librepods.presentation.components.screenTopPadding
import me.kavishdevar.librepods.presentation.components.MaterialButtonStyle
import me.kavishdevar.librepods.presentation.components.LocalOneUIOnCard
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.BottomSheetDefaults
import me.kavishdevar.librepods.presentation.components.StyledButton
import me.kavishdevar.librepods.presentation.components.StyledSectionHeader
import me.kavishdevar.librepods.presentation.theme.DesignSystem
import me.kavishdevar.librepods.presentation.theme.LocalDesignSystem
import me.kavishdevar.librepods.presentation.theme.LocalOneUI
import me.kavishdevar.librepods.presentation.components.StyledAlertDialog
import me.kavishdevar.librepods.utils.LogCollector
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import me.kavishdevar.librepods.presentation.theme.sfProFamily

@Composable
fun CustomIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TroubleshootingScreen() {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    val logCollector = remember { LogCollector(context) }
    val savedLogs = remember { mutableStateListOf<File>() }

    var isCollectingLogs by remember { mutableStateOf(false) }
    var showTroubleshootingSteps by remember { mutableStateOf(false) }
    var currentStep by remember { mutableIntStateOf(0) }
    var logContent by remember { mutableStateOf("") }
    var selectedLogFile by remember { mutableStateOf<File?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var isLoadingLogContent by remember { mutableStateOf(false) }
    var logContentLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(isCollectingLogs) {
        while (isCollectingLogs) {
            delay(250)
            delay(250)
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var showBottomSheet by remember { mutableStateOf(false) }

    val oneUI = LocalOneUI.current
    val appleLook = LocalDesignSystem.current == DesignSystem.Apple
    val backgroundColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurface
    val accentColor = MaterialTheme.colorScheme.primary
    val cardRadius = when {
        oneUI -> 26.dp
        appleLook -> 28.dp
        else -> 24.dp
    }
    val cardShape = if (appleLook) AppleShape(cardRadius) else RoundedCornerShape(cardRadius)

    val isDarkTheme = isSystemInDarkTheme()

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val logsDir = File(context.filesDir, "logs")
            if (logsDir.exists()) {
                savedLogs.clear()
                savedLogs.addAll(logsDir.listFiles()?.filter { it.name.endsWith(".txt") }
                    ?.sortedByDescending { it.lastModified() } ?: emptyList())
            }
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(logContent.toByteArray())
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, resources.getString(R.string.log_saved_successfully), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            resources.getString(R.string.failed_to_save_log, e.localizedMessage),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    // Read during composition so it follows both the step and the app language.
    val instructionText = when (currentStep) {
        0 -> stringResource(R.string.troubleshooting_step_xposed)
        1 -> stringResource(R.string.troubleshooting_step_disconnect)
        2 -> stringResource(R.string.troubleshooting_step_preparing)
        3 -> stringResource(R.string.troubleshooting_step_collecting)
        4 -> stringResource(R.string.troubleshooting_step_complete)
        else -> ""
    }

    fun openLogBottomSheet(file: File) {
        selectedLogFile = file
        logContent = ""
        isLoadingLogContent = false
        logContentLoaded = false
        showBottomSheet = true
    }

    val backdrop = rememberLayerBackdrop()

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Only the Apple look draws its toolbar over the content.
        val topPadding = screenTopPadding(material = 8.dp)
        val bottomPadding = screenBottomPadding(material = 16.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .verticalScroll(scrollState)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(topPadding))

            StyledSectionHeader(stringResource(R.string.saved_logs))

            if (savedLogs.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(backgroundColor, cardShape)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.no_logs_found),
                        fontSize = 16.sp,
                        color = textColor
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(backgroundColor, cardShape)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.total_logs, savedLogs.size),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = textColor
                        )

                        if (savedLogs.size > 1) {
                            TextButton(
                                onClick = { showDeleteAllDialog = true },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text(stringResource(R.string.delete_all))
                            }
                        }
                    }

                    savedLogs.forEach { logFile ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clickable {
                                    openLogBottomSheet(logFile)
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = logFile.name,
                                    fontSize = 16.sp,
                                    color = textColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    text = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US)
                                        .format(Date(logFile.lastModified())),
                                    fontSize = 14.sp,
                                    color = textColor.copy(alpha = 0.6f)
                                )
                            }

                            CustomIconButton(
                                onClick = {
                                    selectedLogFile = logFile
                                    showDeleteDialog = true
                                }
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(
                visible = !showTroubleshootingSteps,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300))
            ) {
                StyledButton(
                    backdrop = rememberLayerBackdrop(),
                    isInteractive = false,
                    onClick = { showTroubleshootingSteps = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isCollectingLogs
                ) {
                    Text(stringResource(R.string.collect_logs))
                }
            }

            AnimatedVisibility(
                visible = showTroubleshootingSteps,
                enter = fadeIn(animationSpec = tween(300)) +
                    slideInVertically(animationSpec = tween(300)) { it / 2 },
                exit = fadeOut(animationSpec = tween(300)) +
                    slideOutVertically(animationSpec = tween(300)) { it / 2 }
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))

                    StyledSectionHeader(stringResource(R.string.troubleshooting_steps))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(backgroundColor, cardShape)
                            .padding(16.dp)
                    ) {
                      CompositionLocalProvider(LocalOneUIOnCard provides true) {
                        val textAlpha = animateFloatAsState(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 300),
                            label = "textAlpha"
                        )

                        Text(
                            text = instructionText,
                            fontSize = 16.sp,
                            color = textColor.copy(alpha = textAlpha.value),
                            lineHeight = 22.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        when (currentStep) {
                            0 -> {
                                StyledButton(
                                    materialButtonStyle = MaterialButtonStyle.Filled,
                                    backdrop = rememberLayerBackdrop(),
                                    isInteractive = false,
                                    onClick = {
                                        coroutineScope.launch {
                                            logCollector.openXposedSettings(context)
                                            delay(2000)
                                            currentStep = 1
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.open_xposed_settings))
                                }
                            }

                            1 -> {
                                StyledButton(
                                    materialButtonStyle = MaterialButtonStyle.Filled,
                                    backdrop = rememberLayerBackdrop(),
                                    isInteractive = false,
                                    onClick = {
                                        currentStep = 2
                                        isCollectingLogs = true

                                        coroutineScope.launch {
                                            try {
                                                logCollector.clearLogs()

                                                logCollector.addLogMarker(LogCollector.LogMarkerType.START)

                                                logCollector.killBluetoothService()

                                                withContext(Dispatchers.Main) {
                                                    delay(500)
                                                    currentStep = 3
                                                }

                                                val timestamp = SimpleDateFormat(
                                                    "yyyyMMdd_HHmmss",
                                                    Locale.US
                                                ).format(Date())

                                                logContent =
                                                    logCollector.startLogCollection(
                                                        listener = { /* Removed live log display */ },
                                                        connectionDetectedCallback = {
                                                            launch {
                                                                delay(5000)
                                                                withContext(Dispatchers.Main) {
                                                                    if (isCollectingLogs) {
                                                                        logCollector.stopLogCollection()
                                                                        currentStep = 4
                                                                        isCollectingLogs =
                                                                            false
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    )

                                                val logFile =
                                                    logCollector.saveLogToInternalStorage(
                                                        "airpods_log_$timestamp.txt",
                                                        logContent
                                                    )
                                                logFile?.let {
                                                    withContext(Dispatchers.Main) {
                                                        savedLogs.add(0, it)
                                                        selectedLogFile = it
                                                        Toast.makeText(
                                                            context,
                                                            resources.getString(R.string.log_saved_name, it.name),
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(
                                                        context,
                                                        resources.getString(R.string.error_collecting_logs, e.message),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                    isCollectingLogs = false
                                                    currentStep = 0
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.continue_label))
                                }
                            }

                            2, 3 -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(
                                        color = accentColor
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = if (currentStep == 2) stringResource(R.string.preparing) else stringResource(R.string.collecting_logs),
                                        fontSize = 14.sp,
                                        color = textColor
                                    )

                                    if (currentStep == 3) {
                                        Spacer(modifier = Modifier.height(16.dp))

                                        StyledButton(
                                            backdrop = rememberLayerBackdrop(),
                                            isInteractive = false,
                                            onClick = {
                                                coroutineScope.launch {
                                                    logCollector.addLogMarker(
                                                        LogCollector.LogMarkerType.CUSTOM,
                                                        "Manual stop requested by user"
                                                    )
                                                    delay(1000)
                                                    logCollector.stopLogCollection()
                                                    delay(500)

                                                    withContext(Dispatchers.Main) {
                                                        currentStep = 4
                                                        isCollectingLogs = false
                                                        Toast.makeText(
                                                            context,
                                                            resources.getString(R.string.log_collection_stopped),
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                        ) {
                                            Text(stringResource(R.string.stop_collection))
                                        }
                                    }
                                }
                            }

                            4 -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    StyledButton(
                                        backdrop = rememberLayerBackdrop(),
                                        isInteractive = false,
                                        onClick = {
                                            selectedLogFile?.let { file ->
                                                val fileUri = FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.provider",
                                                    file
                                                )
                                                val shareIntent =
                                                    Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(
                                                            Intent.EXTRA_STREAM,
                                                            fileUri
                                                        )
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                context.startActivity(
                                                    Intent.createChooser(
                                                        shareIntent,
                                                        resources.getString(R.string.share_log_file)
                                                    )
                                                )
                                            }
                                        },
                                        modifier = Modifier.width(150.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Share"
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.share))
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    StyledButton(
                                        backdrop = rememberLayerBackdrop(),
                                        isInteractive = false,
                                        onClick = {
                                            selectedLogFile?.let { file ->
                                                saveLauncher.launch(
                                                    file.absolutePath
                                                )
                                            }
                                        },
                                        modifier = Modifier.width(150.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_save),
                                            contentDescription = "Save"
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.save))
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                StyledButton(
                                    materialButtonStyle = MaterialButtonStyle.Filled,
                                    backdrop = rememberLayerBackdrop(),
                                    isInteractive = false,
                                    onClick = {
                                        currentStep = 0
                                        showTroubleshootingSteps = false
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.done))
                                }
                            }
                        }
                      }
                    }
                }
            }

            if (showDeleteDialog && selectedLogFile != null) {
                StyledAlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = stringResource(R.string.delete_log_file),
                    text = stringResource(R.string.delete_log_file_confirmation),
                    confirmText = stringResource(R.string.delete),
                    dismissText = stringResource(R.string.cancel),
                    destructive = true,
                    onConfirm = {
                                selectedLogFile?.let { file ->
                                    if (file.delete()) {
                                        savedLogs.remove(file)
                                        Toast.makeText(
                                            context,
                                            resources.getString(R.string.log_file_deleted),
                                            Toast.LENGTH_SHORT
                                        )
                                            .show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            resources.getString(R.string.failed_to_delete_log_file),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                                showDeleteDialog = false
                    }
                )
            }

            if (showDeleteAllDialog) {
                StyledAlertDialog(
                    onDismissRequest = { showDeleteAllDialog = false },
                    title = stringResource(R.string.delete_all_logs),
                    text = stringResource(R.string.delete_all_logs_confirmation, savedLogs.size),
                    confirmText = stringResource(R.string.delete_all),
                    dismissText = stringResource(R.string.cancel),
                    destructive = true,
                    onConfirm = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    var deletedCount = 0
                                    savedLogs.forEach { file ->
                                        if (file.delete()) {
                                            deletedCount++
                                        }
                                    }
                                    withContext(Dispatchers.Main) {
                                        if (deletedCount > 0) {
                                            savedLogs.clear()
                                            Toast.makeText(
                                                context,
                                                resources.getString(R.string.deleted_log_files, deletedCount),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                resources.getString(R.string.failed_to_delete_log_files),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                                showDeleteAllDialog = false
                    }
                )
            }

            Spacer(modifier = Modifier.height(bottomPadding))
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                containerColor = when {
                    oneUI -> MaterialTheme.colorScheme.surface
                    appleLook -> if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
                    else -> BottomSheetDefaults.ContainerColor
                },
                shape = if (appleLook || oneUI) RoundedCornerShape(topStart = cardRadius, topEnd = cardRadius) else BottomSheetDefaults.ExpandedShape,
                tonalElevation = 8.dp
            ) {
                LaunchedEffect(selectedLogFile) {
                    if (!logContentLoaded) {
                        delay(300)
                        withContext(Dispatchers.IO) {
                            isLoadingLogContent = true
                            logContent = try {
                                selectedLogFile?.readText() ?: ""
                            } catch (e: Exception) {
                                resources.getString(R.string.error_loading_log_content, e.message)
                            }
                            isLoadingLogContent = false
                            logContentLoaded = true
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    ) {
                        Text(
                            text = selectedLogFile?.name ?: stringResource(R.string.log_content),
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
                            color = textColor
                        )
                        Text(
                            text = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US)
                                .format(Date(selectedLogFile?.lastModified() ?: 0)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isLoadingLogContent) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = accentColor)
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .background(
                                    color = Color.Black,
                                    shape = RoundedCornerShape(8.dp)
                                )
                        ) {
                            val horizontalScrollState = rememberScrollState()
                            val verticalScrollState = rememberScrollState()

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                                    .horizontalScroll(horizontalScrollState)
                                    .verticalScroll(verticalScrollState)
                            ) {
                                Text(
                                    text = logContent,
                                    fontSize = 14.sp,
                                    color = Color.LightGray,
                                    lineHeight = 20.sp,
                                    fontFamily = FontFamily.Monospace,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StyledButton(
                            backdrop = rememberLayerBackdrop(),
                            isInteractive = false,
                            onClick = {
                                selectedLogFile?.let { file ->
                                    val fileUri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.provider",
                                        file
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_STREAM, fileUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(
                                            shareIntent,
                                            resources.getString(R.string.share_log_file)
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.share))
                        }

                        StyledButton(
                            backdrop = rememberLayerBackdrop(),
                            isInteractive = false,
                            onClick = {
                                selectedLogFile?.let { file ->
                                    saveLauncher.launch(file.absolutePath)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_save),
                                contentDescription = "Save"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            logCollector.stopLogCollection()
        }
    }
}
