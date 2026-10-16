package com.example.appriskauditor.model

/**
 * The five special-access types we check per app.
 */
enum class SpecialAccessType {
    ACCESSIBILITY_SERVICE,
    DEVICE_ADMIN,
    NOTIFICATION_LISTENER,
    OVERLAY,              // SYSTEM_ALERT_WINDOW
    INSTALL_PACKAGES,     // REQUEST_INSTALL_PACKAGES
    USAGE_ACCESS          // PACKAGE_USAGE_STATS
}

/**
 * State of one special-access capability for a given app.
 *
 * @param type      which capability
 * @param active    true  = actually enabled/granted right now (highest risk)
 *                  false = only declared in manifest, not active (lower risk)
 * @param label     short human-readable description shown in the UI
 */
data class SpecialAccess(
    val type: SpecialAccessType,
    val active: Boolean,
    val label: String
)
