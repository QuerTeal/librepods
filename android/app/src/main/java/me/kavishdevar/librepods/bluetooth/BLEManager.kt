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

package me.kavishdevar.librepods.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import me.kavishdevar.librepods.utils.BluetoothCryptography
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.collections.iterator

/**
 * Manager for Bluetooth Low Energy scanning operations specifically for AirPods
 */
class BLEManager(private val context: Context) {

    data class AirPodsStatus(
        val address: String,
        val lastSeen: Long = SystemClock.elapsedRealtime(),
        val paired: Boolean = false,
        val model: String = "Unknown",
        val leftBattery: Int? = null,
        val rightBattery: Int? = null,
        val caseBattery: Int? = null,
        val isLeftInEar: Boolean = false,
        val isRightInEar: Boolean = false,
        val isLeftCharging: Boolean = false,
        val isRightCharging: Boolean = false,
        val isCaseCharging: Boolean = false,
        val lidOpen: Boolean? = null,
        val isCaseAdvertisement: Boolean = false,
        val caseOpenCounter: Int? = null,
        val color: String = "Unknown",
        val connectionState: String = "Unknown"
    )

    fun getMostRecentStatus(): AirPodsStatus? {
        return deviceStatusMap.values.maxByOrNull { it.lastSeen }
    }

    fun getMostRecentCaseStatus(): AirPodsStatus? =
        deviceStatusMap.values.filter { it.isCaseAdvertisement }.maxByOrNull { it.lastSeen }

    fun getMostRecentPodStatus(): AirPodsStatus? =
        mostRecentPodStatus(deviceStatusMap.values)

    interface AirPodsStatusListener {
        fun onDeviceStatusChanged(device: AirPodsStatus, previousStatus: AirPodsStatus?)
        fun onBroadcastFromNewAddress(device: AirPodsStatus)
        fun onLidStateChanged(lidOpen: Boolean)
        fun onLidStateUnknown() {}
        fun onEarStateChanged(device: AirPodsStatus, leftInEar: Boolean, rightInEar: Boolean)
        fun onBatteryChanged(device: AirPodsStatus)
        fun onDeviceDisappeared()
    }

    private var mBluetoothLeScanner: BluetoothLeScanner? = null
    private var mScanCallback: ScanCallback? = null
    private var airPodsStatusListener: AirPodsStatusListener? = null
    private val deviceStatusMap = mutableMapOf<String, AirPodsStatus>()
    private val verifiedAddresses = mutableSetOf<String>()
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val lidTracker = LidStateTracker(LID_FRESHNESS_MS)
    private var trustedIrk: ByteArray? = null
    private var trustedEncryptionKey: ByteArray? = null
    private var lastCaseSeenMillis: Long? = null
    private var lastScanTimestampNanos = 0L
    private var scanningRequested = false
    private var scanFailures = 0
    private val modelNames = mapOf(
        0x0E20 to "AirPods Pro",
        0x1420 to "AirPods Pro 2",
        0x2420 to "AirPods Pro 2 (USB-C)",
        0x2720 to "AirPods Pro 3",
        0x0220 to "AirPods 1",
        0x0F20 to "AirPods 2",
        0x1320 to "AirPods 3",
        0x1920 to "AirPods 4",
        0x1B20 to "AirPods 4 (ANC)",
        0x0A20 to "AirPods Max",
        0x1F20 to "AirPods Max (USB-C)"
    )

    val colorNames = mapOf(
        0x00 to "White", 0x01 to "Black", 0x02 to "Red", 0x03 to "Blue",
        0x04 to "Pink", 0x05 to "Gray", 0x06 to "Silver", 0x07 to "Gold",
        0x08 to "Rose Gold", 0x09 to "Space Gray", 0x0A to "Dark Blue",
        0x0B to "Light Blue", 0x0C to "Yellow"
    )

    val connStates = mapOf(
        0x00 to "Disconnected", 0x04 to "Idle", 0x05 to "Music",
        0x06 to "Call", 0x07 to "Ringing", 0x09 to "Hanging Up", 0xFF to "Unknown"
    )

