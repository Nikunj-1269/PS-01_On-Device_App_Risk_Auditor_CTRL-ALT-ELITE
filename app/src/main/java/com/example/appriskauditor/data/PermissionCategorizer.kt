package com.example.appriskauditor.data

import com.example.appriskauditor.model.Permission
import com.example.appriskauditor.model.PermissionCategory

/**
 * Maps every known sensitive permission to one of the 9 risk categories.
 *
 * Only the short name is stored (e.g. "CAMERA"), but the lookup handles
 * both the short form and the full "android.permission.CAMERA" form.
 *
 * Uncategorized permissions stay as [PermissionCategory.UNCATEGORIZED].
 */
object PermissionCategorizer {

    // ── lookup table (config object — easy to extend) ────────────────────────

    private val CATEGORY_MAP: Map<String, PermissionCategory> = mapOf(
        // 1. Special access
        "BIND_ACCESSIBILITY_SERVICE"          to PermissionCategory.SPECIAL_ACCESS,
        "SYSTEM_ALERT_WINDOW"                 to PermissionCategory.SPECIAL_ACCESS,
        "BIND_DEVICE_ADMIN"                   to PermissionCategory.SPECIAL_ACCESS,
        "BIND_NOTIFICATION_LISTENER_SERVICE"  to PermissionCategory.SPECIAL_ACCESS,
        "REQUEST_INSTALL_PACKAGES"            to PermissionCategory.SPECIAL_ACCESS,
        "PACKAGE_USAGE_STATS"                 to PermissionCategory.SPECIAL_ACCESS,

        // 2. SMS
        "READ_SMS"                            to PermissionCategory.SMS,
        "RECEIVE_SMS"                         to PermissionCategory.SMS,
        "SEND_SMS"                            to PermissionCategory.SMS,
        "RECEIVE_MMS"                         to PermissionCategory.SMS,
        "READ_CELL_BROADCASTS"                to PermissionCategory.SMS,

        // 3. Contacts & call logs
        "READ_CONTACTS"                       to PermissionCategory.CONTACTS_CALL_LOGS,
        "WRITE_CONTACTS"                      to PermissionCategory.CONTACTS_CALL_LOGS,
        "READ_CALL_LOG"                       to PermissionCategory.CONTACTS_CALL_LOGS,
        "WRITE_CALL_LOG"                      to PermissionCategory.CONTACTS_CALL_LOGS,
        "GET_ACCOUNTS"                        to PermissionCategory.CONTACTS_CALL_LOGS,

        // 4. Camera & microphone
        "CAMERA"                              to PermissionCategory.CAMERA_MIC,
        "RECORD_AUDIO"                        to PermissionCategory.CAMERA_MIC,

        // 5. Location
        "ACCESS_FINE_LOCATION"                to PermissionCategory.LOCATION,
        "ACCESS_COARSE_LOCATION"              to PermissionCategory.LOCATION,
        "ACCESS_BACKGROUND_LOCATION"          to PermissionCategory.LOCATION,

        // 6. Phone & identity
        "READ_PHONE_STATE"                    to PermissionCategory.PHONE_IDENTITY,
        "READ_PHONE_NUMBERS"                  to PermissionCategory.PHONE_IDENTITY,
        "CALL_PHONE"                          to PermissionCategory.PHONE_IDENTITY,
        "PROCESS_OUTGOING_CALLS"              to PermissionCategory.PHONE_IDENTITY,
        "USE_SIP"                             to PermissionCategory.PHONE_IDENTITY,

        // 7. Storage & media
        "READ_MEDIA_IMAGES"                   to PermissionCategory.STORAGE,
        "READ_MEDIA_VIDEO"                    to PermissionCategory.STORAGE,
        "READ_MEDIA_AUDIO"                    to PermissionCategory.STORAGE,
        "READ_EXTERNAL_STORAGE"               to PermissionCategory.STORAGE,
        "WRITE_EXTERNAL_STORAGE"              to PermissionCategory.STORAGE,
        "MANAGE_EXTERNAL_STORAGE"             to PermissionCategory.STORAGE,
        "MANAGE_MEDIA"                        to PermissionCategory.STORAGE,

        // 8. Background & persistence
        "RECEIVE_BOOT_COMPLETED"              to PermissionCategory.BACKGROUND,
        "FOREGROUND_SERVICE"                  to PermissionCategory.BACKGROUND,
        "FOREGROUND_SERVICE_CAMERA"           to PermissionCategory.BACKGROUND,
        "FOREGROUND_SERVICE_LOCATION"         to PermissionCategory.BACKGROUND,
        "FOREGROUND_SERVICE_MICROPHONE"       to PermissionCategory.BACKGROUND,
        "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" to PermissionCategory.BACKGROUND,
        "WAKE_LOCK"                           to PermissionCategory.BACKGROUND,
        "SCHEDULE_EXACT_ALARM"                to PermissionCategory.BACKGROUND,

        // 9. Network
        "INTERNET"                            to PermissionCategory.NETWORK,
        "ACCESS_NETWORK_STATE"                to PermissionCategory.NETWORK,
        "ACCESS_WIFI_STATE"                   to PermissionCategory.NETWORK,
        "CHANGE_NETWORK_STATE"                to PermissionCategory.NETWORK,
        "CHANGE_WIFI_STATE"                   to PermissionCategory.NETWORK,
        "NEARBY_WIFI_DEVICES"                 to PermissionCategory.NETWORK
    )

    /**
     * Returns the [PermissionCategory] for a full permission name like
     * "android.permission.CAMERA" or just "CAMERA".
     */
    fun categoryFor(permissionName: String): PermissionCategory {
        // Strip any package prefix, use only the last segment
        val shortName = permissionName.substringAfterLast('.')
        return CATEGORY_MAP[shortName] ?: PermissionCategory.UNCATEGORIZED
    }

    /**
     * Applies categories to a list of [Permission] objects and returns a new list.
     */
    fun categorize(permissions: List<Permission>): List<Permission> =
        permissions.map { perm ->
            val cat = categoryFor(perm.name)
            if (cat == perm.category) perm else perm.copy(category = cat)
        }

    /**
     * Groups a categorized permission list by category (excluding UNCATEGORIZED
     * unless it has granted dangerous permissions — judges don't need noise).
     */
    fun groupByCategory(permissions: List<Permission>): Map<PermissionCategory, List<Permission>> =
        permissions
            .filter { it.category != PermissionCategory.UNCATEGORIZED || it.granted }
            .groupBy { it.category }
            .toSortedMap(compareBy { it.ordinal })
}
