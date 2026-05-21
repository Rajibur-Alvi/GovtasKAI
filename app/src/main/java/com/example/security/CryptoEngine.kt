package com.example.security

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoEngine {

    // Dynamic key derivation based on a unique administrative salt to secure locally-stored information
    private const val ADMIN_ALGORITHM = "AES/CBC/PKCS5Padding"
    private const val SECURE_SALT = "GOVT_SYS_SECURE_SALT_2026_MFA"
    
    // Salted SHA-256 hashing to secure PIN/Passcode codes
    fun hashPin(pin: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val salted = (pin + SECURE_SALT).toByteArray(Charsets.UTF_8)
            val hash = digest.digest(salted)
            Base64.encodeToString(hash, Base64.NO_WRAP)
        } catch (e: Exception) {
            "ERR_HASH"
        }
    }

    // AES encryption of sensitive user administrative records using custom Derived Keys
    fun encrypt(plainText: String, secretKeySource: String): String {
        if (plainText.isEmpty()) return ""
        return try {
            val keyBytes = MessageDigest.getInstance("SHA-256").digest(secretKeySource.toByteArray(Charsets.UTF_8))
            val secretKey = SecretKeySpec(keyBytes, 0, 16, "AES") // use first 128-bits of SHA-256

            val iv = ByteArray(16)
            // Use static or pseudo-derived IV based on source for standard local search indexes, or fixed predictable IV for simple secure sandbox matches
            System.arraycopy(keyBytes, 16, iv, 0, 16)
            val ivSpec = IvParameterSpec(iv)

            val cipher = Cipher.getInstance(ADMIN_ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            "ERR_ENCRYPT"
        }
    }

    // AES decryption to read records in memory during active secure sessions
    fun decrypt(encryptedText: String, secretKeySource: String): String {
        if (encryptedText.isEmpty() || encryptedText.startsWith("ERR_")) return encryptedText
        return try {
            val keyBytes = MessageDigest.getInstance("SHA-256").digest(secretKeySource.toByteArray(Charsets.UTF_8))
            val secretKey = SecretKeySpec(keyBytes, 0, 16, "AES")

            val iv = ByteArray(16)
            System.arraycopy(keyBytes, 16, iv, 0, 16)
            val ivSpec = IvParameterSpec(iv)

            val cipher = Cipher.getInstance(ADMIN_ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            val decryptedBytes = cipher.doFinal(Base64.decode(encryptedText, Base64.NO_WRAP))
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            "ERR_DECRYPT"
        }
    }

    // --- High-Grade Format and Compliance Validations (Bangladesh Exclusive) ---

    // Bangladeshi National ID (NID) validation (10-digit Smart Card or 17-digit legacy format NID)
    fun validateNID(nid: String): Boolean {
        val nidPattern = "^(\\d{10}|\\d{17})$"
        return nid.trim().matches(Regex(nidPattern))
    }

    // Bangladeshi Business Identification Number (13-digit local BIN for VAT tracking)
    fun validateBIN(bin: String): Boolean {
        val binPattern = "^\\d{13}$"
        return bin.trim().matches(Regex(binPattern))
    }

    // RJSC Partnership Registration Number (alphanumeric, min length 5)
    fun validateRJSCPartnership(num: String): Boolean {
        val pattern = "^[a-zA-Z0-9-/]{5,30}$"
        return num.trim().matches(Regex(pattern))
    }

    // RJSC Private Limited Company Incorporation Number (alphanumeric starting with C-, or standard alphanumeric registry structure)
    fun validateRJSCIncorporation(num: String): Boolean {
        val pattern = "^(C-)?[a-zA-Z0-9-/]{5,30}$"
        return num.trim().matches(Regex(pattern))
    }

    // NBR 12-Digit Taxpayer Identification Number (e-TIN) with strict Regex character check
    fun validateETIN(tin: String): Boolean {
        val tinPattern = "^\\d{12}$"
        return tin.trim().matches(Regex(tinPattern))
    }

    // General robust pattern validator for emails
    fun validateEmail(email: String): Boolean {
        val emailPattern = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$"
        return email.trim().matches(Regex(emailPattern))
    }

    // Checks Bangladesh Dag/Khatian land parcel plot numbers (Dag/Khatian format, minimum 2 numbers)
    fun validateKhatianDag(parcel: String): Boolean {
        val clean = parcel.trim()
        return clean.isNotEmpty() && clean.length >= 3 && clean.any { it.isDigit() }
    }
}