    private val cleanupHandler = Handler(Looper.getMainLooper())
    private val cleanupRunnable = object : Runnable {
        override fun run() {
            cleanupStaleDevices()
            cleanupHandler.postDelayed(this, CLEANUP_INTERVAL_MS)
        }
    }
    private val lidExpiryRunnable = Runnable {
        if (lidTracker.expire(SystemClock.elapsedRealtime())) {
            airPodsStatusListener?.onLidStateUnknown()
        }
    }
    private val scanRetryRunnable = Runnable { if (scanningRequested) startScanning() }

    /** Low duty cycle while the screen is off; nobody can see a popup then. */
    private var lowPower = false
    private val lowPowerRunnable = Runnable { applyLowPower(true) }

    /**
     * Full duty cycle as soon as the screen turns on, so a lid opened now is seen within an
     * advertising interval or two. Dropping to low power waits a while, which keeps screen toggling
     * under Android's limit of five scan starts per 30 seconds.
     */
    fun setInteractive(interactive: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            cleanupHandler.post { setInteractive(interactive) }
            return
        }
        cleanupHandler.removeCallbacks(lowPowerRunnable)
        if (interactive) applyLowPower(false)
        else cleanupHandler.postDelayed(lowPowerRunnable, SCREEN_OFF_LOW_POWER_DELAY_MS)
    }

    @SuppressLint("MissingPermission")
    private fun applyLowPower(low: Boolean) {
        if (lowPower == low) return
        lowPower = low
        // Sparse advertisements at low duty must not read as the case going quiet. Going back to
        // full duty, the short window returns with the next advertisement instead of right away,
        // so a case left open doesn't expire and pop up again just because the screen turned on.
        if (low) lidTracker.freshnessMillis = LOW_POWER_LID_FRESHNESS_MS
        val callback = mScanCallback ?: return
        Log.d(TAG, "Restarting BLE scan in ${if (low) "low power" else "low latency"} mode")
        try { mBluetoothLeScanner?.stopScan(callback) } catch (_: Exception) { }
        mScanCallback = null
        mBluetoothLeScanner = null
        startScanning()
    }

    fun setAirPodsStatusListener(listener: AirPodsStatusListener) {
        airPodsStatusListener = listener
    }

    @SuppressLint("MissingPermission")
    fun startScanning() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            cleanupHandler.post { startScanning() }
            return
        }
        scanningRequested = true
        if (mScanCallback != null) return
        cleanupHandler.removeCallbacks(scanRetryRunnable)
        try {
            Log.d(TAG, "Starting BLE scanner")

            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val btAdapter = btManager.adapter

            if (btAdapter == null) {
                Log.d(TAG, "No Bluetooth adapter available")
                return
            }

            if (!btAdapter.isEnabled) {
                Log.d(TAG, "Bluetooth is disabled")
                return
            }

            mBluetoothLeScanner = btAdapter.bluetoothLeScanner

            val scanSettings = ScanSettings.Builder()
                .setScanMode(if (lowPower) ScanSettings.SCAN_MODE_LOW_POWER else ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                .setReportDelay(0L)
                // Newer cases may use extended advertising; report it alongside legacy packets.
                .setLegacy(false)
                .setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
                .build()

            // Keep the Galaxy-compatible broad Apple filter; validate type and length in the parser.
            val scanFilter = ScanFilter.Builder()
                .setManufacturerData(76, byteArrayOf())
                .build()

            mScanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    if (mScanCallback !== this) return
                    scanFailures = 0
                    processScanResult(result)
                }

                override fun onBatchScanResults(results: List<ScanResult>) {
                    if (mScanCallback !== this) return
                    for (result in results) {
                        processScanResult(result)
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.e(TAG, "BLE scan failed with error code: $errorCode")
                    if (mScanCallback !== this) return
                    try { mBluetoothLeScanner?.stopScan(this) } catch (_: Exception) { }
                    mScanCallback = null
                    mBluetoothLeScanner = null
                    if (errorCode != SCAN_FAILED_FEATURE_UNSUPPORTED && scanningRequested) {
                        val retryDelay = if (errorCode == 6) 30_000L
                            else (1000L shl scanFailures.coerceAtMost(5)).coerceAtMost(30_000L)
                        scanFailures++
                        cleanupHandler.removeCallbacks(scanRetryRunnable)
                        cleanupHandler.postDelayed(scanRetryRunnable, retryDelay)
                    }
                }
            }

            mBluetoothLeScanner?.startScan(listOf(scanFilter), scanSettings, mScanCallback)
            Log.d(TAG, "BLE scanner started successfully")

            cleanupHandler.removeCallbacks(cleanupRunnable)
            cleanupHandler.postDelayed(cleanupRunnable, CLEANUP_INTERVAL_MS)
        } catch (t: Throwable) {
            mScanCallback = null
            mBluetoothLeScanner = null
            Log.e(TAG, "Error starting BLE scanner", t)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            cleanupHandler.post { stopScanning() }
            return
        }
        scanningRequested = false
        cleanupHandler.removeCallbacks(scanRetryRunnable)
        try {
            if (mBluetoothLeScanner != null && mScanCallback != null) {
                Log.d(TAG, "Stopping BLE scanner")
                mBluetoothLeScanner?.stopScan(mScanCallback)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error stopping BLE scanner", t)
        } finally {
            mScanCallback = null
            mBluetoothLeScanner = null
            cleanupHandler.removeCallbacks(cleanupRunnable)
            cleanupHandler.removeCallbacks(lidExpiryRunnable)
            deviceStatusMap.clear()
            verifiedAddresses.clear()
            trustedIrk = null
            trustedEncryptionKey = null
            lastCaseSeenMillis = null
            lastScanTimestampNanos = 0L
            lidTracker.reset()
            airPodsStatusListener?.onLidStateUnknown()
        }
    }

    private fun getEncryptionKeyFromPreferences(): ByteArray? =
        me.kavishdevar.librepods.data.SecureProximityKeyStore.get(context,
            AACPManager.Companion.ProximityKeyType.ENC_KEY)


    @SuppressLint("GetInstance")
    private fun decryptLastBytes(data: ByteArray, key: ByteArray): ByteArray? {
        return try {
            if (data.size < 16) {
                return null
            }

            val block = data.copyOfRange(data.size - 16, data.size)
            val cipher = Cipher.getInstance("AES/ECB/NoPadding")
            val secretKey = SecretKeySpec(key, "AES")
            cipher.init(Cipher.DECRYPT_MODE, secretKey)
            cipher.doFinal(block)
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting data", e)
            null
        }
    }

    private fun formatBattery(byteVal: Int): Pair<Boolean, Int?> {
        val charging = (byteVal and 0x80) != 0
        val level = byteVal and 0x7F
        return Pair(charging, level.takeIf { it in 0..100 })
    }

    // Diagnostics: what the scanner heard from AirPods, summarised every 30 s.
    private val heardTypes = mutableMapOf<String, Int>()
    private var heardCase = 0
    private var heardPods = 0
    private var caseUndecoded = 0
    private var podsUnverified = 0
    private var lastSummaryMillis = 0L

    private fun summarize() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastSummaryMillis < 30_000) return
        lastSummaryMillis = now
        if (heardCase + heardPods > 0) Log.d(TAG, "Heard in 30 s: case=$heardCase (undecoded $caseUndecoded), " +
            "pods=$heardPods (unverified $podsUnverified); Apple types ${heardTypes.entries.sortedByDescending { it.value }.take(4).joinToString { "${it.key}x${it.value}" }}")
        heardTypes.clear(); heardCase = 0; heardPods = 0; caseUndecoded = 0; podsUnverified = 0
    }

    private fun processScanResult(result: ScanResult) {
        try {
            val scanRecord = result.scanRecord ?: return
            val address = result.device.address

            val manufacturerData = scanRecord.getManufacturerSpecificData(76) ?: return
            val fromCase = CaseAdvertisementParser.isCaseAdvertisement(manufacturerData)
            val fromPods = manufacturerData.size == 27 && manufacturerData[0] == 7.toByte() &&
                manufacturerData[1] == 25.toByte() && manufacturerData[2] == 1.toByte()
            val typeKey = "%02x/%d".format(manufacturerData.getOrNull(0)?.toInt()?.and(0xff) ?: -1, manufacturerData.size)
            heardTypes[typeKey] = (heardTypes[typeKey] ?: 0) + 1
            if (fromCase) heardCase++
            if (fromPods) heardPods++
            summarize()
            if (!fromCase && !fromPods) return

            val irk = getIrkFromPreferences()
            val encryptionKey = getEncryptionKeyFromPreferences()
            if (!fromCase && irk == null) {
                Log.d(TAG, "Proximity advertisement before the identity key is loaded")
                return
            }
            if ((irk != null && trustedIrk != null && trustedIrk?.contentEquals(irk) != true) ||
                (encryptionKey != null && trustedEncryptionKey != null &&
                    trustedEncryptionKey?.contentEquals(encryptionKey) != true)) {
                verifiedAddresses.clear()
                deviceStatusMap.clear()
                lidTracker.reset()
                lastCaseSeenMillis = null
                lastScanTimestampNanos = 0L
            }
            if (irk != null) trustedIrk = irk.copyOf()
            if (encryptionKey != null) trustedEncryptionKey = encryptionKey.copyOf()

            val caseReport = if (fromCase) {
                if (encryptionKey == null) { caseUndecoded++; return }
                CaseAdvertisementParser.parse(manufacturerData, encryptionKey) ?: run {
                    caseUndecoded++
                    if (caseUndecoded == 1) decryptLastBytes(manufacturerData.copyOfRange(3, 19), encryptionKey)?.let {
                        Log.d(TAG, "Case advertisement did not decode; block ${it.joinToString(" ") { b -> "%02x".format(b) }}")
                    }
                    return
                }
            } else null

            if (!fromCase && !verifiedAddresses.contains(address)) {
                if (!BluetoothCryptography.verifyRPA(address, irk!!)) {
                    podsUnverified++
                    return
                }
                if (verifiedAddresses.size >= 64) verifiedAddresses.clear()
                verifiedAddresses.add(address)
                Log.d(TAG, "RPA verified and added to trusted list: $address")
            }

            if (result.timestampNanos < lastScanTimestampNanos) return
            lastScanTimestampNanos = result.timestampNanos
            val seenMillis = result.timestampNanos / 1_000_000L
            if (SystemClock.elapsedRealtime() - seenMillis >= lidTracker.freshnessMillis) return
            if (!lowPower) lidTracker.freshnessMillis = LID_FRESHNESS_MS

            val parsedStatus = (if (caseReport != null) {
                AirPodsStatus(
                    address = address,
                    model = "AirPods Pro 3",
                    leftBattery = caseReport.left.level,
                    rightBattery = caseReport.right.level,
                    caseBattery = caseReport.case.level,
                    isLeftCharging = caseReport.left.charging,
                    isRightCharging = caseReport.right.charging,
                    isCaseCharging = caseReport.case.charging,
                    lidOpen = caseReport.lidOpen,
                    isCaseAdvertisement = true,
                    caseOpenCounter = caseReport.lidCounter
                )
            } else {
                val decryptedData = encryptionKey?.let { decryptLastBytes(manufacturerData, it) }
                if (decryptedData != null && decryptedData.size == 16) {
                    parseProximityMessageWithDecryption(address, manufacturerData, decryptedData)
                } else {
                    parseProximityMessage(address, manufacturerData)
                }
            }).copy(lastSeen = seenMillis)

            val previousStatus = deviceStatusMap[address]
            deviceStatusMap[address] = parsedStatus
            if (fromCase) {
                lastCaseSeenMillis = seenMillis
                if (previousStatus == null || previousStatus.lidOpen != parsedStatus.lidOpen ||
                    previousStatus.leftBattery != parsedStatus.leftBattery ||
                    previousStatus.rightBattery != parsedStatus.rightBattery ||
                    previousStatus.caseBattery != parsedStatus.caseBattery ||
                    previousStatus.isLeftCharging != parsedStatus.isLeftCharging ||
                    previousStatus.isRightCharging != parsedStatus.isRightCharging ||
                    previousStatus.isCaseCharging != parsedStatus.isCaseCharging) {
                    Log.i(TAG, "Selected case decoded: lid=${parsedStatus.lidOpen}, " +
                        "left=${parsedStatus.leftBattery}, right=${parsedStatus.rightBattery}, case=${parsedStatus.caseBattery}, " +
                        "leftCharging=${parsedStatus.isLeftCharging}, rightCharging=${parsedStatus.isRightCharging}")
                }
            }

            airPodsStatusListener?.let { listener ->
                if (previousStatus == null) {
                    listener.onBroadcastFromNewAddress(parsedStatus)
                    Log.d(TAG, "New AirPods device detected: $address")

                } else {
                    if (parsedStatus != previousStatus) {
                        listener.onDeviceStatusChanged(parsedStatus, previousStatus)
                    }

                    if (!fromCase && (parsedStatus.isLeftInEar != previousStatus.isLeftInEar ||
                        parsedStatus.isRightInEar != previousStatus.isRightInEar)) {
                        listener.onEarStateChanged(
                            parsedStatus,
                            parsedStatus.isLeftInEar,
                            parsedStatus.isRightInEar
                        )
                        Log.d(TAG, "Ear state changed - Left: ${parsedStatus.isLeftInEar}, Right: ${parsedStatus.isRightInEar}")
                    }

                    if (parsedStatus.leftBattery != previousStatus.leftBattery ||
                        parsedStatus.rightBattery != previousStatus.rightBattery ||
                        parsedStatus.caseBattery != previousStatus.caseBattery ||
                        parsedStatus.isLeftCharging != previousStatus.isLeftCharging ||
                        parsedStatus.isRightCharging != previousStatus.isRightCharging ||
                        parsedStatus.isCaseCharging != previousStatus.isCaseCharging) {
                        listener.onBatteryChanged(parsedStatus)
                        Log.d(TAG, "Battery changed - Left: ${parsedStatus.leftBattery}, Right: ${parsedStatus.rightBattery}, Case: ${parsedStatus.caseBattery}")
                    }
                }
                // Once the selected Pro 3 case is decoded, it is the authoritative lid source.
                // Falling back to pod bytes during sparse case intervals would repeat popups.
                val lid = parsedStatus.lidOpen
                val caseHasPriority = !fromCase && lastCaseSeenMillis != null
                if (lid != null && !caseHasPriority) {
                    when (lidTracker.observe(lid, seenMillis, caseReport?.lidCounter)) {
                        LidState.OPEN -> {
                            Log.i(TAG, "Lid open seen ${SystemClock.elapsedRealtime() - seenMillis}ms after the advertisement" +
                                if (lowPower) " (low power scan)" else "")
                            listener.onLidStateChanged(true)
                        }
                        LidState.CLOSED -> listener.onLidStateChanged(false)
                        else -> Unit
                    }
                    cleanupHandler.removeCallbacks(lidExpiryRunnable)
                    cleanupHandler.postDelayed(lidExpiryRunnable,
                        (lidTracker.freshnessMillis - (SystemClock.elapsedRealtime() - seenMillis)).coerceAtLeast(1L))
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error processing scan result", t)
        }
    }

    private fun parseProximityMessageWithDecryption(address: String, data: ByteArray, decrypted: ByteArray): AirPodsStatus {
        val paired = data[2].toInt() == 1
        val modelId = ((data[3].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF)
        val model = modelNames[modelId] ?: "Unknown ($modelId)"

        val status = data[5].toInt() and 0xFF
//        val flagsCase = data[7].toInt() and 0xFF
        val lid = data[8].toInt() and 0xFF
        val color = colorNames[data[9].toInt()] ?: "Unknown"
        val conn = connStates[data[10].toInt()] ?: "Unknown (${data[10].toInt()})"

        val primaryLeft = ((status shr 5) and 0x01) == 1
        val thisInCase = ((status shr 6) and 0x01) == 1
        val xorFactor = primaryLeft xor thisInCase

        val isLeftInEar = if (xorFactor) (status and 0x08) != 0 else (status and 0x02) != 0
        val isRightInEar = if (xorFactor) (status and 0x02) != 0 else (status and 0x08) != 0

        val isFlipped = !primaryLeft

        val leftByteIndex = if (isFlipped) 2 else 1
        val rightByteIndex = if (isFlipped) 1 else 2

        val (isLeftCharging, leftBattery) = formatBattery(decrypted[leftByteIndex].toInt() and 0xFF)
        val (isRightCharging, rightBattery) = formatBattery(decrypted[rightByteIndex].toInt() and 0xFF)

        val rawCaseBatteryByte = decrypted[3].toInt() and 0xFF
        val (isCaseCharging, rawCaseBattery) = formatBattery(rawCaseBatteryByte)

        val caseBattery = rawCaseBattery

        val lidOpen = if (thisInCase) ((lid shr 3) and 0x01) == 0 else null

        return AirPodsStatus(
            address = address,
            lastSeen = SystemClock.elapsedRealtime(),
            paired = paired,
            model = model,
            leftBattery = leftBattery,
            rightBattery = rightBattery,
            caseBattery = caseBattery,
            isLeftInEar = isLeftInEar,
            isRightInEar = isRightInEar,
            isLeftCharging = isLeftCharging,
            isRightCharging = isRightCharging,
            isCaseCharging = isCaseCharging,
            lidOpen = lidOpen,
            color = color,
            connectionState = conn
        )
    }

    private fun cleanupStaleDevices() {
        val now = SystemClock.elapsedRealtime()
        val staleCutoff = now - STALE_DEVICE_TIMEOUT_MS
        val hadDevices = deviceStatusMap.isNotEmpty()

        val staleDevices = deviceStatusMap.filter { it.value.lastSeen < staleCutoff }

        for (device in staleDevices) {
            deviceStatusMap.remove(device.key)
            Log.d(TAG, "Removed stale device from tracking: ${device.key}")
        }

        if (hadDevices && deviceStatusMap.isEmpty()) {
            airPodsStatusListener?.onDeviceDisappeared()
        }
    }

    private fun getIrkFromPreferences(): ByteArray? =
        me.kavishdevar.librepods.data.SecureProximityKeyStore.get(context,
            AACPManager.Companion.ProximityKeyType.IRK)


    private fun parseProximityMessage(address: String, data: ByteArray): AirPodsStatus {
        val paired = data[2].toInt() == 1
        val modelId = ((data[3].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF)
        val model = modelNames[modelId] ?: "Unknown ($modelId)"

        val status = data[5].toInt() and 0xFF
        val podsBattery = data[6].toInt() and 0xFF
        val flagsCase = data[7].toInt() and 0xFF
        val lid = data[8].toInt() and 0xFF
        val color = colorNames[data[9].toInt()] ?: "Unknown"
        val conn = connStates[data[10].toInt()] ?: "Unknown (${data[10].toInt()})"

        val primaryLeft = ((status shr 5) and 0x01) == 1
        val thisInCase = ((status shr 6) and 0x01) == 1
        val xorFactor = primaryLeft xor thisInCase

        val isLeftInEar = if (xorFactor) (status and 0x08) != 0 else (status and 0x02) != 0
        val isRightInEar = if (xorFactor) (status and 0x02) != 0 else (status and 0x08) != 0

        val isFlipped = !primaryLeft

        val leftBatteryNibble = if (isFlipped) (podsBattery shr 4) and 0x0F else podsBattery and 0x0F
        val rightBatteryNibble = if (isFlipped) podsBattery and 0x0F else (podsBattery shr 4) and 0x0F

        val caseBattery = flagsCase and 0x0F
        val flags = (flagsCase shr 4) and 0x0F

        val isLeftCharging = if (isFlipped) (flags and 0x02) != 0 else (flags and 0x01) != 0
        val isRightCharging = if (isFlipped) (flags and 0x01) != 0 else (flags and 0x02) != 0
        val isCaseCharging = (flags and 0x04) != 0

        val lidOpen = if (thisInCase) ((lid shr 3) and 0x01) == 0 else null

        fun decodeBattery(n: Int): Int? = when (n) {
            in 0x0..0x9 -> n * 10
            in 0xA..0xE -> 100
            0xF -> null
            else -> null
        }

        return AirPodsStatus(
            address = address,
            lastSeen = SystemClock.elapsedRealtime(),
            paired = paired,
            model = model,
            leftBattery = decodeBattery(leftBatteryNibble),
            rightBattery = decodeBattery(rightBatteryNibble),
            caseBattery = decodeBattery(caseBattery),
            isLeftInEar = isLeftInEar,
            isRightInEar = isRightInEar,
            isLeftCharging = isLeftCharging,
            isRightCharging = isRightCharging,
            isCaseCharging = isCaseCharging,
            lidOpen = lidOpen,
            color = color,
            connectionState = conn
        )
    }

    companion object {
        internal fun mostRecentPodStatus(statuses: Collection<AirPodsStatus>): AirPodsStatus? =
            statuses.filter { !it.isCaseAdvertisement }.maxByOrNull { it.lastSeen }
        private const val TAG = "AirPodsBLE"
        private const val CLEANUP_INTERVAL_MS = 10000L
        private const val STALE_DEVICE_TIMEOUT_MS = 15000L
        private const val LID_FRESHNESS_MS = 2500L
        // SCAN_MODE_LOW_POWER listens about 0.5 s out of every 5 s.
        private const val LOW_POWER_LID_FRESHNESS_MS = 12_000L
        private const val SCREEN_OFF_LOW_POWER_DELAY_MS = 15_000L
    }
}
