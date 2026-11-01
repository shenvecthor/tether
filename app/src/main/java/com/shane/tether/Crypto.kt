package com.shane.tether

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end encryption. The 256-bit pair key only ever lives on the two phones
 * (it travels inside the pairing QR code). Firebase stores ciphertext only.
 */
object Crypto {
    private val rng = SecureRandom()
    private const val GCM = "AES/GCM/NoPadding"

    fun newKey(): ByteArray = ByteArray(32).also { rng.nextBytes(it) }

    fun randomHex(bytes: Int): String =
        ByteArray(bytes).also { rng.nextBytes(it) }.joinToString("") { "%02x".format(it) }

    fun encrypt(key: ByteArray, plain: String): String {
        val iv = ByteArray(12).also { rng.nextBytes(it) }
        val c = Cipher.getInstance(GCM)
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }

    fun decrypt(key: ByteArray, blob: String): String? = try {
        val all = Base64.decode(blob, Base64.NO_WRAP)
        val c = Cipher.getInstance(GCM)
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, all, 0, 12))
        String(c.doFinal(all, 12, all.size - 12), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }

    // ---- Protect the pair key at rest with the hardware-backed Android Keystore ----
    private const val KS_ALIAS = "tether_wrap"

    private fun wrapKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KS_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                KS_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun wrap(secret: ByteArray): String {
        val c = Cipher.getInstance(GCM)
        c.init(Cipher.ENCRYPT_MODE, wrapKey())
        return Base64.encodeToString(c.iv + c.doFinal(secret), Base64.NO_WRAP)
    }

    fun unwrap(blob: String): ByteArray? = try {
        val all = Base64.decode(blob, Base64.NO_WRAP)
        val c = Cipher.getInstance(GCM)
        c.init(Cipher.DECRYPT_MODE, wrapKey(), GCMParameterSpec(128, all, 0, 12))
        c.doFinal(all, 12, all.size - 12)
    } catch (e: Exception) {
        null
    }
}
