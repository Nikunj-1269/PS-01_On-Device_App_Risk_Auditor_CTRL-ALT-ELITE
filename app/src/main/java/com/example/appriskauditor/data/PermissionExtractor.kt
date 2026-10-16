package com.example.appriskauditor.data

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.GET_PERMISSIONS
import android.content.pm.PermissionInfo
import android.os.Build
import com.example.appriskauditor.model.Permission
import com.example.appriskauditor.model.PermissionCategory
import com.example.appriskauditor.model.ProtectionLevel

/**
 * Extracts and classifies all permissions declared by a package.
 *
 * Android's requestedPermissionsFlags bitmask tells us whether each
 * permission in requestedPermissions[] is currently granted.
 *
 * Protection-level mapping:
 *   normal       → ProtectionLevel.NORMAL
 *   dangerous    → ProtectionLevel.DANGEROUS
 *   signature*   → ProtectionLevel.SIGNATURE
 *   appOp/preinstalled/development/… → ProtectionLevel.SPECIAL
 */
class PermissionExtractor(private val pm: PackageManager) {

    /**
     * Returns the list of [Permission] objects for the given [packageName].
     * Never throws; returns an empty list on any error.
     */
    fun extract(packageName: String): List<Permission> {
        val pkgInfo = getPackageInfo(packageName) ?: return emptyList()

        val names  = pkgInfo.requestedPermissions ?: return emptyList()
        val flags  = pkgInfo.requestedPermissionsFlags ?: IntArray(names.size)

        return names.mapIndexed { i, name ->
            val granted = (flags.getOrElse(i) { 0 } and
                    PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            val level   = resolveProtectionLevel(name)
            Permission(
                name            = name,
                granted         = granted,
                protectionLevel = level,
                // Category is filled later by PermissionCategorizer (Step 3)
                category        = PermissionCategory.UNCATEGORIZED
            )
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun getPackageInfo(pkg: String): PackageInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(GET_PERMISSIONS.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, GET_PERMISSIONS)
        }
    }.getOrNull()

    /**
     * Queries PackageManager for a permission's protection level.
     * Custom / unknown permissions (e.g. declared by another app but not installed)
     * are silently caught and returned as NORMAL so the app never crashes.
     */
    private fun resolveProtectionLevel(permName: String): ProtectionLevel {
        val info = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPermissionInfo(permName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPermissionInfo(permName, 0)
            }
        }.getOrNull() ?: return ProtectionLevel.NORMAL   // unknown → treat as normal

        // protectionLevel field encodes base level + flags in one int.
        // Mask out the flags to get just the base level.
        val base = info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE
        return when (base) {
            PermissionInfo.PROTECTION_DANGEROUS  -> ProtectionLevel.DANGEROUS
            PermissionInfo.PROTECTION_SIGNATURE  -> ProtectionLevel.SIGNATURE
            PermissionInfo.PROTECTION_NORMAL     -> ProtectionLevel.NORMAL
            else                                  -> ProtectionLevel.SPECIAL
        }
    }
}
