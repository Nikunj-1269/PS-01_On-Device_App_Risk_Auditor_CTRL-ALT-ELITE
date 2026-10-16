package com.example.appriskauditor.model

import android.graphics.drawable.Drawable

/**
 * Source from which an app was installed.
 */
enum class InstallerSource {
    PLAY_STORE,
    SIDELOADED,   // installed via ADB or unknown source
    OTHER         // system, OEM, or known enterprise store
}

/**
 * Snapshot of one installed app, including all extracted permissions.
 * Collected in Step 1; permissions filled in Step 2.
 */
data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val versionName: String,
    val versionCode: Long,
    val installTime: Long,      // epoch millis
    val lastUpdateTime: Long,   // epoch millis
    val installerSource: InstallerSource,
    val targetSdk: Int,
    val isSystemApp: Boolean,
    /** All permissions declared by the app (granted + not granted). */
    val permissions: List<Permission> = emptyList(),
    /** Special OS-level access items detected for this app. */
    val specialAccess: List<SpecialAccess> = emptyList(),
    /** Risk score computed by [RiskEngine]. Null until scoring is done. */
    val riskResult: RiskResult? = null
)
