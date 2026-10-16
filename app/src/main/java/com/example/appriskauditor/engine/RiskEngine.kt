package com.example.appriskauditor.engine

import com.example.appriskauditor.model.AppInfo
import com.example.appriskauditor.model.InstallerSource
import com.example.appriskauditor.model.Permission
import com.example.appriskauditor.model.PermissionCategory
import com.example.appriskauditor.model.RiskBand
import com.example.appriskauditor.model.RiskResult
import com.example.appriskauditor.model.SpecialAccess
import com.example.appriskauditor.model.SpecialAccessType

/**
 * Pure-Kotlin risk scoring engine.
 *
 * No Android framework imports — can be unit-tested on the JVM directly.
 *
 * All weights live in [Weights] so they are easy to tune in one place.
 */
object RiskEngine {

    // ── Weight configuration object ──────────────────────────────────────────

    object Weights {
        // Special access
        const val SPECIAL_ACCESS_ACTIVE   = 20
        const val SPECIAL_ACCESS_DECLARED = 8

        // Permission categories (full weight = granted dangerous perm)
        const val SMS               = 15
        const val CONTACTS          = 10
        const val CAMERA_MIC        = 10
        const val LOCATION          = 8
        const val LOCATION_BG_BONUS = 5   // extra when ACCESS_BACKGROUND_LOCATION granted
        const val PHONE_IDENTITY    = 6
        const val STORAGE           = 6
        const val STORAGE_MANAGE_BONUS = 4  // extra for MANAGE_EXTERNAL_STORAGE
        const val BACKGROUND        = 4
        const val NETWORK           = 1

        // Declared-but-not-granted multiplier (30% of full weight)
        const val DECLARED_ONLY_FACTOR = 0.30

        // Combination rule bonuses (Step 7 — included here as config)
        const val COMBO_SMS_INTERNET_BOOT_SIDELOADED = 15
        const val COMBO_A11Y_OVERLAY                 = 12
        const val COMBO_DEVICE_ADMIN_NOT_TRUSTED     = 10

        const val MAX_SCORE = 100
    }

    // ── Main entry point ─────────────────────────────────────────────────────

