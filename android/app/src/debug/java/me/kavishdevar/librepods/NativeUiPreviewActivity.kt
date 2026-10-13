package me.kavishdevar.librepods

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import me.kavishdevar.librepods.data.*
import me.kavishdevar.librepods.presentation.components.*
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import me.kavishdevar.librepods.presentation.theme.AppDesign
import me.kavishdevar.librepods.presentation.theme.LibrePodsTheme
import me.kavishdevar.librepods.presentation.screens.ContactSheet

/** Real UI components, isolated from device commands, for repeatable device QA. */
class NativeUiPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val fontScale = intent.getFloatExtra("font_scale", 1f).coerceIn(0.85f, 2f)
        super.onCreate(savedInstanceState)
        setTurnScreenOn(true)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = if (intent.getBooleanExtra("landscape", false))
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val panel = intent.getStringExtra("scenario") == "panel"
        val showDialog = intent.getStringExtra("scenario") == "dialog"
        val dark = intent.getBooleanExtra("dark", false)
        val initialDesign = AppDesign.fromPreferenceValue(intent.getStringExtra("design"))
        val bars = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        setContent {
            var design by rememberSaveable { mutableStateOf(initialDesign) }
            val configuration = androidx.compose.ui.platform.LocalConfiguration.current
            val previewConfiguration = remember(configuration, dark) {
                android.content.res.Configuration(configuration).apply {
                    uiMode = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (dark) android.content.res.Configuration.UI_MODE_NIGHT_YES
                        else android.content.res.Configuration.UI_MODE_NIGHT_NO
                }
            }
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
                androidx.compose.ui.platform.LocalConfiguration provides previewConfiguration
            ) {
                LibrePodsTheme(darkTheme = dark, design = design) {
                    if (panel) PanelFixture()
                    else if (intent.getStringExtra("scenario") == "fractional") FractionalFixture()
                    else if (intent.getStringExtra("scenario") == "widgets") WidgetsFixture()
                    else if (intent.getStringExtra("scenario") == "sheet") SheetFixture()
                    else if (intent.getStringExtra("scenario") == "toolbar") ToolbarFixture()
                    else if (intent.getStringExtra("scenario") == "detent") DetentFixture()
                    else if (intent.getStringExtra("scenario") == "features") FeatureFixture()
                    else if (intent.getStringExtra("scenario") == "navigation") SettingsNavigationFixture()
                    else if (intent.getStringExtra("scenario") == "companion") CompanionManagementFixture()
                    else if (intent.getStringExtra("scenario") == "battery") BatteryFixture()
                    else if (intent.getStringExtra("scenario") == "images") ProductImagesFixture()
                    else if (intent.getStringExtra("scenario") == "audit") UiAuditFixture()
                    else ControlsFixture(design, showDialog) { design = it }
                }
            }
        }
    }
}

@Composable
private fun BatteryFixture() {
    var sample by rememberSaveable { mutableIntStateOf(0) }
    val batteries = when (sample) {
        1 -> listOf(BatteryComponent.LEFT, BatteryComponent.RIGHT, BatteryComponent.CASE)
            .map { Battery(it, 0, BatteryStatus.DISCONNECTED) }
        2 -> listOf(Battery(BatteryComponent.LEFT, 0, BatteryStatus.NOT_CHARGING),
            Battery(BatteryComponent.RIGHT, 80, BatteryStatus.NOT_CHARGING),
            Battery(BatteryComponent.CASE, 0, BatteryStatus.CHARGING))
        3 -> listOf(Battery(BatteryComponent.LEFT, -1, BatteryStatus.CHARGING),
            Battery(BatteryComponent.RIGHT, 42, BatteryStatus.NOT_CHARGING),
            Battery(BatteryComponent.CASE, 255, BatteryStatus.NOT_CHARGING))
        else -> emptyList()
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Text("배터리 샘플: $sample")
        BatteryView(batteries, R.drawable.airpods_pro_3_buds, R.drawable.airpods_pro_3_case)
        StyledButton(onClick = { sample++ }, backdrop = rememberLayerBackdrop()) { Text("배터리 샘플 변경") }
    }
}

