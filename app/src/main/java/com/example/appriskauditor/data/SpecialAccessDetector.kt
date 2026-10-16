package com.example.appriskauditor.data

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.example.appriskauditor.model.SpecialAccess
import com.example.appriskauditor.model.SpecialAccessType

/**
 * Detects whether an app holds any of the 6 special OS-level access types.
 *
 * Each item is either:
 *   active = true  → currently enabled / granted (counts heavily in the score)
 *   active = false → only declared in the manifest (counts less)
 *
 * All calls are wrapped in runCatching so a SecurityException on a
 * locked-down device never crashes the app.
 */
class SpecialAccessDetector(private val context: Context) {

    private val pm: PackageManager get() = context.packageManager

    // Cached once per detector instance (stable for the lifetime of one scan)
    private val enabledA11yServices: Set<String> by lazy { fetchEnabledA11yServices() }
    private val activeDeviceAdmins: Set<String> by lazy { fetchActiveDeviceAdmins() }
    private val enabledNotifListeners: Set<String> by lazy { fetchEnabledNotifListeners() }

    /**
     * Returns the list of [SpecialAccess] items that apply to [packageName].
     * Returns an empty list if the app has no special access of any kind.
     */
    fun detect(packageName: String): List<SpecialAccess> {
        val result = mutableListOf<SpecialAccess>()

        // 1. Accessibility service
        val a11yDeclared = isPermissionDeclared(packageName, "android.permission.BIND_ACCESSIBILITY_SERVICE")
        if (a11yDeclared) {
            val active = enabledA11yServices.any { it.startsWith("$packageName/") }
            result += SpecialAccess(
                type   = SpecialAccessType.ACCESSIBILITY_SERVICE,
                active = active,
                label  = if (active)
                    "Accessibility service ACTIVE — can read all screen content"
                else
                    "Accessibility service declared (not currently enabled)"
            )
        }

        // 2. Device admin
        val adminDeclared = isPermissionDeclared(packageName, "android.permission.BIND_DEVICE_ADMIN")
        if (adminDeclared) {
            val active = activeDeviceAdmins.contains(packageName)
            result += SpecialAccess(
                type   = SpecialAccessType.DEVICE_ADMIN,
                active = active,
                label  = if (active)
                    "Device administrator ACTIVE — can lock or wipe your device"
                else
                    "Device admin declared (not currently active)"
            )
        }

        // 3. Notification listener
        val notifDeclared = isPermissionDeclared(packageName, "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE")
        if (notifDeclared) {
            val active = enabledNotifListeners.any { it.startsWith("$packageName/") }
            result += SpecialAccess(
                type   = SpecialAccessType.NOTIFICATION_LISTENER,
                active = active,
                label  = if (active)
                    "Notification listener ACTIVE — can read all your notifications"
                else
                    "Notification listener declared (not currently enabled)"
            )
        }

        // 4. Overlay (SYSTEM_ALERT_WINDOW)
        val overlayDeclared = isPermissionDeclared(packageName, "android.permission.SYSTEM_ALERT_WINDOW")
        if (overlayDeclared) {
            val active = checkAppOp(packageName, AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW)
            result += SpecialAccess(
                type   = SpecialAccessType.OVERLAY,
                active = active,
                label  = if (active)
                    "Can draw over other apps — could hide phishing screens on top of real apps"
                else
                    "Overlay permission declared (not currently allowed)"
            )
        }

        // 5. Install packages
        val installDeclared = isPermissionDeclared(packageName, "android.permission.REQUEST_INSTALL_PACKAGES")
        if (installDeclared) {
            val active = checkAppOp(packageName, AppOpsManager.OPSTR_REQUEST_INSTALL_PACKAGES)
            result += SpecialAccess(
                type   = SpecialAccessType.INSTALL_PACKAGES,
                active = active,
                label  = if (active)
                    "Can install other apps — could silently install malware"
                else
                    "Install-packages declared (not currently allowed)"
            )
        }

        // 6. Usage access (PACKAGE_USAGE_STATS)
        val usageDeclared = isPermissionDeclared(packageName, "android.permission.PACKAGE_USAGE_STATS")
        if (usageDeclared) {
            val active = checkAppOp(packageName, AppOpsManager.OPSTR_GET_USAGE_STATS)
            result += SpecialAccess(
                type   = SpecialAccessType.USAGE_ACCESS,
                active = active,
                label  = if (active)
                    "Can see which apps you use and when — detailed behavioural profiling"
                else
                    "Usage access declared (not currently granted)"
            )
        }

        return result
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun isPermissionDeclared(pkg: String, permName: String): Boolean = runCatching {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(
                pkg,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
        }
        info.requestedPermissions?.contains(permName) == true
    }.getOrDefault(false)

    /** Returns true when AppOpsManager reports the op as MODE_ALLOWED for the package. */
    private fun checkAppOp(pkg: String, opStr: String): Boolean = runCatching {
        val uid = pm.getApplicationInfo(pkg, 0).uid
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(opStr, uid, pkg)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(opStr, uid, pkg)
        }
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    private fun fetchEnabledA11yServices(): Set<String> = runCatching {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .mapNotNull { it.resolveInfo?.serviceInfo?.let { si -> "${si.packageName}/${si.name}" } }
            .toSet()
    }.getOrDefault(emptySet())

    private fun fetchActiveDeviceAdmins(): Set<String> = runCatching {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        dpm.activeAdmins
            ?.map { it.packageName }
            ?.toSet()
            ?: emptySet()
    }.getOrDefault(emptySet())

    private fun fetchEnabledNotifListeners(): Set<String> = runCatching {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return@runCatching emptySet<String>()
        // Format: "pkg/ComponentName:pkg2/ComponentName2"
        flat.split(":").filter { it.isNotBlank() }.toSet()
    }.getOrDefault(emptySet())
}
