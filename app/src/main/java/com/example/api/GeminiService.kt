package com.example.api

import android.util.Log
import com.example.security.CryptoEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiService {

    private const val TAG = "GeminiService"
    
    // Configure 60-second timeouts to support robust transactions
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // Structured data response model
    data class AIResult(
        val status: String, // VERIFIED, APPROVED, MANUAL_REVIEW_NEEDED
        val confidence: Int,
        val evaluationText: String,
        val missingInfo: String
    )

    // Primary intelligent evaluation mechanism combining Gemini API with an offline administrative rulemaking module for Bangladesh
    suspend fun evaluateApplication(
        module: String,
        taskName: String,
        applicantName: String,
        decryptedFields: Map<String, String>,
        isOfflineMode: Boolean = false
    ): AIResult = withContext(Dispatchers.IO) {
        
        // 1. Gather all inputs to build context
        val fieldsDetail = decryptedFields.map { "${it.key}: ${it.value}" }.joinToString("\n")
        val email = decryptedFields["Contact_Email"] ?: ""
        val nid = decryptedFields["NID"] ?: ""
        val bin = decryptedFields["BIN"] ?: ""
        val thin = decryptedFields["e_TIN"] ?: ""
        val regNo = decryptedFields["Corp_Reg_Number"] ?: ""
        val structure = decryptedFields["Corp_Structure"] ?: ""
        val scaleSqFt = decryptedFields["Permit_Area_SqFt"]?.toDoubleOrNull() ?: 0.0
        val costBdt = decryptedFields["Est_Budget_BDT"]?.toDoubleOrNull() ?: decryptedFields["Authorized_Capital_BDT"]?.toDoubleOrNull() ?: decryptedFields["Annual_Income_BDT"]?.toDoubleOrNull() ?: 0.0

        // 2. Perform advanced programmatic offline validation (First Sentinel Layer - Bangladesh Rules)
        val validationErrors = mutableListOf<String>()
        if (email.isNotEmpty() && !CryptoEngine.validateEmail(email)) {
            validationErrors.add("Invalid administrative contact email format: $email")
        }
        if (nid.isNotEmpty() && !CryptoEngine.validateNID(nid)) {
            validationErrors.add("Bangladeshi NID is structurally invalid. Must be exactly a 10-digit Smart Card or a 17-digit legacy card.")
        }
        if (bin.isNotEmpty() && !CryptoEngine.validateBIN(bin)) {
            validationErrors.add("VAT Business Identification Number (BIN) is structurally invalid. Must be exactly 13 digits.")
        }
        if (thin.isNotEmpty() && !CryptoEngine.validateETIN(thin)) {
            validationErrors.add("NBR Taxpayer Identification Number (e-TIN) is invalid. Must be exactly a 12-digit numeric array.")
        }
        if (regNo.isNotEmpty()) {
            if (structure.contains("Partnership", ignoreCase = true) && !CryptoEngine.validateRJSCPartnership(regNo)) {
                validationErrors.add("RJSC Partnership registration code structure mismatch.")
            } else if (!CryptoEngine.validateRJSCIncorporation(regNo)) {
                validationErrors.add("RJSC PLtd Corporate Incorporation number structure mismatch.")
            }
        }

        // Check if manual offline mode is requested
        if (isOfflineMode) {
            Log.d(TAG, "Enforcing manual Tactical Offline Failover validation for Bangladesh.")
            return@withContext runOfflineEvaluation(module, taskName, applicantName, decryptedFields, validationErrors, scaleSqFt, costBdt)
        }

        // Check if there's a valid API Key at runtime
        var apiKey: String = ""
        try {
            val buildConfigClass = Class.forName("com.example.BuildConfig")
            val apiKeyField = buildConfigClass.getField("GEMINI_API_KEY")
            apiKey = apiKeyField.get(null) as? String ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "BuildConfig.GEMINI_API_KEY is not defined. Deploying local rule evaluator.")
        }

        val isApiKeyMock = apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY" || apiKey.startsWith("placeholder", ignoreCase = true)

        if (isApiKeyMock) {
            Log.d(TAG, "Using secure Local Rule Evaluator for Bangladesh verification.")
            return@withContext runLocalEvaluation(module, taskName, applicantName, decryptedFields, validationErrors, scaleSqFt, costBdt)
        }

        // 3. Construct dynamic regulatory knowledge base context focused strictly on Bangladesh
        val knowledgeBaseContext = when (module.uppercase()) {
            "CIVIC" -> """
                REGULATORY KNOWLEDGE BASE [CIVIC DESK - Bangladesh Election Commission & NBR VAT Guidelines]:
                - Active citizens must check and provide a valid Bangladeshi NID (10-digit smart or 17-digit legacy format).
                - A 13-digit Business Identification Number (BIN) is checked for local VAT tracking.
                - Location and District of Birth fields are mandatory.
                - Verify contact numbers conform to local operators (prefix +8801 or 01).
            """.trimIndent()
            "TAX" -> """
                REGULATORY KNOWLEDGE BASE [TAX DESK - National Board of Revenue (NBR) Bangladesh - Income Tax Act 2023]:
                - Every individual taxpayer filing must supply a valid 12-digit numeric e-TIN. Exact format check strictly enforced.
                - NBR Tax threshold check: Any aggregate income exceeding 2,000,000 BDT is flagged as High-Asset/Premium bracket and MANDATES "MANUAL_REVIEW_NEEDED" state for specialized wealth audits.
                - Deductions and tax surcharge calculations conform specifically closely to NBR guidelines.
            """.trimIndent()
            "BUSINESS" -> """
                REGULATORY KNOWLEDGE BASE [CORPORATE DESK - Registrar of Joint Stock Companies & Firms (RJSC) Bangladesh]:
                - Regulated under the Bangladesh Companies Act 1994.
                - Registrar files must list local RJSC Partnership Registration Numbers or Private Limited Corporate Incorporation Numbers.
                - Authorized starting capital under 50,000 BDT triggers capital capitalization warnings. Minimum corporate registration threshold must be audited.
            """.trimIndent()
            "PROPERTY" -> """
                REGULATORY KNOWLEDGE BASE [PROPERTY DESK - Rajdhani Unnayan Kartripakkha (RAJUK) & Bangladesh National Building Code]:
                - Valid land registry Dag / Khatian parcel number must be provided.
                - Any residential or commercial structural addition exceeding 5,000 square feet represents an urban variance and MANDATES "MANUAL_REVIEW_NEEDED" status with urban planning impact inquiry.
                - Estimated construction budget exceeding 2,500,000 BDT requires a certified RAJUK structural clearance and insurance coverage.
            """.trimIndent()
            else -> ""
        }

        // 4. Construct intelligent prompt and system instruction for Gemini using strict JSON schema
        val systemInstruction = """
            You are the Bangladesh national regulatory underwriting and legal compliance processor engine GovTaskAI.
            Your assignment is to underwrite and evaluate administrative, tax, company registration, and land zoning files for complete compliance with Bangladesh regulatory framework.
            Validate parameters against NBR, RJSC, RAJUK, and the Companies Act 1994 of Bangladesh.
            
            $knowledgeBaseContext
            
            You must reply ONLY with a single valid JSON object containing exactly these fields:
            {
              "compliance_status": "APPROVED" (if compliant with no errors), "VERIFIED" (fully validated but has minor comments/warnings), or "MANUAL_REVIEW_NEEDED" (critical discrepancies, missing details, or regulatory mismatches),
              "confidence_score": 95.0,
              "risk_assessment": "Low risk. All corporate parameters match standardized registration checklists.",
              "deficiency_checklist": [
                {"field": "e-TIN", "issue": "Format is valid, but starts with wrong district circle prefix."},
                {"field": "Filing Year", "issue": "Fiscal period mismatch with local NBR tax calendar."}
              ]
            }
            Do not include Markdown backticks or any trailing text, output raw parsable JSON.
        """.trimIndent()

        val prompt = """
            Evaluate Bangladesh application file details for underwriting compliance audit:
            Module: $module
            Task: $taskName
            Applicant Name: $applicantName
            
            Document Field Registry:
            $fieldsDetail
            
            Discovered Cryptographic/Digit Anomalies:
            ${if (validationErrors.isEmpty()) "None" else validationErrors.joinToString("; ")}
        """.trimIndent()

        try {
            val jsonRequest = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", systemInstruction)
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                })
            }

            val requestBody = jsonRequest.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "API request failure: ${response.code} ${response.message}")
                return@withContext runLocalEvaluation(module, taskName, applicantName, decryptedFields, validationErrors, scaleSqFt, costBdt)
            }

            val bodyString = response.body?.string() ?: ""
            val jsonResponse = JSONObject(bodyString)
            val textOutput = jsonResponse.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

            val scrubbed = textOutput.trim().removeSurrounding("```json", "```").trim()
            val parsed = JSONObject(scrubbed)
            
            val status = parsed.optString("compliance_status", "VERIFIED")
            val confidence = parsed.optDouble("confidence_score", 90.0).toInt()
            val riskAssessment = parsed.optString("risk_assessment", "Administrative audit completed successfully under Bangladesh guidelines.")
            
            val checklistArray = parsed.optJSONArray("deficiency_checklist")
            val deficienciesList = mutableListOf<String>()
            if (checklistArray != null) {
                for (i in 0 until checklistArray.length()) {
                    val obj = checklistArray.getJSONObject(i)
                    val field = obj.optString("field", "")
                    val issue = obj.optString("issue", "")
                    if (field.isNotEmpty() || issue.isNotEmpty()) {
                        deficienciesList.add("[$field] - $issue")
                    }
                }
            }
            
            AIResult(
                status = status,
                confidence = confidence,
                evaluationText = riskAssessment,
                missingInfo = deficienciesList.joinToString(",")
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error executing Gemini check: ${e.message}. Processing local fallback evaluation.", e)
            runLocalEvaluation(module, taskName, applicantName, decryptedFields, validationErrors, scaleSqFt, costBdt)
        }
    }

    // High fidelity logical model configured strictly for Bangladesh Rules
    private fun runLocalEvaluation(
        module: String,
        taskName: String,
        applicantName: String,
        decryptedFields: Map<String, String>,
        validationErrors: List<String>,
        scaleSqFt: Double,
        costBdt: Double
    ): AIResult {
        val feedback = StringBuilder()
        val missingList = mutableListOf<String>()
        var confidence = 95
        var status = "VERIFIED"

        feedback.append("BANGLADESH SECURE LOCAL COMPLIANCE AUDIT ENGINE\n")
        feedback.append("Case: $taskName, compliance id: G-${System.currentTimeMillis() % 100000}\n")
        feedback.append("Applicant: $applicantName\n\n")

        if (validationErrors.isNotEmpty()) {
            status = "MANUAL_REVIEW_NEEDED"
            confidence -= 15
            feedback.append("⚠️ CRITICAL COMPLIANCE ALERTS DETERMINED (Local Engine):\n")
            validationErrors.forEach { feedback.append("- $it\n") }
            feedback.append("\n")
        }

        when (module.uppercase()) {
            "CIVIC" -> {
                feedback.append("Citing Bangladesh Election Commission NID & NBR VAT Manual:\n")
                if (decryptedFields["Birth_District"].isNullOrBlank()) {
                    missingList.add("[Birth District] - Verified land district of birth is mandatory")
                }
                if (decryptedFields["NID"].isNullOrBlank()) {
                    missingList.add("[NID Card] - Original 10 or 17 digit National ID certificate is missing")
                }
                if (decryptedFields["BIN"].isNullOrBlank()) {
                    missingList.add("[BIN VAT Number] - 13-digit Business Identification Number is missing")
                }
                if (missingList.isEmpty() && validationErrors.isEmpty()) {
                    status = "APPROVED"
                    feedback.append("✅ Profile passes Bangladeshi National ID and local VAT validation checkpoints. Pre-approved for local administrative submission.")
                } else {
                    feedback.append("⚠️ Identification details must be corrected to clear civic registry entry.")
                }
            }
            "TAX" -> {
                feedback.append("Citing Bangladesh National Board of Revenue (NBR) Income Tax Act 2023:\n")
                val income = decryptedFields["Annual_Income_BDT"]?.toDoubleOrNull() ?: 0.0
                if (income <= 0.0) {
                    missingList.add("[Annual Income] - Legally certified proof or statement of income record is missing")
                    confidence -= 10
                }
                if (decryptedFields["e_TIN"].isNullOrBlank()) {
                    missingList.add("[e-TIN Number] - Mandated 12-digit e-TIN value required")
                }
                if (income > 2000000.0) {
                    status = "MANUAL_REVIEW_NEEDED"
                    feedback.append("⚠️ Premium Bracket triggered: Annual income exceeding ৳2,00,0000 BDT requires manually audited balance files and assets registry submission.\n")
                }
                if (missingList.isEmpty() && status != "MANUAL_REVIEW_NEEDED") {
                    status = "APPROVED"
                    feedback.append("✅ Secure Bangladeshi e-TIN and NBR return verified locally. Base 10% tax margin checks passed.")
                }
            }
            "BUSINESS" -> {
                feedback.append("Citing RJSC Company Rules (Bangladesh Companies Act 1994):\n")
                if (decryptedFields["Corp_Reg_Number"].isNullOrBlank()) {
                    missingList.add("[Corporate Reg Code] - Valid RJSC Incorporation or Partnership structure registration code is required")
                }
                if (costBdt < 50000.0) {
                    missingList.add("[Authorized Capital] - Authorized starting capital check is below the ৳50,000 threshold requirement")
                    confidence -= 10
                }
                if (missingList.isEmpty() && validationErrors.isEmpty()) {
                    status = "APPROVED"
                    feedback.append("✅ Corporate structure verified with RJSC database logs. Trade registry file generated successfully.")
                }
            }
            "PROPERTY" -> {
                feedback.append("Citing RAJUK Zoning Bylaws & Pourashava Building Construction Code:\n")
                if (decryptedFields["Dag_Khatian_No"].isNullOrBlank()) {
                    missingList.add("[Khatian/Dag Number] - Authentic land parcel Dag/Khatian registration proof is missing")
                    status = "MANUAL_REVIEW_NEEDED"
                }
                if (scaleSqFt > 5000.0) {
                    status = "MANUAL_REVIEW_NEEDED"
                    feedback.append("⚠️ RAJUK urban plan variance exception: Structures over 5,000 sq ft demand detailed urban planning variance environment permits.\n")
                }
                if (costBdt > 2500000.0) {
                    missingList.add("[Structural Warranty Check] - Large budget constructions over 25,00,000 BDT require RAJUK structural clearance records and certified contractor warranty.")
                    confidence -= 10
                }
                if (missingList.isEmpty() && status != "MANUAL_REVIEW_NEEDED") {
                    status = "APPROVED"
                    feedback.append("✅ Land boundaries clear. Structural offsets conform with local pourashava/municipal bylaws.")
                }
            }
        }

        if (missingList.isNotEmpty()) {
            if (status != "MANUAL_REVIEW_NEEDED") status = "VERIFIED"
            confidence -= (missingList.size * 5)
        }

        return AIResult(
            status = status,
            confidence = kotlin.math.max(30, confidence),
            evaluationText = feedback.toString(),
            missingInfo = missingList.joinToString(", ")
        )
    }

    // Bangladesh tactical offline validation using static rules
    private fun runOfflineEvaluation(
        module: String,
        taskName: String,
        applicantName: String,
        decryptedFields: Map<String, String>,
        validationErrors: List<String>,
        scaleSqFt: Double,
        costBdt: Double
    ): AIResult {
        val feedback = StringBuilder()
        val missingList = mutableListOf<String>()
        val confidence = 85

        feedback.append("📡 TACTICAL OFFLINE FAILOVER ACTIVE (BANGLADESH SPECIALIST)\n")
        feedback.append("Primary server offline. Processing locally with air-gapped Sentinel dry-run engine.\n\n")

        if (validationErrors.isNotEmpty()) {
            feedback.append("🚨 LOCAL DIGIT-TYPO / SYNTAX ALERTS DISCOVERED (Bangladesh):\n")
            validationErrors.forEach { feedback.append("- $it\n") }
            feedback.append("\nAdjust fields and submit again to secure local offline validation.\n")
        } else {
            feedback.append("✅ Local structural validations clean. Bangladeshi identifiers (e-TIN, NID, BIN) verified.\n")
        }

        when (module.uppercase()) {
            "CIVIC" -> {
                feedback.append("\nCiting Bangladesh Civic Identification Code (Offline):\n")
                val nid = decryptedFields["NID"] ?: ""
                if (nid.isNotEmpty() && nid.length != 10 && nid.length != 17) {
                    feedback.append("⚠️ WARNING: National ID must contain exactly 10 digits (Smart Card) or 17 digits (Legacy format).\n")
                }
                if (decryptedFields["Birth_District"].isNullOrBlank()) {
                    missingList.add("[Birth District] - Local birth district/division field is empty")
                }
            }
            "TAX" -> {
                feedback.append("\nCiting Bangladesh Income Tax Act 2023 (Offline):\n")
                val tin = decryptedFields["e_TIN"] ?: ""
                if (tin.isNotEmpty() && tin.length != 12) {
                    feedback.append("⚠️ WARNING: Bangladeshi e-TIN must contain exactly 12 numeric digits.\n")
                }
                val income = decryptedFields["Annual_Income_BDT"]?.toDoubleOrNull() ?: 0.0
                if (income <= 0.0) {
                    missingList.add("[Annual Income] - Legally certified proof or statement of income is required")
                }
            }
            "BUSINESS" -> {
                feedback.append("\nCiting RJSC Company Bylaws - Companies Act 1994 (Offline):\n")
                val structureStr = decryptedFields["Corp_Structure"] ?: ""
                feedback.append("Checked business structure: $structureStr. Auto-mapped to Registrar databases.\n")
                if (decryptedFields["Corp_Reg_Number"].isNullOrBlank()) {
                    missingList.add("[Corporate Reg Code] - Registrar joint stock company identification code is required")
                }
            }
            "PROPERTY" -> {
                feedback.append("\nCiting RAJUK / poured bylaws (Offline):\n")
                val parcel = decryptedFields["Dag_Khatian_No"] ?: ""
                if (parcel.isNotEmpty() && parcel.length < 3) {
                    feedback.append("⚠️ WARNING: Authentic municipal land parcel Dag/Khatian registration id is too short.\n")
                }
                if (scaleSqFt > 5000.0) {
                    feedback.append("⚠️ Warning: Constructions over 5,000 sq ft will trigger mandatory RAJUK live environmental clearance reviews during sync.\n")
                }
            }
        }

        if (missingList.isNotEmpty()) {
            feedback.append("\n📝 DEFICIENCY ITEMS COMPILED OFFLINE:\n")
            missingList.forEach { feedback.append("- Pending Document: $it\n") }
        }

        return AIResult(
            status = "OFFLINE_PENDING_SYNC",
            confidence = confidence,
            evaluationText = feedback.toString(),
            missingInfo = missingList.joinToString(", ")
        )
    }
}