@Composable private fun UiAuditFixture() {
    var value by rememberSaveable { mutableFloatStateOf(25f) }
    var sliderChanges by rememberSaveable { mutableIntStateOf(0) }
    var buttonChanges by rememberSaveable { mutableIntStateOf(0) }
    var ticks by rememberSaveable { mutableIntStateOf(0) }
    val platformHaptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val feedback = remember(platformHaptics) {
        object : androidx.compose.ui.hapticfeedback.HapticFeedback {
            override fun performHapticFeedback(type: androidx.compose.ui.hapticfeedback.HapticFeedbackType) {
                ticks++
                platformHaptics.performHapticFeedback(type)
            }
        }
    }
    val backdrop = rememberLayerBackdrop()
    CompositionLocalProvider(androidx.compose.ui.platform.LocalHapticFeedback provides feedback) {
        Box(Modifier.fillMaxSize()) {
            // Capture only a background sibling; glass controls cannot capture themselves.
            Box(Modifier.matchParentSize().layerBackdrop(backdrop)
                .background(MaterialTheme.colorScheme.surfaceContainer))
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("UI 재점검 샘플", color = MaterialTheme.colorScheme.onSurface)
                Text("슬라이더 변경: $sliderChanges / 버튼 변경: $buttonChanges / 진동: $ticks",
                    color = MaterialTheme.colorScheme.onSurface)
                Text("조절값: $value", color = MaterialTheme.colorScheme.onSurface)
                StyledSlider(label = "접근성 조절", value = value, valueRange = 0f..100f,
                    snapPoints = listOf(43f), snapThreshold = 2f,
                    onValueChange = { value = it; sliderChanges++ })
                StyledSlider(label = "비활성 조절", value = 50f, valueRange = 0f..100f,
                    enabled = false, onValueChange = { sliderChanges++ })
                StyledButton(onClick = { buttonChanges++ }, backdrop = backdrop,
                    materialButtonStyle = MaterialButtonStyle.Filled) { Text("유리 버튼") }
                StyledButton(onClick = { buttonChanges++ }, backdrop = backdrop,
                    isInteractive = false) { Text("일반 누름") }
                StyledButton(onClick = { buttonChanges++ }, backdrop = backdrop,
                    enabled = false) { Text("비활성 버튼") }
                StyledButton(onClick = { value = 25f }, backdrop = backdrop) { Text("상태 복원") }
            }
        }
    }
}

@Composable private fun ProductImagesFixture() {
    val model = remember { AirPodsPro3() }
    var sample by rememberSaveable { mutableIntStateOf(0) }
    val batteries = when (sample % 5) {
        1 -> listOf(Battery(BatteryComponent.LEFT, 85, BatteryStatus.NOT_CHARGING),
            Battery(BatteryComponent.RIGHT, 40, BatteryStatus.NOT_CHARGING),
            Battery(BatteryComponent.CASE, 60, BatteryStatus.NOT_CHARGING))
        2 -> listOf(Battery(BatteryComponent.LEFT, 85, BatteryStatus.NOT_CHARGING),
            Battery(BatteryComponent.CASE, 60, BatteryStatus.NOT_CHARGING))
        3 -> listOf(Battery(BatteryComponent.RIGHT, 40, BatteryStatus.NOT_CHARGING),
            Battery(BatteryComponent.CASE, 60, BatteryStatus.NOT_CHARGING))
        4 -> listOf(Battery(BatteryComponent.LEFT, 100, BatteryStatus.CHARGING),
            Battery(BatteryComponent.RIGHT, 100, BatteryStatus.NOT_CHARGING),
            Battery(BatteryComponent.CASE, 60, BatteryStatus.NOT_CHARGING))
        else -> listOf(BatteryComponent.LEFT, BatteryComponent.RIGHT, BatteryComponent.CASE)
            .map { Battery(it, 100, BatteryStatus.NOT_CHARGING) }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(model.name, style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground)
        Text("이미지 배치: $sample", color = MaterialTheme.colorScheme.onBackground)
        BatteryView(batteries, model.budsRes, model.caseRes, model.leftBudsRes, model.rightBudsRes)
        Spacer(Modifier.height(24.dp))
        StyledButton(onClick = { sample++ }, backdrop = rememberLayerBackdrop()) { Text("이미지 배치 변경") }
    }
}

