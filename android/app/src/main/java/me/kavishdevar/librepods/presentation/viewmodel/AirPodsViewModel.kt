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

package me.kavishdevar.librepods.presentation.viewmodel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.kavishdevar.librepods.bluetooth.AttWriteDebouncer
import me.kavishdevar.librepods.bluetooth.ListeningModeSelection
import me.kavishdevar.librepods.bluetooth.AttSettingState
import me.kavishdevar.librepods.bluetooth.CommandState
import me.kavishdevar.librepods.bluetooth.SettingPhase
import me.kavishdevar.librepods.data.CapabilityContext
import me.kavishdevar.librepods.data.CapabilityRegistry
import me.kavishdevar.librepods.data.AirPodsFeature
import me.kavishdevar.librepods.data.AudioDiagnosticsReader
import me.kavishdevar.librepods.BuildConfig
import me.kavishdevar.librepods.billing.BillingManager
import me.kavishdevar.librepods.bluetooth.AACPManager
import me.kavishdevar.librepods.bluetooth.AACPManager.Companion.ControlCommandIdentifiers
import me.kavishdevar.librepods.bluetooth.ATTHandles
import me.kavishdevar.librepods.bluetooth.BluetoothConnectionManager
import me.kavishdevar.librepods.bluetooth.ControlConnectionPhase
import me.kavishdevar.librepods.data.AirPodsInstance
import me.kavishdevar.librepods.data.AirPodsModels
import me.kavishdevar.librepods.data.AirPodsNotifications
import me.kavishdevar.librepods.data.Battery
import me.kavishdevar.librepods.data.BatteryComponent
import me.kavishdevar.librepods.data.BatteryStatus
import me.kavishdevar.librepods.data.Capability
import me.kavishdevar.librepods.data.ControlCommandRepository
import me.kavishdevar.librepods.data.CustomEq
import me.kavishdevar.librepods.data.HeartRateMonitor
import me.kavishdevar.librepods.data.HeartRateState
import me.kavishdevar.librepods.data.HeartRateStatus
import me.kavishdevar.librepods.data.StemAction
import me.kavishdevar.librepods.data.XposedRemotePrefProvider
import me.kavishdevar.librepods.services.AirPodsService

@Suppress("ArrayInDataClass")
data class AirPodsUiState(
    val deviceName: String = "AirPods",

    val isLocallyConnected: Boolean = false,
    val automaticReconnectPaused: Boolean = false,

    val instance: AirPodsInstance? = null,
    val capabilities: Set<Capability> = emptySet(),

    val controlStates: Map<ControlCommandIdentifiers, ByteArray> = emptyMap(),
    val commandStates: Map<Byte, CommandState> = emptyMap(),
    val attStates: Map<ATTHandles, AttSettingState> = emptyMap(),
    val offListeningMode: Boolean = true,

    val battery: List<Battery> = emptyList(),
    val ancMode: Int = 3,

    val modelName: String = "",
    val actualModel: String = "",
    val serialNumbers: List<String> = emptyList(),
    val version1: String = "",
    val version2: String = "",
    val version3: String = "",

    val headTrackingActive: Boolean = false,
    val headGesturesEnabled: Boolean = true,

    val heartRateAlert: Boolean = false,
    val heartRateAlertThreshold: Int = HeartRateMonitor.DEFAULT_ALERT_THRESHOLD,

    val eqData: FloatArray = floatArrayOf(),

    val automaticEarDetectionEnabled: Boolean = true,
    /** Per-bud wear status from the AirPods: 0 in ear, 1 out of ear, 2 in the case; empty if unknown. */
    val earStatus: List<Byte> = emptyList(),
    val automaticConnectionEnabled: Boolean = true,

    val leftAction: StemAction = StemAction.CYCLE_NOISE_CONTROL_MODES,
    val rightAction: StemAction = StemAction.CYCLE_NOISE_CONTROL_MODES,

    val loudSoundReductionEnabled: Boolean = false,
    val transparencyData: ByteArray = byteArrayOf(),
    val hearingAidData: ByteArray = byteArrayOf(),

    val isPremium: Boolean = false,
    val vendorIdHook: Boolean = false,
    val locationOptIn: Boolean = false,
    val audioOutputAvailable: Boolean? = null,
    val callPermissions: Boolean = false,

    val dynamicEndOfCharge: Boolean = false,

    val connectionSuccessful: Boolean = false,
    val timeUntilFOSSPremiumExpiry: Long = 0L,

    val customEq: CustomEq = CustomEq(1, 50, 50, 50) // disabled
) {
    val featureAvailability get() = CapabilityRegistry.resolve(CapabilityContext(
        modelKnown = instance != null, capabilities = capabilities,
        controlReady = isLocallyConnected, vendorIdHook = vendorIdHook, firmware = version3,
        locationOptIn = locationOptIn,
        audioOutputAvailable = audioOutputAvailable,
        callPermissions = callPermissions,
        commands = commandStates, att = attStates))
}

