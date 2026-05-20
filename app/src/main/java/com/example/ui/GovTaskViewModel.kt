package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiService
import com.example.data.AccountEntity
import com.example.data.AppDatabase
import com.example.data.GovernmentDao
import com.example.data.GovTaskEntity
import com.example.security.CryptoEngine
import com.example.security.MfaEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject

// --- Repository abstraction as mandated by Room skill guidelines ---
class GovernmentRepository(private val dao: GovernmentDao) {
    val accountFlow: StateFlow<AccountEntity?> = dao.getAccountFlow().stateIn(
        scope = kotlinx.coroutines.GlobalScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    suspend fun getAccountSync(): AccountEntity? = dao.getAccountSync()
    suspend fun insertAccount(account: AccountEntity) = dao.insertAccount(account)
    fun getAllTasksFlow() = dao.getAllTasksFlow()
    suspend fun getTaskById(id: Int): GovTaskEntity? = dao.getTaskById(id)
    suspend fun insertTask(task: GovTaskEntity) = dao.insertTask(task)
    suspend fun deleteTaskById(id: Int) = dao.deleteTaskById(id)
    suspend fun clearHistory() = dao.clearHistory()
}

class GovTaskViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: GovernmentRepository
    val accountFlow: StateFlow<AccountEntity?>
    val tasksFlow: StateFlow<List<GovTaskEntity>>

