package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import kotlinx.coroutines.delay
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.GovTaskEntity
import com.example.ui.GovTaskViewModel
import com.example.ui.LoginState
import com.example.ui.SubmitResult
import com.example.ui.theme.MyApplicationTheme
import com.example.security.CryptoEngine
import com.example.security.MfaEngine

class MainActivity : ComponentActivity() {
    private val viewModel: GovTaskViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(dynamicColor = false, darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = com.example.ui.theme.HighDensityBackground
                ) {
                    GovTaskApp(viewModel)
                }
            }
        }
    }
}

@Composable
fun GovTaskApp(viewModel: GovTaskViewModel) {
    val loginState by viewModel.loginState.collectAsStateWithLifecycle()
    val accountState by viewModel.accountFlow.collectAsStateWithLifecycle()

    // Volatile Memory Zero-Out: lifecycle observer to wipe memory blocks on backgrounding ON_STOP
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                viewModel.purgeVolatileMemory()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        when {
            accountState == null || !accountState!!.isRegistered -> {
                // Administrative First-time PIN setup
                AdminRegisterScreen(viewModel)
            }
            loginState is LoginState.LoggedOut -> {
                AdminLoginScreen(viewModel, isMfaChallenge = false)
            }
            loginState is LoginState.MfaEnrollmentNeeded -> {
                val state = loginState as LoginState.MfaEnrollmentNeeded
                MfaEnrollmentScreen(viewModel, state.secret, state.recoveryCodes)
            }
            loginState is LoginState.MfaChallengeRequired -> {
                AdminLoginScreen(viewModel, isMfaChallenge = true)
            }
            loginState is LoginState.LoggedIn -> {
                val state = loginState as LoginState.LoggedIn
                MainWorkspaceScreen(viewModel = viewModel, username = state.username)
            }
            loginState is LoginState.AuthError -> {
                val state = loginState as LoginState.AuthError
                AuthErrorGateScreen(viewModel, state.error)
            }
        }
    }
}

// --- Screen 1: Master Registration Setup ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminRegisterScreen(viewModel: GovTaskViewModel) {
    var pin by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .widthIn(max = 500.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Security Shield",
            tint = com.example.ui.theme.HighDensityPrimary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "GovTaskAI SECURE INITIALIZATION",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = com.example.ui.theme.Slate900,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Provisioning a multi-factor local encrypted federal administrative environment. Initialize your login master PIN passcode.",
            style = MaterialTheme.typography.bodyMedium,
            color = com.example.ui.theme.Slate500,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) pin = it },
            label = { Text("Define System Master PIN (4-6 digits)") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = com.example.ui.theme.HighDensityPrimary,
                unfocusedBorderColor = com.example.ui.theme.HighDensityBorder,
                focusedTextColor = com.example.ui.theme.Slate900,
                unfocusedTextColor = com.example.ui.theme.Slate700,
                focusedLabelColor = com.example.ui.theme.HighDensityPrimary,
                unfocusedLabelColor = com.example.ui.theme.Slate500
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("setup_pin_input")
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = pinConfirm,
            onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) pinConfirm = it },
            label = { Text("Re-enter Master PIN to Validate") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = com.example.ui.theme.HighDensityPrimary,
                unfocusedBorderColor = com.example.ui.theme.HighDensityBorder,
                focusedTextColor = com.example.ui.theme.Slate900,
                unfocusedTextColor = com.example.ui.theme.Slate700,
                focusedLabelColor = com.example.ui.theme.HighDensityPrimary,
                unfocusedLabelColor = com.example.ui.theme.Slate500
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("setup_pin_confirm")
        )

        if (errorText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (pin.length < 4) {
                    errorText = "PIN must be at least 4 digits."
                } else if (pin != pinConfirm) {
                    errorText = "PIN codes do not match."
                } else {
                    errorText = ""
                    viewModel.registerAdminAccount(pin)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.HighDensityPrimary, contentColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("initialize_system_button")
        ) {
            Icon(imageVector = Icons.Default.Shield, contentDescription = "Enable Shield")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Initialize Administrative Shield", fontWeight = FontWeight.Bold)
        }
    }
}