val demoInstance = AirPodsInstance(
    name = "[DEMO] AirPods Pro",
    model = AirPodsModels.getModelByModelNumber("A3064")!!,
    actualModelNumber = "A3064",
    serialNumber = "JXF9Q94A40",
    leftSerialNumber = "L-DEMO",
    rightSerialNumber = "R-DEMO",
    version1 = "90.3388000000000000.1786",
    version2 = "90.3388000000000000.1786",
    version3 = "9441861",
)

val demoState = AirPodsUiState(
    instance = demoInstance,
    deviceName = demoInstance.name,

    isLocallyConnected = true,

    capabilities = demoInstance.model.capabilities,

    battery = listOf(
        Battery(BatteryComponent.LEFT, 80, BatteryStatus.OPTIMIZED_CHARGING),
        Battery(BatteryComponent.RIGHT, 18, BatteryStatus.CHARGING),
        Battery(BatteryComponent.CASE, 76, BatteryStatus.NOT_CHARGING)
    ),

    ancMode = 3,
    offListeningMode = false,

    modelName = demoInstance.model.displayName,
    actualModel = demoInstance.actualModelNumber,
    serialNumbers =  listOf(
        demoInstance.serialNumber?: "",
        demoInstance.leftSerialNumber?: "",
        demoInstance.rightSerialNumber?: ""
    ),

    version1 = demoInstance.version1?: "",
    version2 = demoInstance.version2?: "",
    version3 = demoInstance.version3?: "",

    headTrackingActive = true,
    headGesturesEnabled = true,

    automaticEarDetectionEnabled = true,
    automaticConnectionEnabled = true,

    leftAction = StemAction.CYCLE_NOISE_CONTROL_MODES,
    rightAction = StemAction.DIGITAL_ASSISTANT,

    loudSoundReductionEnabled = true,

    isPremium = true,
    vendorIdHook = true,

    dynamicEndOfCharge = true,

    customEq = CustomEq(state = 2, low = 65, mid = 50, high = 70),

    controlStates = mapOf(
        ControlCommandIdentifiers.CONVERSATION_DETECT_CONFIG to byteArrayOf(0x01),
        ControlCommandIdentifiers.STEM_CONFIG to byteArrayOf(0x00),
        ControlCommandIdentifiers.CLICK_HOLD_INTERVAL to byteArrayOf(0x00),
        ControlCommandIdentifiers.DOUBLE_CLICK_INTERVAL to byteArrayOf(0x00),
        ControlCommandIdentifiers.VOLUME_SWIPE_INTERVAL to byteArrayOf(0x00),
        ControlCommandIdentifiers.VOLUME_SWIPE_MODE to byteArrayOf(0x01),
        ControlCommandIdentifiers.CALL_MANAGEMENT_CONFIG to byteArrayOf(0x00, 0x03),
        ControlCommandIdentifiers.CHIME_VOLUME to byteArrayOf(0x46, 0x50),
        ControlCommandIdentifiers.ADAPTIVE_VOLUME_CONFIG to byteArrayOf(0x01),
        ControlCommandIdentifiers.HEARING_AID to byteArrayOf(0x01, 0x02),
        ControlCommandIdentifiers.HPS_GAIN_SWIPE to byteArrayOf(0x01),
        ControlCommandIdentifiers.HEARING_ASSIST_CONFIG to byteArrayOf(0x02),
        ControlCommandIdentifiers.HRM_STATE to byteArrayOf(0x01),
        ControlCommandIdentifiers.AUTO_ANC_STRENGTH to byteArrayOf(0x45),
        ControlCommandIdentifiers.ONE_BUD_ANC_MODE to byteArrayOf(0x01),
        ControlCommandIdentifiers.SLEEP_DETECTION_CONFIG to byteArrayOf(0x01),
        ControlCommandIdentifiers.PPE_TOGGLE_CONFIG to byteArrayOf(0x01),
        ControlCommandIdentifiers.PPE_CAP_LEVEL_CONFIG to byteArrayOf(0x52),
        ControlCommandIdentifiers.DYNAMIC_END_OF_CHARGE to byteArrayOf(0x01),
        ControlCommandIdentifiers.LISTENING_MODE to byteArrayOf(0x04),
        // Transparency, Adaptive and Noise Cancellation in the long-press cycle.
        ControlCommandIdentifiers.LISTENING_MODE_CONFIGS to byteArrayOf(0x0E)
    )
)