@Composable private fun FeatureFixture() {
    val state = me.kavishdevar.librepods.presentation.viewmodel.demoState.copy(
        vendorIdHook = false, locationOptIn = false, audioOutputAvailable = null,
        commandStates = emptyMap(), attStates = emptyMap())
    StyledScaffold(title = "기능 확인 샘플", showBackButton = false) {
        me.kavishdevar.librepods.presentation.screens.FeatureStatusContent(state,
            AudioDiagnostics(true, true, false, null, null, false, false), 0L, {}, {})
    }
}

@Composable private fun SettingsNavigationFixture() {
    val stack = remember { mutableStateListOf("settings") }
    var registration by rememberSaveable { mutableIntStateOf(1) }
    var companionClicks by rememberSaveable { mutableIntStateOf(0) }
    val apple = me.kavishdevar.librepods.presentation.theme.LocalDesignSystem.current ==
        me.kavishdevar.librepods.presentation.theme.DesignSystem.Apple
    val features = stack.last() == "features"
    StyledScaffold(title = androidx.compose.ui.res.stringResource(
        if (features) R.string.feature_status_title else R.string.settings),
        showBackButton = true, onNavigateBack = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }) {
        androidx.navigation3.ui.NavDisplay(
            modifier = Modifier.fillMaxSize(), backStack = stack,
            onBack = { if (stack.size > 1) stack.removeAt(stack.lastIndex) },
            transitionSpec = { me.kavishdevar.librepods.presentation.navigation.appPushTransition(apple) },
            popTransitionSpec = { me.kavishdevar.librepods.presentation.navigation.appPopTransition(apple) },
            predictivePopTransitionSpec = { me.kavishdevar.librepods.presentation.navigation.appPopTransition(apple) },
            entryProvider = { route ->
                androidx.navigation3.runtime.NavEntry(route) {
                    if (route == "settings") {
                        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)
                            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                            Spacer(Modifier.height(screenTopPadding()))
                            me.kavishdevar.librepods.presentation.screens.GeneralSettingsSection(
                                languageSummary = "시스템 기본값", registered = registration != 0,
                                detectionFailed = registration == 2, navigateToLanguage = {},
                                navigateToFeatureStatus = { stack.add("features") },
                                manageCompanion = { companionClicks++ })
                            Text("설정 이동 샘플 / 관리 호출: $companionClicks", Modifier.padding(16.dp))
                            StyledButton(onClick = { registration = (registration + 1) % 3 },
                                backdrop = rememberLayerBackdrop()) { Text("등록 상태 변경") }
                            Spacer(Modifier.height(screenBottomPadding()))
                        }
                    } else {
                        val state = me.kavishdevar.librepods.presentation.viewmodel.demoState.copy(
                            vendorIdHook = false, commandStates = emptyMap(), attStates = emptyMap())
                        me.kavishdevar.librepods.presentation.screens.FeatureStatusContent(state,
                            AudioDiagnostics(true, true, registration != 0, null, null, false, false), 0L, {}, {})
                    }
                }
            })
    }
}

@Composable private fun CompanionManagementFixture() {
    var enabled by rememberSaveable { mutableIntStateOf(0) }
    var removed by rememberSaveable { mutableIntStateOf(0) }
    var backed by rememberSaveable { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize()) {
        me.kavishdevar.librepods.presentation.screens.CompanionManagementContent(
            onEnable = { enabled++ }, onRemove = { removed++ }, onBack = { backed++ })
        Text("관리 샘플: 감지 $enabled / 해제 $removed / 뒤로 $backed",
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(8.dp))
    }
}

