package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sec_accounts")
data class AccountEntity(
    @PrimaryKey val id: Int = 1,
    val username: String = "admin",
    val hashedPin: String = "",
    val mfaSecret: String = "",
    val mfaEnabled: Boolean = false,
    val backupCodes: String = "",
    val securityLevel: String = "FEDERAL_LEVEL_C",
    val isRegistered: Boolean = false
)

@Entity(tableName = "gov_tasks")
data class GovTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val module: String, // CIVIC, TAX, BUSINESS, PROPERTY
    val taskName: String, // e.g. "TIN Certificate", "Passport Pre-Check", "EIN Filing"
    val applicantName: String,
    val encryptedPayload: String, // Base64 encrypted administrative variables (SSN, income digits, zoning, EIN)
    val status: String, // DRAFT, SECURITY_LOCKED, PROCESSING, VERIFIED, MANUAL_REVIEW_NEEDED, APPROVED
    val aiConfidence: Int = 0,
    val aiEvaluationReason: String = "",
    val missingInfo: String = "",
    val creationTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "system_audit_logs")
data class SystemAuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val encryptedMessage: String
)

@Entity(tableName = "encrypted_session_cache")
data class EncryptedSessionCacheEntity(
    @PrimaryKey val moduleCode: String,
    val encryptedData: String,
    val lastSaved: Long = System.currentTimeMillis()
)

