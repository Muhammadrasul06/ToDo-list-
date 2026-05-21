package com.example.data

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    private const val ALGORITHM = "AES/CBC/PKCS5Padding"
    private const val DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA1"
    private const val KEY_LENGTH = 128 // Compatibility and speed
    private const val ITERATION_COUNT = 500

    fun encrypt(plainText: String, secretPasscode: String): String {
        if (secretPasscode.isEmpty() || plainText.isEmpty()) return plainText
        return try {
            val salt = "TODO_SALT_SECURE_123".toByteArray()
            val factory = SecretKeyFactory.getInstance(DERIVATION_ALGORITHM)
            val spec = PBEKeySpec(secretPasscode.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH)
            val tmp = factory.generateSecret(spec)
            val secretKey = SecretKeySpec(tmp.encoded, "AES")

            val cipher = Cipher.getInstance(ALGORITHM)
            val iv = ByteArray(16)
            SecureRandom().nextBytes(iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))
            
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            plainText
        }
    }

    fun decrypt(cipherTextWithIv: String, secretPasscode: String): String {
        if (secretPasscode.isEmpty() || cipherTextWithIv.isEmpty()) return cipherTextWithIv
        return try {
            val combined = Base64.decode(cipherTextWithIv, Base64.NO_WRAP)
            if (combined.size <= 16) return cipherTextWithIv
            
            val iv = ByteArray(16)
            val cipherText = ByteArray(combined.size - 16)
            System.arraycopy(combined, 0, iv, 0, iv.size)
            System.arraycopy(combined, iv.size, cipherText, 0, cipherText.size)

            val salt = "TODO_SALT_SECURE_123".toByteArray()
            val factory = SecretKeyFactory.getInstance(DERIVATION_ALGORITHM)
            val spec = PBEKeySpec(secretPasscode.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH)
            val tmp = factory.generateSecret(spec)
            val secretKey = SecretKeySpec(tmp.encoded, "AES")

            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
            val decrypted = cipher.doFinal(cipherText)

            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            // If decryption fails, it could be plaintext or incorrect code
            cipherTextWithIv
        }
    }
}