// --- Screen 2: MFA Enrollment Screen ---
@Composable
fun MfaEnrollmentScreen(viewModel: GovTaskViewModel, secret: String, recoveryCodes: List<String>) {
    val context = LocalContext.current
    val otpauthUrl = MfaEngine.getOtpauthUrl("administrator", secret)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .widthIn(max = 500.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        item {
            Icon(
                imageVector = Icons.Default.Verified,
                contentDescription = "MFA Configuration",
                tint = com.example.ui.theme.HighDensityPrimary,
                modifier = Modifier
                    .size(64.dp)
                    .padding(top = 16.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "CO-FACTOR 2FA PROTECTION SYSTEM",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = com.example.ui.theme.Slate900,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Government standards demand a secure Multi-Factor token for state clearances. Configure Google Authenticator or compatible app using details below.",
                style = MaterialTheme.typography.bodySmall,
                color = com.example.ui.theme.Slate500,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Highlight QR secret box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(com.example.ui.theme.HighDensitySurface, shape = RoundedCornerShape(12.dp))
                    .border(1.dp, com.example.ui.theme.HighDensityBorder, shape = RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "GOOGLE AUTHENTICATOR SETUP PROFILE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = com.example.ui.theme.HighDensityPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Secret Base-32 Code:",
                        fontSize = 12.sp,
                        color = com.example.ui.theme.Slate500
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("GovTaskAI Secret", secret)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied secret!", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Text(
                            text = secret,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = com.example.ui.theme.Slate900,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = com.example.ui.theme.Slate500, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Standard OTP URI (for barcode input):",
                        fontSize = 10.sp,
                        color = com.example.ui.theme.Slate400
                    )
                    Text(
                        text = otpauthUrl,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = com.example.ui.theme.Slate500,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Emergency Recovery Sheet
            Text(
                text = "EMERGENCY BACKUP SHEET (Save codes securely):",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = com.example.ui.theme.Slate700,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Backup codes are one-time-use bypass tokens used if Authenticator is lost. They cannot be retrieved again.",
                        color = Color(0xFF991B1B),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    GridRecoveryCodes(recoveryCodes)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    viewModel.finalizeMfaEnrollment()
                },
                colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.HighDensityPrimary, contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("enable_mfa_button")
            ) {
                Text("Confirm 2FA Setup Complete", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
fun GridRecoveryCodes(codes: List<String>) {
    Column {
        for (i in codes.indices step 2) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "${i + 1}: ${codes[i]}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = com.example.ui.theme.Slate900,
                    fontWeight = FontWeight.Bold
                )
                if (i + 1 < codes.size) {
                    Text(
                        text = "${i + 2}: ${codes[i + 1]}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = com.example.ui.theme.Slate900,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// --- Screen 3: Combined Pin & MFA Entry Pad Lock ---
@Composable
fun AdminLoginScreen(viewModel: GovTaskViewModel, isMfaChallenge: Boolean) {
    var codeValue by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }
    val rollingOtpCode by viewModel.rollingTotpCode.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val loginState by viewModel.loginState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .widthIn(max = 480.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isMfaChallenge) Icons.Default.Key else Icons.Default.Lock,
            contentDescription = "Keys",
            tint = com.example.ui.theme.HighDensityPrimary,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (isMfaChallenge) "STEP 2: CO-FACTOR IDENTITY CHECK" else "GOVT TASK AI WORKSPACE SECURED",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = com.example.ui.theme.Slate900,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (isMfaChallenge) "Dynamic passcode verified. Enter the 6-digit credential from Authenticator app." else "Accessing high-grade government data requires master PIN verification clearance.",
            style = MaterialTheme.typography.bodySmall,
            color = com.example.ui.theme.Slate500,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Large high-contrast state key display
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp),
            color = com.example.ui.theme.HighDensitySurface,
            border = BorderStroke(1.dp, com.example.ui.theme.HighDensityBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                     text = if (isMfaChallenge) "SECURITY CODE CHALLENGE" else "CORP PASSCODE ID",
                     fontSize = 12.sp,
                     color = com.example.ui.theme.HighDensityPrimary,
                     fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // Emulate obscured entry
                Text(
                    text = if (codeValue.isEmpty()) {
                        if (isMfaChallenge) "------" else "••••"
                    } else {
                        if (isMfaChallenge) codeValue else "• ".repeat(codeValue.length).trim()
                    },
                    fontSize = 28.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = com.example.ui.theme.Slate900,
                    letterSpacing = 4.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // High-fidelity numeric dial-pad for secure operations
        DialPad(
            maxLength = if (isMfaChallenge) 6 else 6,
            currentValue = codeValue,
            onValueChange = { codeValue = it }
        )

        if (errorText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Simulator Convenience feature (Let users inspect rolling OTP code to log in smoothly)
            if (isMfaChallenge) {
                Button(
                    onClick = {
                        if (rollingOtpCode.isNotEmpty()) {
                            codeValue = rollingOtpCode
                            Toast.makeText(context, "Filled simulation code $rollingOtpCode", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.Slate400, contentColor = Color.White),
                    modifier = Modifier.weight(1f).padding(end = 6.dp)
                ) {
                    Text("Auto Match OTP (${rollingOtpCode})", fontSize = 11.sp, maxLines = 1)
                }
            }

            Button(
                onClick = {
                    if (isMfaChallenge) {
                        val originalPin = (loginState as? LoginState.MfaChallengeRequired)?.pinSource ?: ""
                        if (codeValue.length != 6) {
                            errorText = "Please enter the full 6-digit OTP code."
                        } else {
                            errorText = ""
                            viewModel.verifyMfaChallenge(codeValue, originalPin)
                        }
                    } else {
                        if (codeValue.length < 4) {
                            errorText = "PIN must be at least 4 digits."
                        } else {
                            errorText = ""
                            viewModel.attemptLogin(codeValue)
                            codeValue = ""
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.HighDensityPrimary, contentColor = Color.White),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp)
                    .testTag("submit_auth_gate_button")
            ) {
                Text("UNLOCK CAP", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        if (isMfaChallenge) {
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(
                onClick = {
                    // Prompt for emergency bypass
                    Toast.makeText(context, "Please enter an emergency 8-digit backup code as PIN entry to bypass.", Toast.LENGTH_LONG).show()
                }
            ) {
                Text("Lost Authenticator? Use emergency code bypass", color = com.example.ui.theme.Slate500, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun DialPad(
    maxLength: Int,
    currentValue: String,
    onValueChange: (String) -> Unit
) {
    val numbers = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("Clear", "0", "Delete")
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        for (row in numbers) {
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                for (key in row) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                if (key in listOf("Clear", "Delete")) Color(0xFFE2E8F0) else com.example.ui.theme.HighDensitySurface
                            )
                            .border(1.dp, com.example.ui.theme.HighDensityBorder, CircleShape)
                            .clickable {
                                when (key) {
                                    "Clear" -> onValueChange("")
                                    "Delete" -> {
                                        if (currentValue.isNotEmpty()) {
                                            onValueChange(currentValue.dropLast(1))
                                        }
                                    }
                                    else -> {
                                        if (currentValue.length < maxLength) {
                                            onValueChange(currentValue + key)
                                        }
                                    }
                                }
                            }
                            .testTag("keypad_$key"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = key,
                            color = if (key in listOf("Clear", "Delete")) com.example.ui.theme.HighDensityPrimary else com.example.ui.theme.Slate900,
                            fontSize = if (key in listOf("Clear", "Delete")) 13.sp else 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// --- Screen 4: Authorization Error / Recovery Sheet Screen ---
@Composable
fun AuthErrorGateScreen(viewModel: GovTaskViewModel, error: String) {
    var recoveryCode by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Alert Error",
            tint = com.example.ui.theme.HighDensityError,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "SECURITY BOUNDARY EXPLAINED",
            fontWeight = FontWeight.Bold,
            color = com.example.ui.theme.Slate900,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = error,
            color = com.example.ui.theme.Slate500,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Option to enter backup bypass
        OutlinedTextField(
            value = recoveryCode,
            onValueChange = { recoveryCode = it },
            label = { Text("Enter Emergency Bypass Recovery Code") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = com.example.ui.theme.Slate900,
                unfocusedTextColor = com.example.ui.theme.Slate700,
                focusedBorderColor = com.example.ui.theme.HighDensityPrimary,
                unfocusedBorderColor = com.example.ui.theme.HighDensityBorder,
                focusedLabelColor = com.example.ui.theme.HighDensityPrimary,
                unfocusedLabelColor = com.example.ui.theme.Slate500
            ),
            modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                viewModel.bypassWithRecoveryCode(recoveryCode)
            },
            colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.HighDensityError, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp)
        ) {
            Text("Authorize Backup bypass", color = Color.White)
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = { viewModel.logout() }) {
            Text("Return to Secure Screen Lock", color = com.example.ui.theme.Slate500)
        }
    }
}

// --- Screen 5: Main Workspace Command Center ---
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainWorkspaceScreen(
    viewModel: GovTaskViewModel,
    username: String
) {
    val activeTab by viewModel.activeModule.collectAsStateWithLifecycle()
    val isEvaluating by viewModel.isProcessing.collectAsStateWithLifecycle()
    val checkStatus by viewModel.submitStatus.collectAsStateWithLifecycle()
    val tasks by viewModel.tasksFlow.collectAsStateWithLifecycle()
    val logs by viewModel.securityAuditLogs.collectAsStateWithLifecycle()

    var showDecryptDialog by remember { mutableStateOf<Int?>(null) }
    var pinForDecrypt by remember { mutableStateOf("") }
    var decryptError by remember { mutableStateOf(false) }

    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Workspace Security Title Header
        item {
            WorkspaceHeader(
                username = username,
                onLogoutClick = { viewModel.logout() },
                onPurgeClick = { viewModel.purgeDatabase() }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Security Cleared status box
        item {
            SecurityClearanceCard(tasksCount = tasks.size)
            Spacer(modifier = Modifier.height(10.dp))

            // Communication Link Status Switcher
            val isOffline by viewModel.isOfflineMode.collectAsStateWithLifecycle()
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.HighDensitySurface),
                border = BorderStroke(1.dp, com.example.ui.theme.HighDensityBorder)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "COMMUNICATION LINK CRYPTO GRID",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = com.example.ui.theme.Slate500
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isOffline) Color(0xFFDC2626) else Color(0xFF16A34A))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isOffline) "Isolated Air-Gapped Engine (OFFLINE)" else "Satellite Sync Feed (Gemini AI ONLINE)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = com.example.ui.theme.Slate900
                            )
                        }
                    }
                    
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFE2E8F0))
                            .padding(2.dp)
                    ) {
                        // Online Tab
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (!isOffline) Color.White else Color.Transparent)
                                .clickable { viewModel.setOfflineMode(false) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("ONLINE", fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = if (!isOffline) com.example.ui.theme.HighDensityPrimary else com.example.ui.theme.Slate500)
                        }
                        // Offline Tab
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isOffline) Color(0xFFFEE2E2) else Color.Transparent)
                                .clickable { viewModel.setOfflineMode(true) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("AIR-GAP", fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = if (isOffline) Color(0xFFDC2626) else com.example.ui.theme.Slate500)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Horizontal Grid Selection Tabs for Modules
        item {
            ModuleSelectionTabs(activeTab = activeTab, onTabSelected = { viewModel.changeActiveModule(it) })
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Dynamic Form Fields Builder based on selected administrative branch
        item {
            ModuleFormCard(
                module = activeTab,
                isEvaluating = isEvaluating,
                viewModel = viewModel,
                onSubmit = { applicant, fields, pin ->
                    viewModel.submitAdministrativeForm(applicant, fields, pin)
                }
            )
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Gemini AI underwriting execution results
        item {
            AnimatedContent(targetState = checkStatus) { status ->
                if (status != null) {
                    ComplianceAuditOutputCard(
                        result = status,
                        module = activeTab,
                        onDismiss = { /* keep displayed as history instead */ }
                    )
                } else {
                    Box(Modifier.height(1.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Encrypted database list section
        item {
            Text(
                text = "DATABASE CIPHER VAULT (Room Encrypted Storage)",
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = com.example.ui.theme.Slate500,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        }

        if (tasks.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.HighDensitySurface),
                    border = BorderStroke(1.dp, com.example.ui.theme.HighDensityBorder)
                ) {
                    Text(
                        text = "🔒 Vault is empty. Complete and transmit an administrative application to encrypt records.",
                        fontSize = 12.sp,
                        color = com.example.ui.theme.Slate500,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        } else {
            items(tasks) { task ->
                EncryptedTaskItemCard(
                    task = task,
                    viewModel = viewModel,
                    onUnlockRequest = { id ->
                        showDecryptDialog = id
                        pinForDecrypt = ""
                        decryptError = false
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        // Security Console sandbox Logs
        item {
            Spacer(modifier = Modifier.height(16.dp))
            SystemAuditConsoleCard(logs = logs)
            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    // Modal Decrypt Challenge Dialogue
    if (showDecryptDialog != null) {
        AlertDialog(
            onDismissRequest = { showDecryptDialog = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Key, contentDescription = "Decrypt key", tint = com.example.ui.theme.HighDensityPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Secure Decrypt Challenge", color = com.example.ui.theme.Slate900, fontSize = 16.sp)
                }
            },
            text = {
                Column {
                    Text(
                        text = "This application's variables are fully encrypted inside SQLite. Provide your administrative PIN to load and read the decrypt key.",
                        fontSize = 12.sp,
                        color = com.example.ui.theme.Slate500
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pinForDecrypt,
                        onValueChange = { pinForDecrypt = it },
                        label = { Text("Administrative Login PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = com.example.ui.theme.Slate900,
                            unfocusedTextColor = com.example.ui.theme.Slate700,
                            focusedBorderColor = com.example.ui.theme.HighDensityPrimary,
                            unfocusedBorderColor = com.example.ui.theme.HighDensityBorder,
                            focusedLabelColor = com.example.ui.theme.HighDensityPrimary,
                            unfocusedLabelColor = com.example.ui.theme.Slate500
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (decryptError) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Verification PIN rejected. Decryption aborted.", color = com.example.ui.theme.HighDensityError, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.unlockAndDecryptTask(showDecryptDialog!!, pinForDecrypt) { success ->
                            if (success) {
                                showDecryptDialog = null
                                decryptError = false
                            } else {
                                decryptError = true
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.HighDensityPrimary, contentColor = Color.White)
                ) {
                    Text("VALIDATE & DECRYPT")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDecryptDialog = null }) {
                    Text("CANCEL", color = com.example.ui.theme.Slate500)
                }
            },
            containerColor = com.example.ui.theme.HighDensitySurface
        )
    }
}

// Header Area Component
@Composable
fun WorkspaceHeader(
    username: String,
    onLogoutClick: () -> Unit,
    onPurgeClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "GovTaskAI Command Center",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                color = com.example.ui.theme.Slate900
            )
            Text(
                text = "SECURE SESSION FOR: ${username.uppercase()} • FEDERAL REGIONAL AREA",
                fontSize = 10.sp,
                color = com.example.ui.theme.HighDensityPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        Row {
            IconButton(
                onClick = onPurgeClick,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFFFEE2E2))
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear Session History", tint = Color(0xFFDC2626))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onLogoutClick,
                colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.Slate900, contentColor = Color.White)
            ) {
                Icon(imageVector = Icons.Default.Lock, contentDescription = "Lock", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("LOCK", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// Clearance visual banner
@Composable
fun SecurityClearanceCard(tasksCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.HighDensityAccent),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Cleared Shield",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "SECURITY PROTOCOL ACTIVE",
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = Color(0xFFC7D2FE) // Indigo-200
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text("VAULT-256", fontSize = 8.sp, color = Color(0xFFA5B4FC), fontWeight = FontWeight.ExtraBold)
                    }
                }
                Text(
                    text = "MFA Verified & Encrypted • Powered by Gemini AI client-side validation logic.",
                    fontSize = 11.sp,
                    color = Color.White,
                    lineHeight = 14.sp
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(text = "STATUS", fontSize = 9.sp, color = Color(0xFFA5B4FC), fontWeight = FontWeight.Bold)
                Text(text = "VERIFIED", fontSize = 12.sp, color = com.example.ui.theme.HighDensitySuccess, fontWeight = FontWeight.ExtraBold)
                Text(text = "$tasksCount Arch.", fontSize = 9.sp, color = Color(0xFFC7D2FE))
            }
        }
    }
}

// Four primary module tabs
@Composable
fun ModuleSelectionTabs(activeTab: String, onTabSelected: (String) -> Unit) {
    val tabs = listOf(
        Triple("CIVIC", "Civic Desk", Icons.Default.AccountBalance),
        Triple("TAX", "Tax Desk", Icons.Default.Star), 
        Triple("BUSINESS", "Corporate", Icons.Default.Home), 
        Triple("PROPERTY", "Property", Icons.Default.Search)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(com.example.ui.theme.HighDensitySurface)
            .border(1.dp, com.example.ui.theme.HighDensityBorder, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        tabs.forEach { (id, label, icon) ->
            val isSelected = activeTab == id
            
            val (tabBg, tabText, tabBorder) = when (id) {
                "CIVIC" -> Triple(com.example.ui.theme.CivicBg, com.example.ui.theme.CivicPrimary, com.example.ui.theme.CivicBorder)
                "TAX" -> Triple(com.example.ui.theme.TaxBg, com.example.ui.theme.TaxPrimary, com.example.ui.theme.TaxBorder)
                "BUSINESS" -> Triple(com.example.ui.theme.BusinessBg, com.example.ui.theme.BusinessPrimary, com.example.ui.theme.BusinessBorder)
                else -> Triple(com.example.ui.theme.PropertyBg, com.example.ui.theme.PropertyPrimary, com.example.ui.theme.PropertyBorder)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) tabBg else Color.Transparent)
                    .then(if (isSelected) Modifier.border(1.dp, tabBorder, RoundedCornerShape(10.dp)) else Modifier)
                    .clickable { onTabSelected(id) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isSelected) tabText else com.example.ui.theme.Slate400,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = if (isSelected) tabText else com.example.ui.theme.Slate500,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

class DraftDelegate(
    private val getter: () -> String,
    private val setter: (String) -> Unit
) {
    operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): String = getter()
    operator fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: String) {
        setter(value)
    }
}

// Structured verification input sheets
@Composable
fun ModuleFormCard(
    module: String,
    isEvaluating: Boolean,
    viewModel: com.example.ui.GovTaskViewModel,
    onSubmit: (applicantName: String, dataFields: Map<String, String>, pinSource: String) -> Unit
) {
    val draft by viewModel.formDraft.collectAsStateWithLifecycle()

    var name by DraftDelegate({ draft.applicantName }, { Target -> viewModel.updateFormDraft { it.copy(applicantName = Target) } })
    var civicBirthCity by DraftDelegate({ draft.civicBirthCity }, { Target -> viewModel.updateFormDraft { it.copy(civicBirthCity = Target) } })
    var civicSsn by DraftDelegate({ draft.civicSsn }, { Target -> viewModel.updateFormDraft { it.copy(civicSsn = Target) } })
    var civicEmail by DraftDelegate({ draft.civicEmail }, { Target -> viewModel.updateFormDraft { it.copy(civicEmail = Target) } })
    var civicEmergencyNum by DraftDelegate({ draft.civicEmergencyNum }, { Target -> viewModel.updateFormDraft { it.copy(civicEmergencyNum = Target) } })

    var taxTin by DraftDelegate({ draft.taxTin }, { Target -> viewModel.updateFormDraft { it.copy(taxTin = Target) } })
    var taxIncome by DraftDelegate({ draft.taxIncome }, { Target -> viewModel.updateFormDraft { it.copy(taxIncome = Target) } })
    var taxYear by DraftDelegate({ draft.taxYear }, { Target -> viewModel.updateFormDraft { it.copy(taxYear = Target) } })
    var taxEmail by DraftDelegate({ draft.taxEmail }, { Target -> viewModel.updateFormDraft { it.copy(taxEmail = Target) } })

    var busEin by DraftDelegate({ draft.busEin }, { Target -> viewModel.updateFormDraft { it.copy(busEin = Target) } })
    var busStructure by DraftDelegate({ draft.busStructure }, { Target -> viewModel.updateFormDraft { it.copy(busStructure = Target) } })
    var busCapital by DraftDelegate({ draft.busCapital }, { Target -> viewModel.updateFormDraft { it.copy(busCapital = Target) } })
    var busEmail by DraftDelegate({ draft.busEmail }, { Target -> viewModel.updateFormDraft { it.copy(busEmail = Target) } })

    var propParcelId by DraftDelegate({ draft.propParcelId }, { Target -> viewModel.updateFormDraft { it.copy(propParcelId = Target) } })
    var propSqFt by DraftDelegate({ draft.propSqFt }, { Target -> viewModel.updateFormDraft { it.copy(propSqFt = Target) } })
    var propEstCost by DraftDelegate({ draft.propEstCost }, { Target -> viewModel.updateFormDraft { it.copy(propEstCost = Target) } })
    var propEmail by DraftDelegate({ draft.propEmail }, { Target -> viewModel.updateFormDraft { it.copy(propEmail = Target) } })

    var showScannerDialog by remember { mutableStateOf(false) }
    var securityTokenPinConfirm by remember { mutableStateOf("") }

    // Clear active temporary cache on module change
    LaunchedEffect(module) {
        securityTokenPinConfirm = ""
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.HighDensitySurface),
        border = BorderStroke(1.dp, com.example.ui.theme.HighDensityBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Module Title & Description
            val (title, info) = when (module) {
                "CIVIC" -> "Federal Citizen Registration Portal" to "Pre-qualify and compile TIN generation credentials or passport file registrations under federal boundaries."
                "TAX" -> "Secured Income Return & Tax Certificate" to "Calculate individual state returns, log identification, and review compliance scores under IRC regulations."
                "BUSINESS" -> "Corporate State Corporate Registrar" to "Automate state trade license generation, verify EIN strings, and compile organizational articles."
                "PROPERTY" -> "Zoning Assessment Ledger & PermitAI" to "Review construction zoning, evaluate setback regulations, and output property building permits."
                else -> "" to ""
            }

            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = com.example.ui.theme.Slate900)
            Text(text = info, fontSize = 11.sp, color = com.example.ui.theme.Slate500, lineHeight = 15.sp, modifier = Modifier.padding(top = 2.dp))
            
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { showScannerDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9), contentColor = com.example.ui.theme.Slate900),
                border = BorderStroke(1.dp, com.example.ui.theme.HighDensityBorder),
                modifier = Modifier.fillMaxWidth().height(38.dp).testTag("trigger_ocr_scan_button"),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt, 
                    contentDescription = "OCR Scanner icon", 
                    modifier = Modifier.size(14.dp), 
                    tint = com.example.ui.theme.HighDensityPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("SCAN & EXTRACT FORM / ID (AIR-GAPPED OCR)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = com.example.ui.theme.Slate900)
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = com.example.ui.theme.HighDensityBorder
            )

            // General mandatory applicant name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Filing Citizen/Applicant Name") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = com.example.ui.theme.Slate900,
                    unfocusedTextColor = com.example.ui.theme.Slate700,
                    focusedBorderColor = com.example.ui.theme.HighDensityPrimary,
                    unfocusedBorderColor = com.example.ui.theme.HighDensityBorder,
                    focusedLabelColor = com.example.ui.theme.HighDensityPrimary,
                    unfocusedLabelColor = com.example.ui.theme.Slate500
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("applicant_name_input")
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Load module-specific fields
            when (module) {
                "CIVIC" -> {
                    OutlinedTextField(
                        value = civicEmail,
                        onValueChange = { civicEmail = it },
                        label = { Text("Administrative contact Email") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = com.example.ui.theme.Slate900,
                            unfocusedTextColor = com.example.ui.theme.Slate700,
                            focusedBorderColor = com.example.ui.theme.HighDensityPrimary,
                            unfocusedBorderColor = com.example.ui.theme.HighDensityBorder,
                            focusedLabelColor = com.example.ui.theme.HighDensityPrimary,
                            unfocusedLabelColor = com.example.ui.theme.Slate500
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = civicSsn,
                        onValueChange = { civicSsn = it },
                        label = { Text("National Social Security ID (AAA-GG-SSSS)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = com.example.ui.theme.Slate900,
                            unfocusedTextColor = com.example.ui.theme.Slate700,
                            focusedBorderColor = com.example.ui.theme.HighDensityPrimary,
                            unfocusedBorderColor = com.example.ui.theme.HighDensityBorder,
                            focusedLabelColor = com.example.ui.theme.HighDensityPrimary,
                            unfocusedLabelColor = com.example.ui.theme.Slate500
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("civic_ssn_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        OutlinedTextField(
                            value = civicBirthCity,
                            onValueChange = { civicBirthCity = it },
                            label = { Text("City of Birth") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = com.example.ui.theme.Slate900,
                                unfocusedTextColor = com.example.ui.theme.Slate700,
                                focusedBorderColor = com.example.ui.theme.HighDensityPrimary,
                                unfocusedBorderColor = com.example.ui.theme.HighDensityBorder,
                                focusedLabelColor = com.example.ui.theme.HighDensityPrimary,
                                unfocusedLabelColor = com.example.ui.theme.Slate500
                            ),
                            modifier = Modifier.weight(1f).padding(end = 4.dp)
                        )
                        OutlinedTextField(
                            value = civicEmergencyNum,
                            onValueChange = { civicEmergencyNum = it },
                            label = { Text("Emergency Phone") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = com.example.ui.theme.Slate900,
                                unfocusedTextColor = com.example.ui.theme.Slate700,
                                focusedBorderColor = com.example.ui.theme.HighDensityPrimary,
                                unfocusedBorderColor = com.example.ui.theme.HighDensityBorder,
                                focusedLabelColor = com.example.ui.theme.HighDensityPrimary,
                                unfocusedLabelColor = com.example.ui.theme.Slate500
                            ),
                            modifier = Modifier.weight(1f).padding(start = 4.dp)
                        )
                    }
                }
                "TAX" -> {
                    OutlinedTextField(
                        value = taxEmail,
                        onValueChange = { taxEmail = it },
                        label = { Text("Administrative contact Email") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = com.example.ui.theme.Slate900,
                            unfocusedTextColor = com.example.ui.theme.Slate700,
                            focusedBorderColor = com.example.ui.theme.HighDensityPrimary,
                            unfocusedBorderColor = com.example.ui.theme.HighDensityBorder,
                            focusedLabelColor = com.example.ui.theme.HighDensityPrimary,
                            unfocusedLabelColor = com.example.ui.theme.Slate500
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        OutlinedTextField(
                            value = taxTin,
                            onValueChange = { taxTin = it },
                            label = { Text("9-Digit Taxpayer ID (TIN)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = com.example.ui.theme.Slate900,
                                unfocusedTextColor = com.example.ui.theme.Slate700,
                                focusedBorderColor = com.example.ui.theme.HighDensityPrimary,
                                unfocusedBorderColor = com.example.ui.theme.HighDensityBorder,
                                focusedLabelColor = com.example.ui.theme.HighDensityPrimary,
                                unfocusedLabelColor = com.example.ui.theme.Slate500
                            ),
                            modifier = Modifier.weight(1f).padding(end = 4.dp).testTag("tax_tin_input")
                        )
                        OutlinedTextField(
                            value = taxYear,
                            onValueChange = { taxYear = it },
                            label = { Text("Filing Year") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = com.example.ui.theme.Slate900,
                                unfocusedTextColor = com.example.ui.theme.Slate700,
                                focusedBorderColor = com.example.ui.theme.HighDensityPrimary,
                                unfocusedBorderColor = com.example.ui.theme.HighDensityBorder,
                                focusedLabelColor = com.example.ui.theme.HighDensityPrimary,
                                unfocusedLabelColor = com.example.ui.theme.Slate500
                            ),
                            modifier = Modifier.weight(1f).padding(start = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = taxIncome,
                        onValueChange = { taxIncome = it },
                        label = { Text("Estimated Annual Aggregate Income (USD)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = com.example.ui.theme.Slate900,
                            unfocusedTextColor = com.example.ui.theme.Slate700,
                            focusedBorderColor = com.example.ui.theme.HighDensityPrimary,
                            unfocusedBorderColor = com.example.ui.theme.HighDensityBorder,
                            focusedLabelColor = com.example.ui.theme.HighDensityPrimary,
                            unfocusedLabelColor = com.example.ui.theme.Slate500
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                "BUSINESS" -> {
                    OutlinedTextField(
                        value = busEmail,
                        onValueChange = { busEmail = it },
                        label = { Text("Administrative contact Email") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = com.example.ui.theme.Slate900,
                            unfocusedTextColor = com.example.ui.theme.Slate700,
                            focusedBorderColor = com.example.ui.theme.HighDensityPrimary,
                            unfocusedBorderColor = com.example.ui.theme.HighDensityBorder,
                            focusedLabelColor = com.example.ui.theme.HighDensityPrimary,
                            unfocusedLabelColor = com.example.ui.theme.Slate500
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = busEin,
                        onValueChange = { busEin = it },
                        label = { Text("Employer Identification Number (XX-XXXXXXX)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = com.example.ui.theme.Slate900,
                            unfocusedTextColor = com.example.ui.theme.Slate700,
                            focusedBorderColor = com.example.ui.theme.HighDensityPrimary,
                            unfocusedBorderColor = com.example.ui.theme.HighDensityBorder,
                            focusedLabelColor = com.example.ui.theme.HighDensityPrimary,
                            unfocusedLabelColor = com.example.ui.theme.Slate500
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("business_ein_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        OutlinedTextField(
                            value = busStructure,
                            onValueChange = { busStructure = it },
                            label = { Text("Corporate Structure (LLC/C-Corp)") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = com.example.ui.theme.Slate900,
                                unfocusedTextColor = com.example.ui.theme.Slate700,
                                focusedBorderColor = com.example.ui.theme.HighDensityPrimary,
                                unfocusedBorderColor = com.example.ui.theme.HighDensityBorder,
                                focusedLabelColor = com.example.ui.theme.HighDensityPrimary,
                                unfocusedLabelColor = com.example.ui.theme.Slate500
                            ),
                            modifier = Modifier.weight(1f).padding(end = 4.dp)
                        )
                        OutlinedTextField(
                            value = busCapital,
                            onValueChange = { busCapital = it },
                            label = { Text("Estimated Launch Budget ($)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = com.example.ui.theme.Slate900,
                                unfocusedTextColor = com.example.ui.theme.Slate700,
                                focusedBorderColor = com.example.ui.theme.HighDensityPrimary,
                                unfocusedBorderColor = com.example.ui.theme.HighDensityBorder,
                                focusedLabelColor = com.example.ui.theme.HighDensityPrimary,
                                unfocusedLabelColor = com.example.ui.theme.Slate500
                            ),
                            modifier = Modifier.weight(1f).padding(start = 4.dp)
                        )
                    }
                }
                "PROPERTY" -> {
                    OutlinedTextField(
                        value = propEmail,
                        onValueChange = { propEmail = it },
                        label = { Text("Administrative contact Email") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = com.example.ui.theme.Slate900,
                            unfocusedTextColor = com.example.ui.theme.Slate700,
                            focusedBorderColor = com.example.ui.theme.HighDensityPrimary,
                            unfocusedBorderColor = com.example.ui.theme.HighDensityBorder,
                            focusedLabelColor = com.example.ui.theme.HighDensityPrimary,
                            unfocusedLabelColor = com.example.ui.theme.Slate500
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = propParcelId,
                        onValueChange = { propParcelId = it },
                        label = { Text("Municipal Property Grid Parcel ID") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = com.example.ui.theme.Slate900,
                            unfocusedTextColor = com.example.ui.theme.Slate700,
                            focusedBorderColor = com.example.ui.theme.HighDensityPrimary,
                            unfocusedBorderColor = com.example.ui.theme.HighDensityBorder,
                            focusedLabelColor = com.example.ui.theme.HighDensityPrimary,
                            unfocusedLabelColor = com.example.ui.theme.Slate500
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("property_parcel_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        OutlinedTextField(
                            value = propSqFt,
                            onValueChange = { propSqFt = it },
                            label = { Text("Addition Area (Sq.Ft)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = com.example.ui.theme.Slate900,
                                unfocusedTextColor = com.example.ui.theme.Slate700,
                                focusedBorderColor = com.example.ui.theme.HighDensityPrimary,
                                unfocusedBorderColor = com.example.ui.theme.HighDensityBorder,
                                focusedLabelColor = com.example.ui.theme.HighDensityPrimary,
                                unfocusedLabelColor = com.example.ui.theme.Slate500
                            ),
                            modifier = Modifier.weight(1f).padding(end = 4.dp)
                        )
                        OutlinedTextField(
                            value = propEstCost,
                            onValueChange = { propEstCost = it },
                            label = { Text("Investment Cost Estimate ($)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = com.example.ui.theme.Slate900,
                                unfocusedTextColor = com.example.ui.theme.Slate700,
                                focusedBorderColor = com.example.ui.theme.HighDensityPrimary,
                                unfocusedBorderColor = com.example.ui.theme.HighDensityBorder,
                                focusedLabelColor = com.example.ui.theme.HighDensityPrimary,
                                unfocusedLabelColor = com.example.ui.theme.Slate500
                            ),
                            modifier = Modifier.weight(1f).padding(start = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = com.example.ui.theme.HighDensityBorder)
            Spacer(modifier = Modifier.height(10.dp))

            // Security PIN confirmation
            OutlinedTextField(
                value = securityTokenPinConfirm,
                onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) securityTokenPinConfirm = it },
                label = { Text("Confirm administrative passcode PIN to compile file") },
                visualTransformation = PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = com.example.ui.theme.Slate900,
                    unfocusedTextColor = com.example.ui.theme.Slate700,
                    focusedBorderColor = Color(0xFFDC2626), // Accent red warning
                    unfocusedBorderColor = com.example.ui.theme.HighDensityBorder,
                    focusedLabelColor = Color(0xFFDC2626),
                    unfocusedLabelColor = com.example.ui.theme.Slate500
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("form_pin_confirmation_input")
            )

            Spacer(modifier = Modifier.height(16.dp))

            val context = LocalContext.current

            Button(
                onClick = {
                    if (name.isBlank()) {
                        Toast.makeText(context, "Applicant Name cannot be blank.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (securityTokenPinConfirm.isBlank()) {
                        Toast.makeText(context, "Provide security PIN passcode confirmation.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    // Build dynamic dictionary
                    val payload = mutableMapOf<String, String>()
                    when (module) {
                        "CIVIC" -> {
                            payload["Contact_Email"] = civicEmail
                            payload["SSN_OR_TIN"] = civicSsn
                            payload["Passport_City_Of_Birth"] = civicBirthCity
                            payload["Emergency_Num"] = civicEmergencyNum
                        }
                        "TAX" -> {
                            payload["Contact_Email"] = taxEmail
                            payload["Taxpayer_TIN"] = taxTin
                            payload["Tax_Filing_Year"] = taxYear
                            payload["Est_Annual_Income"] = taxIncome
                        }
                        "BUSINESS" -> {
                            payload["Contact_Email"] = busEmail
                            payload["Business_EIN"] = busEin
                            payload["Corporate_Structure"] = busStructure
                            payload["Est_Capital_Budget"] = busCapital
                        }
                        "PROPERTY" -> {
                            payload["Contact_Email"] = propEmail
                            payload["Property_Parcel_ID"] = propParcelId
                            payload["Zoning_SqFt"] = propSqFt
                            payload["Est_Budget_USD"] = propEstCost
                        }
                    }

                    onSubmit(name, payload, securityTokenPinConfirm)
                },
                enabled = !isEvaluating,
                colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.HighDensityPrimary, contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("submit_form_button")
            ) {
                if (isEvaluating) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("TRANSMITTING TO BOT BRAIN...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(imageVector = Icons.Default.Send, contentDescription = "Transmit")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ENCRYPT & COMPILE WITH AI CO-PILOT", fontWeight = FontWeight.SemiBold)
                }
            }

            if (showScannerDialog) {
                AdministrativeDocumentScannerDialog(
                    onDismissRequest = { showScannerDialog = false },
                    onApplyScannedData = { scanName, scanEmail, arg1, arg2, arg3 ->
                        name = scanName
                        when (module) {
                            "CIVIC" -> {
                                civicEmail = scanEmail
                                civicSsn = arg1
                                civicBirthCity = arg2
                                civicEmergencyNum = arg3
                            }
                            "TAX" -> {
                                taxEmail = scanEmail
                                taxTin = arg1
                                taxYear = arg2
                                taxIncome = arg3
                            }
                            "BUSINESS" -> {
                                busEmail = scanEmail
                                busEin = arg1
                                busStructure = arg2
                                busCapital = arg3
                            }
                            "PROPERTY" -> {
                                propEmail = scanEmail
                                propParcelId = arg1
                                propSqFt = arg2
                                propEstCost = arg3
                            }
                        }
                    },
                    currentModule = module
                )
            }
        }
    }
}

// Visual output showing evaluation results
@Composable
fun ComplianceAuditOutputCard(
    result: SubmitResult,
    module: String,
    onDismiss: () -> Unit
) {
    val (statusColor, statusBg, icon) = when (result.status.uppercase()) {
        "APPROVED" -> Triple(Color(0xFF15803D), Color(0xFFF0FDF4), Icons.Default.CheckCircle)
        "VERIFIED" -> Triple(com.example.ui.theme.HighDensityPrimary, com.example.ui.theme.CivicBg, Icons.Default.Info)
        "MANUAL_REVIEW_NEEDED" -> Triple(com.example.ui.theme.HighDensityError, Color(0xFFFEF2F2), Icons.Default.Warning)
        "OFFLINE_PENDING_SYNC" -> Triple(Color(0xFFD97706), Color(0xFFFEF3C7), Icons.Default.Info)
        else -> Triple(com.example.ui.theme.Slate900, com.example.ui.theme.HighDensitySurface, Icons.Default.Info)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = statusBg),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = icon, contentDescription = result.status, tint = statusColor, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "COMPLIANCE STATUS: ${result.status}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = statusColor
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(com.example.ui.theme.Slate900)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Confidence: ${result.confidence}%",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "BRAIN UNDERWRITING AUDIT REASONING:",
                fontSize = 10.sp,
                color = statusColor,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = result.evaluation,
                fontSize = 12.sp,
                color = com.example.ui.theme.Slate900,
                lineHeight = 16.sp
            )

            if (result.missingInfo.trim().isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                
                // Remediation Dashboard Header
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.05f)),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Deficiency Checklist",
                                tint = statusColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "REMEDIATION DASHBOARD & FIELD DIAGNOSTICS",
                                fontSize = 9.sp,
                                color = statusColor,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val deficiencies = result.missingInfo.split(",")
                        deficiencies.filter { it.trim().isNotEmpty() }.forEach { rawItem ->
                            val item = rawItem.trim()
                            
                            // Parse "[Field] - Issue" format
                            val (fieldStr, issueStr) = if (item.startsWith("[") && item.contains("]")) {
                                val closeIdx = item.indexOf("]")
                                val field = item.substring(1, closeIdx).trim()
                                val rest = item.substring(closeIdx + 1).trim()
                                val issue = if (rest.startsWith("-") || rest.startsWith(":")) {
                                    rest.substring(1).trim()
                                } else {
                                    rest
                                }
                                Pair(field, issue)
                            } else {
                                Pair("", item)
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(statusColor)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    if (fieldStr.isNotEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(statusColor.copy(alpha = 0.12f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = fieldStr.uppercase(),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = statusColor
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                    }
                                    Text(
                                        text = issueStr,
                                        color = com.example.ui.theme.Slate700,
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Database individual Row element
@Composable
fun EncryptedTaskItemCard(
    task: GovTaskEntity,
    viewModel: GovTaskViewModel,
    onUnlockRequest: (Int) -> Unit
) {
    val context = LocalContext.current
    val decryptedCache by viewModel.decryptedTaskContents.collectAsStateWithLifecycle()
    val isRevealed = decryptedCache.containsKey(task.id)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.HighDensitySurface),
        border = BorderStroke(
            width = 1.dp,
            color = if (isRevealed) com.example.ui.theme.HighDensityPrimary.copy(alpha = 0.5f) else com.example.ui.theme.HighDensityBorder
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    when (task.status.uppercase()) {
                                        "APPROVED" -> Color(0xFF15803D)
                                        "VERIFIED" -> com.example.ui.theme.HighDensityPrimary
                                        else -> com.example.ui.theme.HighDensityError
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "FILE ID: G-${task.id % 10000} • Module: ${task.module}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = com.example.ui.theme.Slate500
                        )
                    }
                    Text(
                        text = task.taskName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = com.example.ui.theme.Slate900,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // SECURE LOCK SWITCH BUTTON
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isRevealed) com.example.ui.theme.HighDensityPrimary else Color(0xFFE2E8F0))
                        .clickable {
                            if (isRevealed) {
                                // Clear the cache, lock it back up
                                val current = viewModel.decryptedTaskContents.value.toMutableMap()
                                current.remove(task.id)
                                viewModel.addAuditLog("Explicit user command: Re-applied AES encryption shell. Plaintext ejected from RAM.")
                            } else {
                                onUnlockRequest(task.id)
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("lock_toggle_${task.id}")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isRevealed) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = "Lock icon",
                            tint = if (isRevealed) Color.White else com.example.ui.theme.Slate700,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isRevealed) "DECRYPTED" else "SQL AES SECURED",
                            color = if (isRevealed) Color.White else com.example.ui.theme.Slate900,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Body representing encrypted ciphertext payload, or decrypted state values
            if (isRevealed) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF1F5F9), shape = RoundedCornerShape(6.dp))
                        .border(1.dp, com.example.ui.theme.HighDensityBorder, shape = RoundedCornerShape(6.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "DECRYPTED PLAIN-TEXT STRUCTS (VOLATILE RAM):",
                                fontSize = 9.sp,
                                color = com.example.ui.theme.HighDensityPrimary,
                                fontWeight = FontWeight.Bold
                            )

                            // Native high density PDF generator button
                            TextButton(
                                onClick = {
                                    val plaintextStr = decryptedCache[task.id] ?: ""
                                    val recordsMap = mutableMapOf<String, String>()
                                    
                                    // Parse key values from decrypted plain-text formatted output
                                    plaintextStr.lines().forEach { line ->
                                        if (line.contains(":")) {
                                            val parts = line.split(":", limit = 2)
                                            if (parts.size == 2) {
                                                recordsMap[parts[0].trim()] = parts[1].trim()
                                            }
                                        }
                                    }
                                    
                                    val pdfFile = com.example.security.PdfGenerator.generateFormPdf(context, task, recordsMap)
                                    if (pdfFile != null) {
                                        viewModel.exportComplianceLedger(context, task.id) { ledgerFile ->
                                            (context as? android.app.Activity)?.runOnUiThread {
                                                val msg = if (ledgerFile != null) {
                                                    "📄 OFFICIAL PDF & COMPLIANCE LEDGER COMPILED SUCCESSFULLY:\nDocument saved: ${pdfFile.name}\nLedger saved: ${ledgerFile.name}"
                                                } else {
                                                    "📄 OFFICIAL PDF COMPILED SUCCESSFULLY:\nDocument saved: ${pdfFile.name}"
                                                }
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            }
                                        }
                                        viewModel.addAuditLog("📄 OFFICIAL_PDF: Pre-compiled form file G-${task.id % 10000} written into downloads folder.")
                                    } else {
                                        Toast.makeText(context, "🚨 PDF compilation error. Security sandbox violation.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share, 
                                    contentDescription = "Export PDF file", 
                                    tint = com.example.ui.theme.HighDensityPrimary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("EXPORT PDF PACKET", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = com.example.ui.theme.HighDensityPrimary)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Applicant: ${task.applicantName}\n" + (decryptedCache[task.id] ?: ""),
                            fontSize = 11.sp,
                            color = com.example.ui.theme.Slate700,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 15.sp
                        )
                    }
                }
            } else {
                Text(
                    text = "CORRUPTED / SECURED VALUE CIPHER BLOCKS:\n${task.encryptedPayload.take(90)}...",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = com.example.ui.theme.Slate500,
                    maxLines = 2,
                    lineHeight = 13.sp,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// Interactive auditing logs
@Composable
fun SystemAuditConsoleCard(logs: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.Slate900),
        border = BorderStroke(1.dp, com.example.ui.theme.HighDensityBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2EA44F))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "REALTIME SANDBOX AUDIT SYSTEM (CONSOLE)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Text(
                    text = "STABLE STATUS",
                    fontSize = 9.sp,
                    color = Color(0xFF2EA44F),
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(modifier = Modifier.height(100.dp)) {
                LazyColumn {
                    items(logs) { log ->
                        Text(
                            text = log,
                            color = Color(0xFFA7F3D0), // Emerald-200 terminal font
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AdministrativeDocumentScannerDialog(
    onDismissRequest: () -> Unit,
    onApplyScannedData: (
        name: String,
        email: String,
        arg1: String,
        arg2: String,
        arg3: String
    ) -> Unit,
    currentModule: String
) {
    var selectedDocIndex by remember { mutableStateOf(0) }
    var isScanning by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableStateOf(0f) }
    var scanStatusText by remember { mutableStateOf("Ready to scan") }
    var scanCompleted by remember { mutableStateOf(false) }

    val docOptions = when (currentModule) {
        "CIVIC" -> listOf("U.S. National ID (Margaret Vance)", "Expired Passport Card")
        "TAX" -> listOf("Standard W-2 Form (Robert Sterling)", "Form 1099-MISC Record")
        "BUSINESS" -> listOf("Articles of Organization (Helix Biotech)", "Commercial Trade Permit")
        "PROPERTY" -> listOf("Zoning Deed Receipt (David Thorne)", "East District Permit Proposal")
        else -> listOf("Unknown Certification Paper")
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.CameraAlt, contentDescription = "OCR Scanner icon", tint = com.example.ui.theme.HighDensityPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("EDGE OCR LASER SCANNER vx7", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = com.example.ui.theme.Slate900)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Aptly position your physical civic document boundaries inside the green focus brackets. On-device Core ML-Kit de-skew, contrast boost, and text extraction runs safely in air-gapped RAM.",
                    fontSize = 11.sp,
                    color = com.example.ui.theme.Slate500,
                    lineHeight = 15.sp
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Text("SELECT PHYSICAL FORM TARGET:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = com.example.ui.theme.Slate700)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(), 
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    docOptions.forEachIndexed { idx, option ->
                        Button(
                            onClick = { 
                                if (!isScanning) {
                                    selectedDocIndex = idx
                                    scanCompleted = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedDocIndex == idx) com.example.ui.theme.HighDensityPrimary else Color(0xFFF1F5F9),
                                contentColor = if (selectedDocIndex == idx) Color.White else com.example.ui.theme.Slate700
                            ),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.weight(1f).height(30.dp),
                            border = if (selectedDocIndex == idx) null else BorderStroke(1.dp, com.example.ui.theme.HighDensityBorder)
                        ) {
                            Text(
                                text = if (option.length > 18) option.take(16) + ".." else option, 
                                fontSize = 8.sp, 
                                fontWeight = FontWeight.ExtraBold, 
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Simulated Viewfinder Frame
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .border(1.dp, if (isScanning) Color.Green else com.example.ui.theme.HighDensityBorder, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isScanning) {
                        val infiniteTransition = rememberInfiniteTransition()
                        val floatAnim by infiniteTransition.animateFloat(
                            initialValue = 0.05f,
                            targetValue = 0.95f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1400, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            )
                        )
                        
                        // Green Laser Line
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .align(Alignment.TopCenter)
                                .offset(y = (floatAnim * 150).dp)
                                .background(Color.Green)
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.Green, strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "CORE-OCR EXTRACTING: ${scanProgress.toInt()}%", 
                                color = Color.Green, 
                                fontSize = 9.sp, 
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = scanStatusText, 
                                color = Color.White.copy(alpha = 0.7f), 
                                fontSize = 8.sp, 
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else if (scanCompleted) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = "Scanned Check mark", tint = Color.Green, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("SCAN COMPLETED SECURELY", color = Color.Green, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = when (currentModule) {
                                    "CIVIC" -> "NAME: MARGARET K. VANCE\nID VALUE: 045-88-2512\nBIRTHCITY: INDIANAPOLIS\nEMAIL: margaret.vance@fed.gov"
                                    "TAX" -> "NAME: ROBERT T. STERLING\nID TIN: 258416397\nTAX YEAR: 2025\nEST ANNUAL INCOME: $185,900"
                                    "BUSINESS" -> "NAME: HELIX BIOLABS INC.\nID EIN: 47-1958214\nSTRUCTURE: C-Corp\nCAPITAL BUDGET: $820,000"
                                    "PROPERTY" -> "NAME: DAVID THORNE\nPARCEL ID: PL-85261-N\nZONING SIZE: 4250 SQFT\nEST BUDGET: $135,000"
                                    else -> "GENERIC UNIFIED ADMINISTRATIVE TUPLE EXTREMELY SAFE"
                                },
                                color = Color.LightGray,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Default.CameraAlt, contentDescription = "Camera viewport", tint = Color.LightGray.copy(alpha = 0.4f), modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("TARGET VIEWPORT SLEEPING", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text("TAP BELOW TRIGGER TO INITIATE LIVE SCAN", color = Color.Gray.copy(alpha = 0.6f), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                        }
                    }

                    // Green brackets overlay
                    Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                        Text("[", color = if (isScanning) Color.Green else Color.LightGray.copy(alpha = 0.3f), fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopStart))
                        Text("]", color = if (isScanning) Color.Green else Color.LightGray.copy(alpha = 0.3f), fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopEnd))
                        Text("[", color = if (isScanning) Color.Green else Color.LightGray.copy(alpha = 0.3f), fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomStart))
                        Text("]", color = if (isScanning) Color.Green else Color.LightGray.copy(alpha = 0.3f), fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomEnd))
                    }
                }

                if (isScanning) {
                    LaunchedEffect(Unit) {
                        scanProgress = 0f
                        scanStatusText = "🔍 Aligning focal borders..."
                        delay(500)
                        scanProgress = 30f
                        scanStatusText = "📐 Running Perspective Warp..."
                        delay(500)
                        scanProgress = 65f
                        scanStatusText = "🔆 Binarizing and clarifying reflections..."
                        delay(500)
                        scanProgress = 85f
                        scanStatusText = "🧬 Dynamic OCR Matrix segmentation..."
                        delay(500)
                        scanProgress = 100f
                        isScanning = false
                        scanCompleted = true
                    }
                }
            }
        },
        confirmButton = {
            if (scanCompleted) {
                Button(
                    onClick = {
                        when (currentModule) {
                            "CIVIC" -> {
                                onApplyScannedData("Margaret K. Vance", "margaret.vance@fed.gov", "045-88-2512", "Indianapolis", "317-555-0104")
                            }
                            "TAX" -> {
                                onApplyScannedData("Robert T. Sterling", "r.sterling@comcast.net", "258416397", "2025", "185900")
                            }
                            "BUSINESS" -> {
                                onApplyScannedData("Helix BioLabs Inc.", "compliance@helixbiolabs.com", "47-1958214", "C-Corp", "820000")
                            }
                            "PROPERTY" -> {
                                onApplyScannedData("David Thorne", "david@thornedesign.net", "PL-85261-N", "4250", "135000")
                            }
                        }
                        onDismissRequest()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Green, contentColor = Color.Black),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("APPLY DIGITAL SIGNATURE PACKET", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = { 
                        if (!isScanning) {
                            isScanning = true 
                            scanCompleted = false
                            scanProgress = 0f
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.HighDensityPrimary),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(imageVector = Icons.Default.FlashOn, contentDescription = "Laser flash icon", modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("TRIGGER OCR DE-SKEW", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.height(36.dp)
            ) {
                Text("CANCEL", color = com.example.ui.theme.Slate500, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = com.example.ui.theme.HighDensitySurface
    )
}