@Composable
private fun DetentFixture() {
    var value by rememberSaveable { mutableFloatStateOf(55f) }
    var changes by rememberSaveable { mutableIntStateOf(0) }
    var ticks by rememberSaveable { mutableIntStateOf(0) }
    val platformHaptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val feedback = remember(platformHaptics) {
        object : androidx.compose.ui.hapticfeedback.HapticFeedback {
            override fun performHapticFeedback(type: androidx.compose.ui.hapticfeedback.HapticFeedbackType) {
                ticks++
                platformHaptics.performHapticFeedback(type)
            }
        }
    }
    CompositionLocalProvider(androidx.compose.ui.platform.LocalHapticFeedback provides feedback) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
            Text("샘플 변경: $changes")
            Text("기준값: $value / 진동: $ticks")
            StyledSlider(label = "기본값 스냅", value = value, valueRange = 10f..85f,
                snapPoints = listOf(ConversationalAwarenessDefaults.VOLUME_PERCENT.toFloat()),
                snapThreshold = ConversationalAwarenessDefaults.SNAP_THRESHOLD,
                onValueChange = { value = it; changes++ })
            StyledButton(onClick = { value = 43f }, backdrop = rememberLayerBackdrop()) { Text("기본값 복원") }
        }
    }
}

@Composable
private fun ToolbarFixture() {
    val scroll = rememberScrollState()
    var changes by rememberSaveable { mutableIntStateOf(0) }
    StyledScaffold(title = "스크롤 검증", showBackButton = true, onNavigateBack = { changes++ }) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(screenTopPadding()))
            Text("샘플 변경: $changes")
            repeat(30) { index ->
                StyledList { StyledListItem(name = "스크롤 항목 ${index + 1}", onClick = {}) }
                Spacer(Modifier.height(12.dp))
            }
            Spacer(Modifier.height(screenBottomPadding()))
        }
    }
}

@Composable
private fun SheetFixture() {
    var visible by rememberSaveable { mutableStateOf(true) }
    var changes by rememberSaveable { mutableIntStateOf(0) }
    val subject = rememberTextFieldState("")
    val description = rememberTextFieldState("")
    val subjectFocus = remember { FocusRequester() }
    val descriptionFocus = remember { FocusRequester() }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).statusBarsPadding()) {
        Text("샘플 변경: $changes")
    }
    if (visible) ContactSheet(subject, description, subjectFocus, descriptionFocus,
        onDismiss = { visible = false }, onSend = { changes++; visible = false })
}

@Composable
private fun WidgetsFixture() {
    var changes by rememberSaveable { mutableIntStateOf(0) }
    var checked by rememberSaveable { mutableStateOf(true) }
    val field = rememberTextFieldState("AirPods Pro 3")
    val focus = remember { FocusRequester() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).statusBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("샘플 변경: $changes")
        StyledButton(onClick = { changes++ }, backdrop = rememberLayerBackdrop(), materialButtonStyle = MaterialButtonStyle.Filled, modifier = Modifier.fillMaxWidth()) { Text("샘플 버튼") }
        StyledButton(onClick = { changes++ }, backdrop = rememberLayerBackdrop(), enabled = false, modifier = Modifier.fillMaxWidth()) { Text("비활성 버튼") }
        StyledList {
            StyledListItem(name = "라디오 샘플", selected = true, onClick = {})
            StyledListItem(name = "체크 샘플", checked = true, onClick = {})
            StyledToggle(label = "스위치 샘플", checked = checked, onCheckedChange = { checked = it; changes++ })
        }
        StyledInputField(field, focus, label = "기기 이름")
        Text("입력 값: ${field.text}")
        StyledButton(onClick = { focus.requestFocus() }, backdrop = rememberLayerBackdrop()) { Text("입력 초점") }
        StyledButton(onClick = { checked = !checked }, backdrop = rememberLayerBackdrop()) { Text("상태 동기화") }
    }
}

@Composable
private fun FractionalFixture() {
    var value by rememberSaveable { mutableFloatStateOf(0.25f) }
    var changes by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)) {
        Text("샘플 변경: $changes")
        Text("소수 값: $value")
        OneUISlider(value, { value = it; changes++ }, 0f..1f, label = "소수 범위")
    }
}

