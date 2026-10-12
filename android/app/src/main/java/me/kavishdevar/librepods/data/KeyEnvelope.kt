package me.kavishdevar.librepods.data

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Versioned authenticated encryption, bound to both device identity and key kind. */
object KeyEnvelope {
    fun encrypt(key: SecretKey, identity: String, plain: ByteArray): ByteArray {
        require(plain.size == 16)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(identity.toByteArray(Charsets.UTF_8))
        return byteArrayOf(1) + cipher.iv + cipher.doFinal(plain)
    }
    fun decrypt(key: SecretKey, identity: String, envelope: ByteArray): ByteArray {
        require(envelope.size == 45 && envelope[0] == 1.toByte())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, envelope.copyOfRange(1, 13)))
        cipher.updateAAD(identity.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(envelope.copyOfRange(13, envelope.size)).also { require(it.size == 16) }
    }
}
