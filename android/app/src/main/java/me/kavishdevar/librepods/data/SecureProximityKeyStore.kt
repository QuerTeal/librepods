package me.kavishdevar.librepods.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import android.util.Log
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.concurrent.Executors
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.json.JSONObject
import androidx.core.content.edit
import me.kavishdevar.librepods.bluetooth.AACPManager.Companion.ProximityKeyType

/** Keystore operations stay off the scan/UI thread. Ciphertext is excluded from Android backup. */
object SecureProximityKeyStore {
    private const val ALIAS = "librepods.proximity.v1"
    private val worker = Executors.newSingleThreadExecutor()
    private val cached = mutableMapOf<String, Map<ProximityKeyType, ByteArray>>()
    private val loading = mutableSetOf<String>()

    @Synchronized fun get(context: Context, type: ProximityKeyType): ByteArray? {
        val app = context.applicationContext
        val address = app.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("mac_address", "") ?: ""
        if (address.isBlank()) return null
        val id = id(address)
        cached[id]?.let { return it[type]?.copyOf() }
        if (loading.add(id)) worker.execute {
            val keys = runCatching { load(app, address) }.getOrElse {
                Log.w("ProximityKeyStore", "Protected key load unavailable; identity remains unverified")
                emptyMap()
            }
            synchronized(this) { cached[id] = keys; loading.remove(id) }
        }
        return null
    }

    fun save(context: Context, address: String, keys: Map<ProximityKeyType, ByteArray>) {
        if (address.isBlank() || keys.isEmpty() || keys.values.any { it.size != 16 }) return
        val copies = keys.mapValues { it.value.copyOf() }
        val app = context.applicationContext
        worker.execute {
            runCatching {
                val previous = load(app, address)
                val merged = previous + copies
                write(app, address, merged)
                synchronized(this) { cached[id(address)] = merged }
                removeLegacy(app, address)
            }.onFailure { Log.w("ProximityKeyStore", "Protected key save unavailable") }
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }

    private fun load(context: Context, address: String): Map<ProximityKeyType, ByteArray> {
        val atomic = file(context, address)
        if (atomic.baseFile.exists()) {
            val json = JSONObject(String(atomic.readFully(), Charsets.UTF_8))
            val wrapping = key()
            return ProximityKeyType.entries.mapNotNull { type ->
                if (!json.has(type.name)) null else type to KeyEnvelope.decrypt(wrapping,
                    "${id(address)}:${type.name}", Base64.decode(json.getString(type.name), Base64.NO_WRAP))
            }.toMap()
        }
        val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (!address.equals(preferences.getString("mac_address", ""), true)) return emptyMap()
        val legacy = ProximityKeyType.entries.mapNotNull { type ->
            val value = preferences.getString(type.name, null) ?: return@mapNotNull null
            runCatching { Base64.decode(value, Base64.DEFAULT) }.getOrNull()?.takeIf { it.size == 16 }?.let { type to it }
        }.toMap()
        if (legacy.isNotEmpty()) {
            write(context, address, legacy) // Never erase the old data before durable encrypted storage succeeds.
            removeLegacy(context, address)
        }
        return legacy
    }

    private fun write(context: Context, address: String, keys: Map<ProximityKeyType, ByteArray>) {
        val wrapping = key()
        val json = JSONObject().apply {
            keys.forEach { (type, value) -> put(type.name, Base64.encodeToString(
                KeyEnvelope.encrypt(wrapping, "${id(address)}:${type.name}", value), Base64.NO_WRAP)) }
        }
        val atomic = file(context, address)
        val output = atomic.startWrite()
        try { output.write(json.toString().toByteArray(Charsets.UTF_8)); atomic.finishWrite(output) }
        catch (error: Exception) { atomic.failWrite(output); throw error }
    }
    private fun removeLegacy(context: Context, address: String) {
        val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (address.equals(preferences.getString("mac_address", ""), true))
            preferences.edit(commit = true) {
                remove(ProximityKeyType.IRK.name); remove(ProximityKeyType.ENC_KEY.name)
            } // Runs on the key-store worker, after the atomic encrypted write.
    }
    private fun file(context: Context, address: String) = AtomicFile(File(context.noBackupFilesDir, "proximity_${id(address)}.json"))
    private fun id(address: String) = MessageDigest.getInstance("SHA-256")
        .digest(address.uppercase(java.util.Locale.ROOT).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