@Composable
private fun PanelFixture() {
    var volume by rememberSaveable { mutableFloatStateOf(0.6f) }
    var mode by rememberSaveable { mutableStateOf(NoiseControlMode.TRANSPARENCY) }
    var conversation by rememberSaveable { mutableStateOf(true) }
    var changes by rememberSaveable { mutableIntStateOf(0) }
    Column(
        Modifier.fillMaxSize().background(Color(0xFF4E586C)).statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("샘플 변경: $changes", color = Color.White, modifier = Modifier.padding(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) {
            OneUIControlPanel(
                deviceName = "AirPods Pro 3",
                batteries = listOf(
                    Battery(BatteryComponent.LEFT, 0, BatteryStatus.CHARGING),
                    Battery(BatteryComponent.RIGHT, 80, BatteryStatus.CHARGING),
                    Battery(BatteryComponent.CASE, 100, BatteryStatus.NOT_CHARGING)
                ),
                volumeFraction = volume,
                onVolumeFractionChange = { volume = it; changes++ },
                onVolumeDragStateChange = {},
                availableModes = NoiseControlMode.entries,
                currentMode = mode,
                onModeSelected = { mode = it; changes++ },
                conversationalAwareness = conversation,
                onConversationalAwarenessChange = { conversation = it; changes++ },
                volumeSteps = 14
            )
        }
    }
}

@Composable
private fun ControlsFixture(design: AppDesign, showDialog: Boolean, onDesignChange: (AppDesign) -> Unit) {
    var checked by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableIntStateOf(3) }
    var changes by rememberSaveable { mutableIntStateOf(0) }
    var dialog by rememberSaveable { mutableStateOf(showDialog) }
    var sampleLevel by rememberSaveable { mutableFloatStateOf(25f) }
    val field = rememberTextFieldState("AirPods Pro 3")
    val focus = remember { FocusRequester() }
    StyledScaffold(title = "네이티브 UI 검증") {
        Column(
            Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
                .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(screenTopPadding()))
            Text("샘플 변경: $changes", color = MaterialTheme.colorScheme.onSurface)
            StyledList {
                StyledToggle(label = "알림", checked = checked, onCheckedChange = { checked = it; changes++ })
                StyledToggle(label = "비활성 설정", checked = true, enabled = false, onCheckedChange = { changes++ })
                StyledListItem(name = "선택 항목", selected = selected, onClick = { selected = true; changes++ })
                StyledListItem(name = "체크 항목", checked = checked, onClick = { checked = !checked; changes++ })
            }
            NoiseControlSettings(true, mode, onNoiseControlModeChanged = { mode = it; changes++ })
            StyledInputField(field, focus, label = "기기 이름")
            StyledSlider(label = "샘플 조절", value = sampleLevel, valueRange = 0f..100f, onValueChange = { sampleLevel = it; changes++ })
            StyledList {
                AppDesign.availableDesigns.forEach { item ->
                    StyledListItem(name = item.name, selected = design == item, onClick = { onDesignChange(item) })
                }
                StyledListItem(name = "대화상자 열기", onClick = { dialog = true })
            }
        }
    }
    if (design == AppDesign.Apple) ConfirmationDialog(
        showDialog = remember(dialog) { mutableStateOf(dialog) },
        title = "기기 이름",
        message = "큰 글씨와 키보드에서도 이름을 입력하고 저장할 수 있습니다.",
        confirmText = "저장",
        dismissText = "취소",
        onConfirm = { dialog = false; changes++ },
        onDismiss = { dialog = false },
        backdrop = rememberLayerBackdrop()
    )
    if (dialog && design != AppDesign.Apple) OneUIDialog(
        onDismissRequest = { dialog = false },
        title = "기기 이름",
        message = "큰 글씨와 키보드에서도 이름을 입력하고 저장할 수 있습니다.",
        buttons = listOf(
            OneUIDialogButton("취소", { dialog = false }),
            OneUIDialogButton("저장", { dialog = false; changes++ })
        )
    ) { OneUITextField(field, label = "기기 이름", modifier = Modifier.padding(horizontal = 24.dp)) }
}