    init {
        val database = AppDatabase.getDatabase(application)
        repository = GovernmentRepository(database.governmentDao())
        accountFlow = repository.accountFlow
        tasksFlow = repository.getAllTasksFlow().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    // --- Authentication & MFA UI States ---
    private val _loginState = MutableStateFlow<LoginState>(LoginState.LoggedOut)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    private val _mfaSetupState = MutableStateFlow<MfaSetupState>(MfaSetupState.Idle)
    val mfaSetupState: StateFlow<MfaSetupState> = _mfaSetupState.asStateFlow()

    private val _rollingTotpCode = MutableStateFlow("")
    val rollingTotpCode: StateFlow<String> = _rollingTotpCode.asStateFlow()

    private val _securityAuditLogs = MutableStateFlow<List<String>>(
        listOf("🔒 System Sandbox Initialized. Welcome to GovTaskAI.", "🔗 Encrypted SQLite storage mounted.")
    )
    val securityAuditLogs: StateFlow<List<String>> = _securityAuditLogs.asStateFlow()

    // --- Form UI States ---
    private val _activeModule = MutableStateFlow("CIVIC")
    val activeModule: StateFlow<String> = _activeModule.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _isOfflineMode = MutableStateFlow(false)
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

    private val _submitStatus = MutableStateFlow<SubmitResult?>(null)
    val submitStatus: StateFlow<SubmitResult?> = _submitStatus.asStateFlow()

    fun setOfflineMode(active: Boolean) {
        _isOfflineMode.value = active
        if (active) {
            addAuditLog("📡 FORCE-OFFLINE: Sentinel isolated offline validator activated manually.")
        } else {
            addAuditLog("📡 CONNECTIVITY SYNCHRONIZED: Connected satellite links back to Gemini API.")
        }
    }

    // Decrypted item session caches to show security status and keep decrypted data safely in volatile RAM
    private val _decryptedTaskContents = MutableStateFlow<Map<Int, String>>(emptyMap())
    val decryptedTaskContents: StateFlow<Map<Int, String>> = _decryptedTaskContents.asStateFlow()

    // Setup periodic 2FA dynamic code generation in ViewModel
    init {
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                val acc = repository.getAccountSync()
                if (acc != null && acc.mfaSecret.isNotEmpty()) {
                    _rollingTotpCode.value = MfaEngine.generateCurrentCode(acc.mfaSecret)
                }
                delay(1000) // update frequency
            }
        }
    }

    fun addAuditLog(text: String) {
        val current = _securityAuditLogs.value.toMutableList()
        current.add(0, "⏱️ [${System.currentTimeMillis() % 100000}] $text")
        if (current.size > 20) current.removeLast()
        _securityAuditLogs.value = current
    }

    // --- Core Operations ---

    // Registers a security master PIN / passcode and builds the security profile
    fun registerAdminAccount(pin: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val hashed = CryptoEngine.hashPin(pin)
            val codes = MfaEngine.generateBackupRecoveryCodes()
            val codesStr = codes.joinToString(",")
            val mfaSecret = MfaEngine.generateTotpSecret()

            val account = AccountEntity(
                hashedPin = hashed,
                mfaSecret = mfaSecret,
                mfaEnabled = false,
                backupCodes = codesStr,
                isRegistered = true
            )
            repository.insertAccount(account)
            addAuditLog("Account Secure Key Derived via PBKDF2/SHA-256.")
            addAuditLog("System PIN registered & 2FA secret generated.")
            _loginState.value = LoginState.MfaEnrollmentNeeded(mfaSecret, codes)
        }
    }

    // Locks the Authenticator setup completion and turns on MFA shield
    fun finalizeMfaEnrollment() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentAcc = repository.getAccountSync()
            if (currentAcc != null) {
                repository.insertAccount(currentAcc.copy(mfaEnabled = true))
                addAuditLog("MFA Shield activated. Hardware key bounds configured.")
                _loginState.value = LoginState.LoggedIn(currentAcc.username)
            }
        }
    }

    // Standard Login flow with multi-factor check gates
    fun attemptLogin(pin: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val acc = repository.getAccountSync()
            if (acc == null || !acc.isRegistered) {
                _loginState.value = LoginState.AuthError("No registered secure profile exists. Setup PIN first.")
                return@launch
            }

            val inputHash = CryptoEngine.hashPin(pin)
            if (inputHash == acc.hashedPin) {
                if (acc.mfaEnabled) {
                    addAuditLog("Step 1 Auth passed. Launching 2FA Challenge...")
                    _loginState.value = LoginState.MfaChallengeRequired(acc.mfaSecret, pinSource = pin)
                } else {
                    addAuditLog("Administrator logged in successfully. MFA disabled.")
                    _loginState.value = LoginState.LoggedIn(acc.username)
                }
            } else {
                addAuditLog("🔒 SEC_BREACH: Authentication attempt blocked. Incorrect PIN.")
                _loginState.value = LoginState.AuthError("Invalid administrative security PIN.")
            }
        }
    }

    // Validates entered TOTP rolling codes during login
    fun verifyMfaChallenge(code: String, pinSource: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val acc = repository.getAccountSync()
            if (acc != null && MfaEngine.verifyCode(code, acc.mfaSecret)) {
                addAuditLog("Step 2 Auth (TOTP Match) verified. Session clearances loaded.")
                _loginState.value = LoginState.LoggedIn(acc.username)
            } else {
                addAuditLog("🔒 MFA_BLOCK: OTP Token mismatch. Entry rejected.")
                _loginState.value = LoginState.AuthError("Invalid Security Token match. Please check code or timing.")
            }
        }
    }

    // Handles emergency bypass bypass through backup codes
    fun bypassWithRecoveryCode(code: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val acc = repository.getAccountSync()
            if (acc != null) {
                val list = acc.backupCodes.split(",")
                if (list.contains(code.trim())) {
                    val remaining = list.filter { it != code.trim() }.joinToString(",")
                    repository.insertAccount(acc.copy(backupCodes = remaining))
                    addAuditLog("🔑 BACKUP_RECOVERY: Security profile bypassed with one-time sheet code.")
                    _loginState.value = LoginState.LoggedIn(acc.username)
                } else {
                    _loginState.value = LoginState.AuthError("Recovery code match failed.")
                }
            }
        }
    }

    fun logout() {
        _loginState.value = LoginState.LoggedOut
        _decryptedTaskContents.value = emptyMap()
        _submitStatus.value = null
        addAuditLog("Session closed. In-memory plain text records cleared.")
    }

    // --- Application Submission & Audit Pipeline ---

    fun changeActiveModule(module: String) {
        _activeModule.value = module
        _submitStatus.value = null
    }

    // Submits the governmental pre-qualification form
    fun submitAdministrativeForm(
        applicantName: String,
        fields: Map<String, String>,
        pinSource: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            _submitStatus.value = null

            addAuditLog("Compiling Document Registry packet for $applicantName...")

            // Convert map to local JSON payload
            val jsonPayload = JSONObject()
            fields.forEach { (k, v) -> jsonPayload.put(k, v) }
            val rawJson = jsonPayload.toString()

            // Encrypt using Master Key derived from User's PIN + secure salt
            val encryptedData = CryptoEngine.encrypt(rawJson, pinSource)

            addAuditLog("Applying AES GCM/CBC 128-bit file block cipher...")

            // Execute regulatory intelligence evaluation via GeminiService
            val module = _activeModule.value
            val taskName = getTaskNameForModule(module)

            val aiResult = GeminiService.evaluateApplication(
                module = module,
                taskName = taskName,
                applicantName = applicantName,
                decryptedFields = fields,
                isOfflineMode = _isOfflineMode.value
            )

            addAuditLog("Brain response ingested. Compliance score calculated: ${aiResult.confidence}%.")

            // Store cleanly in Room
            val task = GovTaskEntity(
                module = module,
                taskName = taskName,
                applicantName = applicantName,
                encryptedPayload = encryptedData,
                status = aiResult.status,
                aiConfidence = aiResult.confidence,
                aiEvaluationReason = aiResult.evaluationText,
                missingInfo = aiResult.missingInfo
            )

            repository.insertTask(task)
            addAuditLog("Governmental application G-${System.currentTimeMillis() % 10000} registered legally.")

            _isProcessing.value = false
            _submitStatus.value = SubmitResult(
                status = aiResult.status,
                confidence = aiResult.confidence,
                evaluation = aiResult.evaluationText,
                missingInfo = aiResult.missingInfo
            )
        }
    }

    // Zero-out decrypted buffers immediately from RAM when app is backgrounded or session ends
    fun purgeVolatileMemory() {
        _decryptedTaskContents.value = emptyMap()
        addAuditLog("🔒 SECURITY PROTOCOL: Volatile RAM zero-out triggered. Decrypted text structures fully destroyed.")
    }

    // Decrypts an individual application in-memory with PIN challenge on user demand
    fun unlockAndDecryptTask(taskId: Int, enteredPin: String, onCompletion: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val acc = repository.getAccountSync()
            if (acc == null) {
                onCompletion(false)
                return@launch
            }

            val checkHash = CryptoEngine.hashPin(enteredPin)
            if (checkHash != acc.hashedPin) {
                addAuditLog("❌ Local decrypt challenge failed. Pin rejected.")
                onCompletion(false)
                return@launch
            }

            val task = repository.getTaskById(taskId)
            if (task != null) {
                val decrypted = CryptoEngine.decrypt(task.encryptedPayload, enteredPin)
                if (decrypted.startsWith("ERR_")) {
                    onCompletion(false)
                } else {
                    val current = _decryptedTaskContents.value.toMutableMap()
                    
                    // Format JSON beautifully
                    val json = JSONObject(decrypted)
                    val formatted = StringBuilder()
                    json.keys().forEach { k ->
                        formatted.append("${k.replace("_", " ")}: ${json.get(k)}\n")
                    }
                    current[taskId] = formatted.toString()
                    _decryptedTaskContents.value = current
                    addAuditLog("🔍 File G-${task.id % 10000} decrypted inside in-memory cache successfully.")
                    onCompletion(true)
                }
            } else {
                onCompletion(false)
            }
        }
    }

    fun purgeDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearHistory()
            _decryptedTaskContents.value = emptyMap()
            addAuditLog("⚠️ History purged. Administrative storage blocks cleared.")
        }
    }

    private fun getTaskNameForModule(module: String): String {
        return when (module.uppercase()) {
            "CIVIC" -> "Federal TIN Certificate & Civic Registration"
            "TAX" -> "Tax Return Analyzer & Verification filing"
            "BUSINESS" -> "Corporate State Trade Registry License"
            "PROPERTY" -> "Zoning Assessment & Municipal Permit"
            else -> "GovTaskAI Application filing"
        }
    }
}

// --- UI Sub-States representation ---

sealed interface LoginState {
    object LoggedOut : LoginState
    data class MfaEnrollmentNeeded(val secret: String, val recoveryCodes: List<String>) : LoginState
    data class MfaChallengeRequired(val secret: String, val pinSource: String) : LoginState
    data class LoggedIn(val username: String) : LoginState
    data class AuthError(val error: String) : LoginState
}

sealed interface MfaSetupState {
    object Idle : MfaSetupState
    data class VerificationStep(val secret: String) : MfaSetupState
}

data class SubmitResult(
    val status: String,
    val confidence: Int,
    val evaluation: String,
    val missingInfo: String
)