    /**
     * Scores [app] and returns a [RiskResult] with score, band, and reasons.
     *
     * @param app fully-populated AppInfo (permissions + specialAccess filled)
     */
    fun score(app: AppInfo): RiskResult {
        var total = 0
        val reasons = mutableListOf<String>()

        // 1. Special access
        for (sa in app.specialAccess) {
            if (sa.active) {
                total += Weights.SPECIAL_ACCESS_ACTIVE
                reasons += specialAccessActiveReason(sa)
            } else {
                total += Weights.SPECIAL_ACCESS_DECLARED
                // Declared-only: add reason only for high-impact types
                if (sa.type in HIGH_IMPACT_SPECIAL) {
                    reasons += "${sa.label} (not yet active, but declared)"
                }
            }
        }

        // 2. Permission categories
        total += scoreCategory(app, PermissionCategory.SMS, Weights.SMS, reasons,
            grantedReason = "Can read, receive, or send SMS — this can expose one-time passwords and intercept 2FA codes.",
            declaredReason = "Declares SMS access (not yet granted).")

        total += scoreCategory(app, PermissionCategory.CONTACTS_CALL_LOGS, Weights.CONTACTS, reasons,
            grantedReason = "Can access your contacts and call history — your social network is exposed.",
            declaredReason = "Declares contacts/call-log access (not yet granted).")

        total += scoreCategory(app, PermissionCategory.CAMERA_MIC, Weights.CAMERA_MIC, reasons,
            grantedReason = "Can use the camera or microphone — could record you without your knowledge.",
            declaredReason = "Declares camera/microphone access (not yet granted).")

        // Location — check for background specifically
        val locationScore = scoreCategory(app, PermissionCategory.LOCATION, Weights.LOCATION, reasons,
            grantedReason = "Can track your location.",
            declaredReason = "Declares location access (not yet granted).")
        total += locationScore
        if (locationScore > 0) {
            val bgGranted = app.permissions.any {
                it.category == PermissionCategory.LOCATION &&
                it.name.endsWith("ACCESS_BACKGROUND_LOCATION") &&
                it.granted
            }
            if (bgGranted) {
                total += Weights.LOCATION_BG_BONUS
                reasons += "Can track your location even when you're not using the app (background location)."
            }
        }

        total += scoreCategory(app, PermissionCategory.PHONE_IDENTITY, Weights.PHONE_IDENTITY, reasons,
            grantedReason = "Can read your phone number, IMEI, or make calls — your identity may be exposed.",
            declaredReason = "Declares phone/identity access (not yet granted).")

        // Storage — check for MANAGE_EXTERNAL_STORAGE bonus
        val storageScore = scoreCategory(app, PermissionCategory.STORAGE, Weights.STORAGE, reasons,
            grantedReason = "Can access files on your device.",
            declaredReason = "Declares storage access (not yet granted).")
        total += storageScore
        if (storageScore > 0) {
            val manageGranted = app.permissions.any {
                it.name.endsWith("MANAGE_EXTERNAL_STORAGE") && it.granted
            }
            if (manageGranted) {
                total += Weights.STORAGE_MANAGE_BONUS
                reasons += "Has unrestricted access to ALL files on your device (MANAGE_EXTERNAL_STORAGE)."
            }
        }

        total += scoreCategory(app, PermissionCategory.BACKGROUND, Weights.BACKGROUND, reasons,
            grantedReason = "Can start automatically when the phone boots or run persistently in the background.",
            declaredReason = "Declares background/persistence access (not yet granted).")

        total += scoreCategory(app, PermissionCategory.NETWORK, Weights.NETWORK, reasons,
            grantedReason = "Has internet access — can send data off your device.",
            declaredReason = null)  // Network alone is too common to mention as declared-only

        // 3. Combination rules (Step 7a)
        val comboReasons = checkCombinationRules(app)
        for ((bonus, reason) in comboReasons) {
            total += bonus
            reasons += reason
        }

        // 4. Cap score and compute band
        val finalScore = total.coerceIn(0, Weights.MAX_SCORE)
        val band = RiskResult.bandFor(finalScore)

        // Ensure High/Critical apps always have ≥3 reasons
        if (band >= RiskBand.HIGH && reasons.size < 3) {
            reasons += buildExtraReasons(app, 3 - reasons.size)
        }

        return RiskResult(
            score   = finalScore,
            band    = band,
            reasons = reasons.distinct().take(10)   // cap UI noise at 10
        )
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Scores one permission category.
     * - Granted dangerous perms count at full weight.
     * - Declared-but-not-granted count at DECLARED_ONLY_FACTOR of full weight.
     * Returns the points added (so callers can apply bonuses on top).
     */
    private fun scoreCategory(
        app: AppInfo,
        category: PermissionCategory,
        fullWeight: Int,
        reasons: MutableList<String>,
        grantedReason: String,
        declaredReason: String?
    ): Int {
        val inCategory = app.permissions.filter { it.category == category }
        if (inCategory.isEmpty()) return 0

        val hasGranted   = inCategory.any { it.granted }
        val hasDeclared  = inCategory.isNotEmpty()

        return when {
            hasGranted -> {
                reasons += grantedReason
                fullWeight
            }
            hasDeclared && declaredReason != null -> {
                reasons += declaredReason
                (fullWeight * Weights.DECLARED_ONLY_FACTOR).toInt()
            }
            else -> 0
        }
    }

    /**
     * Combination rule checks — returns list of (bonus, reason) pairs.
     */
    private fun checkCombinationRules(app: AppInfo): List<Pair<Int, String>> {
        val result = mutableListOf<Pair<Int, String>>()
        val grantedNames = app.permissions.filter { it.granted }.map { shortName(it.name) }.toSet()

        // Rule 1: SMS exfiltration pattern
        if ("READ_SMS" in grantedNames || "RECEIVE_SMS" in grantedNames) {
            if ("INTERNET" in grantedNames && "RECEIVE_BOOT_COMPLETED" in grantedNames) {
                if (app.installerSource == InstallerSource.SIDELOADED) {
                    result += Weights.COMBO_SMS_INTERNET_BOOT_SIDELOADED to
                        "⚠ High-risk combination: reads SMS + has internet + starts on boot + was sideloaded — classic spyware pattern."
                }
            }
        }

        // Rule 2: Accessibility + overlay
        val hasA11yActive = app.specialAccess.any {
            it.type == SpecialAccessType.ACCESSIBILITY_SERVICE && it.active
        }
        val hasOverlay = app.specialAccess.any { it.type == SpecialAccessType.OVERLAY }
        if (hasA11yActive && hasOverlay) {
            result += Weights.COMBO_A11Y_OVERLAY to
                "⚠ High-risk combination: active accessibility service + overlay access — can read your screen and draw fake UI on top of real apps."
        }

        // Rule 3: Device admin on a non-trusted app
        val hasActiveAdmin = app.specialAccess.any {
            it.type == SpecialAccessType.DEVICE_ADMIN && it.active
        }
        if (hasActiveAdmin && app.installerSource != InstallerSource.PLAY_STORE) {
            result += Weights.COMBO_DEVICE_ADMIN_NOT_TRUSTED to
                "⚠ Active device administrator from an untrusted source — can lock your screen or wipe the device."
        }

        return result
    }

    /** Generates filler reasons for High/Critical apps that have fewer than 3 so far. */
    private fun buildExtraReasons(app: AppInfo, needed: Int): List<String> {
        val extras = mutableListOf<String>()
        if (extras.size < needed && app.installerSource == InstallerSource.SIDELOADED) {
            extras += "This app was sideloaded (not installed from the Play Store), which increases risk."
        }
        if (extras.size < needed && app.targetSdk < 28) {
            extras += "Targets an old Android API (SDK ${app.targetSdk}) — may bypass modern privacy protections."
        }
        val dangerousCount = app.permissions.count { it.granted }
        if (extras.size < needed && dangerousCount > 10) {
            extras += "Holds an unusually large number of granted permissions ($dangerousCount total)."
        }
        return extras
    }

    private fun specialAccessActiveReason(sa: SpecialAccess): String = when (sa.type) {
        SpecialAccessType.ACCESSIBILITY_SERVICE ->
            "Active accessibility service — can read everything on your screen, including passwords and messages."
        SpecialAccessType.DEVICE_ADMIN ->
            "Active device administrator — can remotely lock or wipe your phone."
        SpecialAccessType.NOTIFICATION_LISTENER ->
            "Active notification listener — reads every notification, including OTP codes and chat messages."
        SpecialAccessType.OVERLAY ->
            "Can draw over other apps — could display a fake login screen on top of your banking app."
        SpecialAccessType.INSTALL_PACKAGES ->
            "Can silently install other apps onto your device."
        SpecialAccessType.USAGE_ACCESS ->
            "Can see which apps you use and when — enables detailed behavioural profiling."
    }

    private fun shortName(fullName: String) = fullName.substringAfterLast('.')

    // Special-access types that merit a reason even when only declared
    private val HIGH_IMPACT_SPECIAL = setOf(
        SpecialAccessType.ACCESSIBILITY_SERVICE,
        SpecialAccessType.DEVICE_ADMIN,
        SpecialAccessType.NOTIFICATION_LISTENER
    )
}