class AirPodsViewModel(

) : ViewModel() {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var appContext: Context
    private lateinit var service: AirPodsService
    private lateinit var controlRepo: ControlCommandRepository

    var isReady by mutableStateOf(false)
        private set

    fun init(service: AirPodsService, controlRepo: ControlCommandRepository, sharedPreferences: SharedPreferences, appContext: Context) {
        this.service = service
        this.controlRepo = controlRepo
        this.sharedPreferences = sharedPreferences
        this.appContext = appContext

        observeBroadcasts()
        loadName()
        loadInstance()
        loadSharedPreferences()
        observeAACP()
        observeSettingResults()
        loadCurrentStatus()
        loadEq()
        loadATT()
        observeSharedPreferences()
        observeBilling()
        if (isDemoMode) activateDemoMode()
        isReady = true
    }

    private val _uiState = MutableStateFlow(AirPodsUiState())

    val uiState: StateFlow<AirPodsUiState> = _uiState

    private var isDemoMode = false

    private val listeners =
        mutableMapOf<ControlCommandIdentifiers, AACPManager.ControlCommandListener>()
    private val attWrites = AttWriteDebouncer(viewModelScope)
    private val listeningModes = ListeningModeSelection()
    private val controlWriteMutex = Mutex()

    private fun observeSettingResults() {
        viewModelScope.launch {
            service.automaticReconnectPaused.collect { paused ->
                if (!isDemoMode) _uiState.update { it.copy(automaticReconnectPaused = paused) }
            }
        }
        viewModelScope.launch {
            service.controlConnectionState.collect { connection ->
                if (!isDemoMode) _uiState.update { state ->
                    if (connection.phase == ControlConnectionPhase.READY)
                        state.copy(isLocallyConnected = true)
                    else state.copy(isLocallyConnected = false, controlStates = emptyMap(),
                        headTrackingActive = false, commandStates = emptyMap(), attStates = emptyMap())
                }
                if (connection.phase != ControlConnectionPhase.READY) {
                    attWrites.cancelAll()
                    listeningModes.reset()
                }
            }
        }
        viewModelScope.launch {
            service.aacpManager.commandTracker.states.collect { states ->
                states[ControlCommandIdentifiers.LISTENING_MODE_CONFIGS.value]?.let {
                    if (it.phase == SettingPhase.FAILED) listeningModes.failed(it.desired?.firstOrNull()?.toInt()?.and(0xff))
                }
                if (!isDemoMode) _uiState.update { it.copy(commandStates = states) }
            }
        }
        viewModelScope.launch {
            service.attManager.states.collect { states ->
                if (!isDemoMode) _uiState.update { current ->
                    current.copy(attStates = states,
                        loudSoundReductionEnabled = states[ATTHandles.LOUD_SOUND_REDUCTION]?.confirmed
                            ?.firstOrNull() == 1.toByte(),
                        hearingAidData = states[ATTHandles.HEARING_AID]?.confirmed?.toByteArray() ?: byteArrayOf(),
                        transparencyData = states[ATTHandles.TRANSPARENCY]?.confirmed?.toByteArray() ?: byteArrayOf())
                }
            }
        }
    }

    private val xposedRemotePref = XposedRemotePrefProvider.create()

    private lateinit var broadcastReceiver: BroadcastReceiver
    private var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

//    private val _cameraAction = MutableStateFlow(
//        sharedPreferences.getString("camera_action", null)
//            ?.let { value -> AACPManager.Companion.StemPressType.entries.find { it.name == value } })
//
//    val cameraAction: StateFlow<AACPManager.Companion.StemPressType?> = _cameraAction
//
//    fun setCameraAction(action: AACPManager.Companion.StemPressType?) {
//        sharedPreferences.edit {
//            if (action == null) remove("camera_action")
//            else putString("camera_action", action.name)
//        }
//        _cameraAction.value = action
//    }

    fun setCustomEq(low: Int, mid: Int, high: Int) {
        require(low in 0..100)
        require(mid in 0..100)
        require(high in 0..100)
        sendCustomEq(_uiState.value.customEq.copy(low = low, mid = mid, high = high))
    }

    fun setCustomEqEnabled(enabled: Boolean) {
        sendCustomEq(_uiState.value.customEq.copy(state = if (enabled) 2 else 1))
    }

    /** AirPods report custom EQ on connect but don't echo writes, so a delivered write is shown right away. */
    private fun sendCustomEq(desired: CustomEq) {
        if (desired == _uiState.value.customEq) return
        if (isDemoMode) _uiState.update { it.copy(customEq = desired) }
        else if (uiState.value.featureAvailability[AirPodsFeature.CUSTOM_EQ]?.canUse == true)
            sendToCurrentSession {
                if (service.aacpManager.sendCustomEqPacket(desired)) _uiState.update { it.copy(customEq = desired) }
            }
    }

    override fun onCleared() {
        listeners.forEach { (id, listener) ->
            controlRepo.remove(id, listener)
        }
        service.aacpManager.customEqCallback = null
        appContext.unregisterReceiver(broadcastReceiver)
        preferenceListener?.let(sharedPreferences::unregisterOnSharedPreferenceChangeListener)
        preferenceListener = null
    }

    private fun loadName() {
        val name = sharedPreferences.getString("name", "AirPods Pro")!!
        _uiState.update { it.copy(deviceName = name) }
    }

    private fun observeBilling() {
        if (isDemoMode) return
        viewModelScope.launch {
            BillingManager.provider.isPremium.collect { premium ->
                if (premium) {
                    sharedPreferences.edit {
                        remove("premium_expiry_time")
                        if (BuildConfig.PLAY_BUILD) remove("foss_upgraded")
                    }
                    _uiState.update { it.copy(isPremium = true, timeUntilFOSSPremiumExpiry = 0L) }
                } else {
                    if (_uiState.value.timeUntilFOSSPremiumExpiry <= 0L) {
                        setControlCommandBoolean(
                            ControlCommandIdentifiers.CONVERSATION_DETECT_CONFIG,
                            false
                        )
                        setHeadGesturesEnabled(false)
                        _uiState.update { it.copy(isPremium = false) }
                    }
                }
            }
        }
    }

    private fun observeSharedPreferences() {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "name" -> loadName()
                "off_listening_mode", "automatic_ear_detection", "automatic_connection_ctrl_cmd",
                "head_gestures", "left_long_press_action", "right_long_press_action",
                "dynamic_end_of_charge", "foss_upgraded", "premium_expiry_time" -> loadSharedPreferences()
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        preferenceListener = listener
    }

    private fun observeBroadcasts() {
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                if (!isDemoMode) when (action) {
                    // Controls become available only after the initialized session receives status.
                    AirPodsNotifications.AIRPODS_CONNECTED -> {
                        _uiState.update {
                            it.copy(isLocallyConnected = true)
                        }
                        // ATT has no notifications now, so refresh its values on every (re)connect.
                        loadATT()
                    }

                    AirPodsNotifications.AIRPODS_DISCONNECTED -> {
                        _uiState.update {
                            it.copy(isLocallyConnected = false)
                        }
                    }

                    AirPodsNotifications.BATTERY_DATA -> {
                        _uiState.update {
                            it.copy(battery = service.getBattery())
                        }
                    }

                    AirPodsNotifications.EAR_DETECTION_DATA -> {
                        val data = intent.getByteArrayExtra("data") ?: byteArrayOf()
                        _uiState.update { it.copy(earStatus = data.toList()) }
                    }

                    AirPodsNotifications.EQ_DATA -> {
                        val data = intent.getFloatArrayExtra("eqData") ?: floatArrayOf()

                        _uiState.update {
                            it.copy(eqData = data)
                        }
                    }

                    AirPodsNotifications.AIRPODS_INFORMATION_UPDATED -> {
                        loadInstance()
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(AirPodsNotifications.AIRPODS_CONNECTED)
            addAction(AirPodsNotifications.AIRPODS_DISCONNECTED)
            addAction(AirPodsNotifications.BATTERY_DATA)
            addAction(AirPodsNotifications.EQ_DATA)
            addAction(AirPodsNotifications.EAR_DETECTION_DATA)
            addAction(AirPodsNotifications.AIRPODS_INFORMATION_UPDATED)
        }

        appContext.registerReceiver(
            broadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED
        )
    }

    fun setControlCommandValue(
        identifier: ControlCommandIdentifiers, value: ByteArray
    ) {
        if (isDemoMode) {
            _uiState.update { it.copy(controlStates = it.controlStates + (identifier to value.copyOf())) }
            return
        }
        if (service.controlConnectionState.value.phase != ControlConnectionPhase.READY) return
        val desired = value.copyOf()
        if (identifier == ControlCommandIdentifiers.LISTENING_MODE_CONFIGS) {
            desired.firstOrNull()?.let { listeningModes.request(it.toInt() and 0xff) }
        }
        sendToCurrentSession {
            // Decide after earlier queued writes have begun. Checking before the queue would
            // discard a return to baseline while an older request is still waiting to be sent.
            if (identifier != ControlCommandIdentifiers.LISTENING_MODE ||
                !service.aacpManager.commandTracker.isConfirmedValue(identifier.value, desired))
                controlRepo.setValue(identifier, desired)
        }
    }

    private fun sendToCurrentSession(send: () -> Unit) {
        val session = service.controlConnectionState.value
        val socket = BluetoothConnectionManager.aacpSocket
        if (session.phase != ControlConnectionPhase.READY || socket == null) return
        // Acquire on the UI dispatcher in request order, before moving the blocking write to IO.
        viewModelScope.launch {
            controlWriteMutex.withLock {
                withContext(Dispatchers.IO) {
                    if (service.controlConnectionState.value == session && BluetoothConnectionManager.aacpSocket === socket)
                        BluetoothConnectionManager.withSessionSocket(socket, send)
                }
            }
        }
    }

    fun setControlCommandBoolean(
        identifier: ControlCommandIdentifiers, enabled: Boolean
    ) {
        setControlCommandValue(
            identifier, if (enabled) byteArrayOf(0x01) else byteArrayOf(0x02)
        )
    }

    fun setControlCommandInt(
        identifier: ControlCommandIdentifiers, value: Int
    ) {
        setControlCommandValue(identifier, byteArrayOf(value.toByte()))
    }

    fun setControlCommandByte(
        identifier: ControlCommandIdentifiers, value: Byte
    ) {
        setControlCommandValue(identifier, byteArrayOf(value))
    }

    fun observeControl(identifier: ControlCommandIdentifiers) {
        val listener = controlRepo.observe(identifier) { value ->
            if (identifier == ControlCommandIdentifiers.LISTENING_MODE_CONFIGS) {
                value.firstOrNull()?.let { listeningModes.receive(it.toInt() and 0xff) }
            }
            _uiState.update { state ->
                val current = state.controlStates[identifier]
                if (current?.contentEquals(value) == true) return@update state

                if (identifier == ControlCommandIdentifiers.DYNAMIC_END_OF_CHARGE) {
                    state.copy(
                        dynamicEndOfCharge = value.firstOrNull() == 0x01.toByte(),
                        controlStates = state.controlStates + (identifier to value)
                    )
                } else if (identifier == ControlCommandIdentifiers.AUTOMATIC_CONNECTION_CONFIG) {
                    state.copy(automaticConnectionEnabled = value.firstOrNull() == 0x01.toByte(),
                        controlStates = state.controlStates + (identifier to value.copyOf()))
                } else if (identifier == ControlCommandIdentifiers.ALLOW_OFF_OPTION) {
                    state.copy(offListeningMode = value.firstOrNull() == 0x01.toByte(),
                        controlStates = state.controlStates + (identifier to value.copyOf()))
                } else {
                    state.copy(
                        controlStates = state.controlStates + (identifier to value)
                    )
                }
            }
        }

        listeners[identifier] = listener
    }

    // I'm lazy, sorry.
    fun observeAACP() {
        val identifiersList = ControlCommandIdentifiers.entries
        for (identifier in identifiersList) {
            observeControl(identifier)
        }
        service.aacpManager.customEqCallback = { customEq ->
            _uiState.update { it.copy(customEq = customEq) }
        }
    }

    fun loadCurrentStatus() {
        if (isDemoMode) return
        service.let { service ->
            _uiState.update {
                it.copy(
                    isLocallyConnected = service.controlConnectionState.value.phase == ControlConnectionPhase.READY,
                    battery = service.getBattery(),
                    earStatus = service.earDetectionNotification.status.take(2),
                    ancMode = controlRepo.getValue(ControlCommandIdentifiers.LISTENING_MODE)?.get(0)?.toInt() ?: 1,
                    controlStates = controlRepo.getMap()
                )
            }
        }
    }

    private fun loadSharedPreferences() {
        // Kept in step with the AirPods' own setting by the service; off until they report it, as on iOS.
        val offListeningModeEnabled = sharedPreferences.getBoolean("off_listening_mode", false)
        val automaticEarDetectionEnabled =
            sharedPreferences.getBoolean("automatic_ear_detection", true)
        val automaticConnectionEnabled =
            sharedPreferences.getBoolean("automatic_connection_ctrl_cmd", true)
        val headGesturesEnabled = sharedPreferences.getBoolean("head_gestures", true)
        val leftAction = StemAction.valueOf(
            sharedPreferences.getString(
                "left_long_press_action",
                "CYCLE_NOISE_CONTROL_MODES"
            ) ?: "CYCLE_NOISE_CONTROL_MODES"
        )
        val rightAction = StemAction.valueOf(
            sharedPreferences.getString(
                "right_long_press_action",
                "CYCLE_NOISE_CONTROL_MODES"
            ) ?: "CYCLE_NOISE_CONTROL_MODES"
        )
        val vendorIdHook = xposedRemotePref.getBoolean("vendor_id_hook", false)
        val dynamicEndOfCharge = sharedPreferences.getBoolean("dynamic_end_of_charge", false)

        val connectionSuccessful = sharedPreferences.getBoolean("connection_successful", false)
        val heartRateAlert = sharedPreferences.getBoolean(AirPodsService.HEART_RATE_ALERT_KEY, false)
        val heartRateAlertThreshold = sharedPreferences.getInt(
            AirPodsService.HEART_RATE_ALERT_THRESHOLD_KEY, HeartRateMonitor.DEFAULT_ALERT_THRESHOLD
        ).coerceIn(HeartRateMonitor.ALERT_RANGE)

        _uiState.update {
            it.copy(
                heartRateAlert = heartRateAlert,
                heartRateAlertThreshold = heartRateAlertThreshold,
                offListeningMode = offListeningModeEnabled,
                automaticEarDetectionEnabled = automaticEarDetectionEnabled,
                automaticConnectionEnabled = automaticConnectionEnabled,
                headGesturesEnabled = headGesturesEnabled,
                leftAction = leftAction,
                rightAction = rightAction,
                vendorIdHook = vendorIdHook,
                dynamicEndOfCharge = dynamicEndOfCharge,
                connectionSuccessful = connectionSuccessful,
            )
        }

        // faulty update on Play caused PLAY_BUILD to be false and resulted in use of FOSS billing in Play. since FOSS is not verified, we need to give 2 weeks to verify the purchase
        if (BuildConfig.PLAY_BUILD) {
            val fossUpgraded = sharedPreferences.getBoolean("foss_upgraded", false)
            val expiryTime = sharedPreferences.getLong("premium_expiry_time", 0L)
            val now = System.currentTimeMillis()

            when {
                // existing temporary premium
                expiryTime > 0L -> {
                    if (expiryTime <= now) {
                        sharedPreferences.edit {
                            remove("premium_expiry_time")
                            remove("foss_upgraded")
                        }

                        _uiState.update {
                            it.copy(
                                timeUntilFOSSPremiumExpiry = 0L,
                                isPremium = false
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                timeUntilFOSSPremiumExpiry = expiryTime - now,
                                isPremium = true
                            )
                        }
                    }
                }

                // First migration from accidental FOSS Play build
                fossUpgraded && !_uiState.value.isPremium -> {
                    val newExpiry = now + 28L * 24 * 60 * 60 * 1000

                    sharedPreferences.edit {
                        putLong("premium_expiry_time", newExpiry)
                    }

                    _uiState.update {
                        it.copy(
                            timeUntilFOSSPremiumExpiry = newExpiry - now,
                            isPremium = true
                        )
                    }
                }
            }
        }
    }

    fun setOffListeningMode(enabled: Boolean) {
        setControlCommandBoolean(ControlCommandIdentifiers.ALLOW_OFF_OPTION, enabled)
    }

    fun setHeadGesturesEnabled(enabled: Boolean) {
        sharedPreferences.edit { putBoolean("head_gestures", enabled) }
        _uiState.update {
            it.copy(headGesturesEnabled = enabled)
        }
    }

    fun setDynamicEndOfCharge(enabled: Boolean) {
        setControlCommandBoolean(ControlCommandIdentifiers.DYNAMIC_END_OF_CHARGE, enabled)
    }

    private fun loadEq() {
        _uiState.update {
            it.copy(
                customEq = service.aacpManager.customEq
            )
        }
    }

    private fun loadInstance() {
        val instance = service.airpodsInstance ?: return

        _uiState.update {
            it.copy(
                capabilities = instance.model.capabilities,
                instance = instance,
                modelName = instance.model.displayName,
                actualModel = instance.actualModelNumber,
                serialNumbers = listOf(
                    instance.serialNumber ?: "",
                    instance.leftSerialNumber ?: "",
                    instance.rightSerialNumber ?: ""
                ),
                version1 = instance.version1 ?: "",
                version2 = instance.version2 ?: "",
                version3 = instance.version3 ?: ""
            )
        }
    }

    fun reconnectFromSavedMac() {
        service.reconnectFromSavedMac()
    }

    fun readAudioDiagnostics() = AudioDiagnosticsReader.read(appContext,
        sharedPreferences.getString("mac_address", "") ?: "").also {
        _uiState.update { state -> state.copy(locationOptIn =
            me.kavishdevar.librepods.data.LastConnectionLocationStore.enabled(appContext),
            callPermissions = appContext.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                appContext.checkSelfPermission(android.Manifest.permission.ANSWER_PHONE_CALLS) == android.content.pm.PackageManager.PERMISSION_GRANTED,
            audioOutputAvailable = it.selectedMusicOutputAvailable) }
    }

    fun refreshCallPermissions() { service.refreshCallStateMonitoring(); readAudioDiagnostics() }

    fun setName(name: String) {
        if (isDemoMode) _uiState.update { it.copy(deviceName = name) }
        else sendToCurrentSession { service.setName(name) }
    }

    fun startHeadTracking() {
        service.startHeadTracking()
        _uiState.update { it.copy(headTrackingActive = true) }
    }

    fun stopHeadTracking() {
        service.stopHeadTracking()
        _uiState.update { it.copy(headTrackingActive = false) }
    }

    val heartRate: StateFlow<HeartRateState> = HeartRateMonitor.state

    fun startHeartRate(): Boolean = !isDemoMode && service.startHeartRate()

    fun stopHeartRate() {
        if (!isDemoMode) service.stopHeartRate()
    }

    /** The alert keeps the sensor on while connected, so turning it on also starts measuring. */
    fun setHeartRateAlert(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(AirPodsService.HEART_RATE_ALERT_KEY, enabled) }
        _uiState.update { it.copy(heartRateAlert = enabled) }
        if (enabled && heartRate.value.status == HeartRateStatus.OFF) startHeartRate()
    }

    fun setHeartRateAlertThreshold(bpm: Int) {
        val value = bpm.coerceIn(HeartRateMonitor.ALERT_RANGE)
        sharedPreferences.edit { putInt(AirPodsService.HEART_RATE_ALERT_THRESHOLD_KEY, value) }
        _uiState.update { it.copy(heartRateAlertThreshold = value) }
    }

    fun setATTCharacteristicValue(handle: ATTHandles, value: ByteArray) {
        if (isDemoMode) {
            _uiState.update { when (handle) {
                ATTHandles.LOUD_SOUND_REDUCTION -> it.copy(loudSoundReductionEnabled = value.firstOrNull() == 1.toByte())
                ATTHandles.HEARING_AID -> it.copy(hearingAidData = value.copyOf())
                ATTHandles.TRANSPARENCY -> it.copy(transparencyData = value.copyOf())
            } }
            return
        }
        if (value.isEmpty() || !uiState.value.vendorIdHook || !uiState.value.isLocallyConnected) return
        val session = service.controlConnectionState.value
        val attSession = service.attManager.sessionId
        attWrites.submit(handle, value, uiState.value.attStates[handle]) { desired ->
            withContext(Dispatchers.IO) {
                if (service.controlConnectionState.value == session)
                    service.attManager.writeCharacteristic(handle, desired, expectedSession = attSession)
            }
        }
    }

    /** Each ATT read opens its own channel, so this runs off the main thread and keeps values it couldn't read. */
    fun loadATT() {
        if (isDemoMode || !uiState.value.vendorIdHook ||
            service.controlConnectionState.value.phase != ControlConnectionPhase.READY) return
        viewModelScope.launch(Dispatchers.IO) {
            ATTHandles.entries.forEach { service.attManager.readCharacteristic(it) }
        }
    }

    fun setAutomaticEarDetectionEnabled(enabled: Boolean) {
        sharedPreferences.edit { putBoolean("automatic_ear_detection", enabled) }
        setControlCommandBoolean(ControlCommandIdentifiers.EAR_DETECTION_CONFIG, enabled)
        _uiState.update {
            it.copy(
                automaticEarDetectionEnabled = enabled
            )
        }
    }

    fun setAutomaticConnectionEnabled(enabled: Boolean) {
        if (!isDemoMode && service.controlConnectionState.value.phase == ControlConnectionPhase.READY)
            sharedPreferences.edit { putBoolean("automatic_connection_ctrl_cmd", enabled) }
        setControlCommandBoolean(ControlCommandIdentifiers.AUTOMATIC_CONNECTION_CONFIG, enabled)
    }

    fun activateDemoMode() {
        isDemoMode = true
        _uiState.update {demoState}
    }

    fun sendPhoneMediaEQ(eq: FloatArray, phoneByte: Byte, mediaByte: Byte) {
        if (!isDemoMode && eq.size == 8 && eq.all { it.isFinite() })
            sendToCurrentSession { service.aacpManager.sendPhoneMediaEQ(eq.copyOf(), phoneByte, mediaByte) }
    }

    fun setLongPressAction(side: String, action: StemAction) {
        val prefKey = if (side.lowercase() == "left") "left_long_press_action" else "right_long_press_action"
        sharedPreferences.edit { putString(prefKey, action.name) }
        _uiState.update {
            if (side.lowercase() == "left") it.copy(leftAction = action) else it.copy(rightAction = action)
        }
    }

    fun toggleListeningMode(modeBit: Int) {
        val currentByte = uiState.value.controlStates[ControlCommandIdentifiers.LISTENING_MODE_CONFIGS]?.get(0)?.toInt() ?: 0
        val newValue = listeningModes.toggle(currentByte, modeBit)
        setControlCommandByte(ControlCommandIdentifiers.LISTENING_MODE_CONFIGS, newValue.toByte())
        if (!isDemoMode) sharedPreferences.edit { putInt("long_press_byte", newValue) }
    }

    fun disconnect() {
        if (isDemoMode) {
            isDemoMode = false
            _uiState.update {
                it.copy(isLocallyConnected = false)
            }
        } else {
            service.disconnectAirPods()
            if (appContext.checkSelfPermission("android.permission.BLUETOOTH_PRIVILEGED") != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(
                    appContext, "App has disconnected, disconnect from Android Settings.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
