package me.kavishdevar.librepods.bluetooth

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * AirPods Pro 3 cases advertise 07 11 06 + an AES block using the selected pair's ENC_KEY.
 * The case address is independent of the pods' IRK. This format has no authentication tag;
 * require its known header, reserved bytes and valid levels before publishing any state.
 * Layout evidence: https://github.com/stuffz/airpods/blob/main/docs/ble-research.md
 */
object CaseAdvertisementParser {
    data class Battery(val level: Int?, val charging: Boolean)
    data class Report(
        val lidOpen: Boolean,
        val lidCounter: Int,
        val left: Battery,
        val right: Battery,
        val case: Battery
    ) {
        val bothBudsSeated: Boolean
            get() = left.level != null && right.level != null && left.charging && right.charging
    }

    fun isCaseAdvertisement(data: ByteArray): Boolean =
        data.size == 19 && data[0] == 0x07.toByte() &&
            data[1] == 0x11.toByte() && data[2] == 0x06.toByte()

    // This single-block decryption mode is fixed by the AirPods advertisement protocol.
    @Suppress("GetInstance")
    fun parse(data: ByteArray, encryptionKey: ByteArray): Report? {
        if (!isCaseAdvertisement(data) || encryptionKey.size != 16) return null
        val block = try {
            Cipher.getInstance("AES/ECB/NoPadding").run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(encryptionKey, "AES"))
                doFinal(data.copyOfRange(3, 19))
            }
        } catch (_: java.security.GeneralSecurityException) {
            return null
        }
        // Firmware variants at 6/7 and the rolling counter at 12..15 are not identity constants.
        // Byte 10 is a 0/1 flag: AirPods Pro 3 report 01 while connected to a phone.
        if (block[0] != 0x29.toByte() || block[1] != 0x20.toByte() ||
            block[8] != 0.toByte() || (block[10].toInt() and 0xff) > 1 || block[11] != 0.toByte()) return null
        val case = battery(block[3]) ?: return null
        val left = battery(block[4]) ?: return null
        val right = battery(block[5]) ?: return null
        return Report(
            lidOpen = block[2].toInt() and 0x08 == 0,
            lidCounter = block[2].toInt() and 0x07,
            left = left, right = right, case = case
        )
    }

    private fun battery(value: Byte): Battery? {
        val unsigned = value.toInt() and 0xff
        if (unsigned == 0xff) return Battery(null, false)
        val level = unsigned and 0x7f
        if (level > 100) return null
        return Battery(level, unsigned and 0x80 != 0)
    }
}
