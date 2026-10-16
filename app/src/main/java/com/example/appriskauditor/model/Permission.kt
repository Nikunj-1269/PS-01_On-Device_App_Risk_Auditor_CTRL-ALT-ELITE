package com.example.appriskauditor.model

/**
 * Protection level bucket for a permission.
 * We collapse Android's raw flags into four readable levels.
 */
enum class ProtectionLevel {
    NORMAL,      // low risk, auto-granted
    DANGEROUS,   // user must approve at runtime
    SIGNATURE,   // granted only to same-signature apps
    SPECIAL      // appOps / special-access permissions
}

/**
 * High-level category used for risk scoring (Step 3 fills the lookup table).
 * UNCATEGORIZED is the default until the lookup is applied.
 */
enum class PermissionCategory {
    SPECIAL_ACCESS,
    SMS,
    CONTACTS_CALL_LOGS,
    CAMERA_MIC,
    LOCATION,
    PHONE_IDENTITY,
    STORAGE,
    BACKGROUND,
    NETWORK,
    UNCATEGORIZED
}

/**
 * A single permission entry for one app.
 *
 * @param name          full permission name, e.g. "android.permission.CAMERA"
 * @param granted       true if the permission is currently granted to the app
 * @param protectionLevel bucketed protection level
 * @param category      semantic category (filled by PermissionCategorizer in Step 3)
 */
data class Permission(
    val name: String,
    val granted: Boolean,
    val protectionLevel: ProtectionLevel,
    val category: PermissionCategory = PermissionCategory.UNCATEGORIZED
)
