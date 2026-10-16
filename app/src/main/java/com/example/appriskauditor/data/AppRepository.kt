package com.example.appriskauditor.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.example.appriskauditor.engine.RiskEngine
import com.example.appriskauditor.model.AppInfo
import com.example.appriskauditor.model.InstallerSource
import com.example.appriskauditor.model.Permission

/**
 * Reads installed packages from PackageManager and converts them to [AppInfo].
 * All work happens on the calling coroutine – call from a background dispatcher.
 */
class AppRepository(private val context: Context) {

    private val pm: PackageManager get() = context.packageManager
    private val permissionExtractor by lazy { PermissionExtractor(pm) }
    // SpecialAccessDetector caches expensive system calls; keep one instance per scan
    private val specialAccessDetector by lazy { SpecialAccessDetector(context) }

    /**
     * Returns the list of installed apps.
     * @param includeSystem when false (default) filters out system apps.
     */
    fun getInstalledApps(includeSystem: Boolean = false): List<AppInfo> {
        val flags = PackageManager.GET_META_DATA
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(flags)
        }

        return packages
            .filter { pkg ->
                val isSystem = (pkg.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                includeSystem || !isSystem
            }
            .map { pkg ->
                val appInfo = pkg.applicationInfo
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                // Extract + categorize permissions (Steps 2 & 3)
                val permissions: List<Permission> =
                    PermissionCategorizer.categorize(
                        permissionExtractor.extract(pkg.packageName)
                    )

                // Detect special access (Step 4)
                val specialAccess = specialAccessDetector.detect(pkg.packageName)

                // Build AppInfo, then score it (Step 5).
                // RiskEngine only needs permissions + specialAccess + metadata — no icon.
                val base = AppInfo(
                    packageName    = pkg.packageName,
                    appName        = pm.getApplicationLabel(appInfo).toString(),
                    icon           = runCatching { pm.getApplicationIcon(pkg.packageName) }.getOrNull(),
                    versionName    = pkg.versionName ?: "—",
                    versionCode    = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        pkg.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        pkg.versionCode.toLong()
                    },
                    installTime    = pkg.firstInstallTime,
                    lastUpdateTime = pkg.lastUpdateTime,
                    installerSource = resolveInstallerSource(pkg.packageName),
                    targetSdk      = appInfo.targetSdkVersion,
                    isSystemApp    = isSystem,
                    permissions    = permissions,
                    specialAccess  = specialAccess
                )
                base.copy(riskResult = RiskEngine.score(base))
            }
    }

    /** Maps the installer package to a human-readable source label. */
    private fun resolveInstallerSource(packageName: String): InstallerSource {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val info = pm.getInstallSourceInfo(packageName)
                when (info.installingPackageName) {
                    "com.android.vending",
                    "com.google.android.feedback" -> InstallerSource.PLAY_STORE
                    null                          -> InstallerSource.SIDELOADED
                    else                          -> InstallerSource.OTHER
                }
            } else {
                @Suppress("DEPRECATION")
                when (pm.getInstallerPackageName(packageName)) {
                    "com.android.vending",
                    "com.google.android.feedback" -> InstallerSource.PLAY_STORE
                    null                          -> InstallerSource.SIDELOADED
                    else                          -> InstallerSource.OTHER
                }
            }
        } catch (e: Exception) {
            InstallerSource.OTHER
        }
    }
}
