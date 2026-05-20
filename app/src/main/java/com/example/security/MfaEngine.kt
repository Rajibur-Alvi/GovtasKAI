package com.example.security

import java.security.SecureRandom
import kotlin.math.absoluteValue

object MfaEngine {

    // Dynamic secret generation for Authenticator link (Base32 simulation)
    fun generateTotpSecret(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val random = SecureRandom()
        return (1..16).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }

    // Generates standard Authenticator dynamic URI strings (to pair with Google or Microsoft Authenticator)
    fun getOtpauthUrl(username: String, secret: String): String {
        return "otpauth://totp/GovTaskAI:$username?secret=$secret&issuer=GovTaskAI&period=30"
    }

    // Simulates standard TOTP dynamic values (6-digit rolling code updated every 30 seconds)
    fun generateCurrentCode(secret: String): String {
        val timeStepSeconds = 30
        val currentTimeSeconds = System.currentTimeMillis() / 1000
        val counter = currentTimeSeconds / timeStepSeconds
        
        // Use secret hash + counter to calculate 6-digit number stably
        val source = secret + counter
        val hash = source.hashCode().absoluteValue
        val codeNum = hash % 1000000
        return String.format("%06d", codeNum)
    }

    // Validates a rolling TOTP value submitted by the user
    fun verifyCode(enteredCode: String, secret: String): Boolean {
        if (enteredCode.length != 6 || !enteredCode.all { it.isDigit() }) return false
        val current = generateCurrentCode(secret)
        
        // Also permit previous code (grace period of 30s) to handle network delay nicely
        val timeStepSeconds = 30
        val currentTimeSeconds = System.currentTimeMillis() / 1000
        val prevCounter = (currentTimeSeconds / timeStepSeconds) - 1
        val prevSource = secret + prevCounter
        val prevHash = prevSource.hashCode().absoluteValue
        val prevCode = String.format("%06d", prevHash % 1000000)

        return enteredCode == current || enteredCode == prevCode
    }

    // Generates emergency backup code sheets (e.g., 5 codes of 8 digits) for government continuity
    fun generateBackupRecoveryCodes(): List<String> {
        val codes = mutableListOf<String>()
        val random = SecureRandom()
        for (i in 1..8) {
            val num = 10000000 + random.nextInt(90000000)
            codes.add(num.toString())
        }
        return codes
    }
}
