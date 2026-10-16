package com.example.appriskauditor.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appriskauditor.data.PermissionCategorizer
import com.example.appriskauditor.model.AppInfo
import com.example.appriskauditor.model.Permission
import com.example.appriskauditor.model.PermissionCategory
import com.example.appriskauditor.model.ProtectionLevel
import com.example.appriskauditor.model.RiskBand
import com.example.appriskauditor.model.SpecialAccess
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Detail screen for one app.
 * Shows: header info, risk score + band, "Why flagged" reasons, permissions by
 * category (granted vs declared, protection level), special access status,
 * and action buttons (system settings / uninstall).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    packageName: String,
    onBack: () -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    // Handle system back gesture / button
    BackHandler(onBack = onBack)

    val app = remember(packageName) { viewModel.findApp(packageName) }

    if (app == null) {
        // App not found — shouldn't happen in normal flow
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("App not found.", color = MaterialTheme.colorScheme.error)
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(app.appName, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        DetailContent(
            app = app,
            modifier = Modifier.padding(padding)
        )
    }
}

// ── Detail content ────────────────────────────────────────────────────────────

@Composable
private fun DetailContent(app: AppInfo, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Compute grouped permissions outside LazyColumn (needs Composable context for remember)
    val grouped = remember(app.packageName) {
        PermissionCategorizer.groupByCategory(app.permissions)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // 1. App header (icon + name + meta)
        item { AppHeaderSection(app) }

        // 2. Risk score panel
        app.riskResult?.let { result ->
            item { RiskScoreSection(score = result.score, band = result.band) }

            // 3. "Why flagged" panel
            if (result.reasons.isNotEmpty()) {
                item { SectionHeader("Why is this app flagged?") }
                items(result.reasons) { reason ->
                    ReasonRow(reason = reason, band = result.band)
                }
            }
        }

        // 4. Special access
        if (app.specialAccess.isNotEmpty()) {
            item { SectionHeader("Special access") }
            items(app.specialAccess) { sa ->
                SpecialAccessRow(sa)
            }
        }

        // 5. Permissions grouped by category
        if (grouped.isNotEmpty()) {
            item { SectionHeader("Permissions by category") }
            grouped.forEach { (category, perms) ->
                item { CategoryHeader(category) }
                items(perms) { perm -> PermissionRow(perm) }
            }
        }

        // 6. Action buttons
        item {
            Spacer(Modifier.height(16.dp))
            ActionButtons(
                packageName = app.packageName,
                isSystemApp = app.isSystemApp,
                onSettings = {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", app.packageName, null)
                    )
                    context.startActivity(intent)
                },
                onUninstall = {
                    val intent = Intent(Intent.ACTION_DELETE,
                        Uri.fromParts("package", app.packageName, null))
                    context.startActivity(intent)
                }
            )
        }
    }
}

// ── Section: App header ───────────────────────────────────────────────────────

@Composable
private fun AppHeaderSection(app: AppInfo) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color    = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(drawable = app.icon, contentDescription = app.appName, size = 64)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(app.appName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                InfoRow("Version",   app.versionName)
                InfoRow("Installed", formatDate(app.installTime))
                InfoRow("Updated",   formatDate(app.lastUpdateTime))
                InfoRow("Source",    when (app.installerSource) {
                    com.example.appriskauditor.model.InstallerSource.PLAY_STORE -> "Google Play Store"
                    com.example.appriskauditor.model.InstallerSource.SIDELOADED -> "⚠ Sideloaded"
                    com.example.appriskauditor.model.InstallerSource.OTHER      -> "Other / Unknown"
                })
                InfoRow("Target SDK", "API ${app.targetSdk}")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 1.dp)) {
        Text(
            "$label: ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
    }
}

// ── Section: Risk score ───────────────────────────────────────────────────────

@Composable
private fun RiskScoreSection(score: Int, band: RiskBand) {
    val (bg, fg) = bandColors(band)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        color = bg,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "Risk Score",
                    style = MaterialTheme.typography.labelMedium,
                    color = fg
                )
                Text(
                    score.toString(),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = fg
                )
                Text("out of 100", style = MaterialTheme.typography.labelSmall, color = fg)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    bandLabel(band),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = fg
                )
                Text(
                    bandDescription(band),
                    style = MaterialTheme.typography.bodySmall,
                    color = fg
                )
            }
        }
    }
}

private fun bandDescription(band: RiskBand): String = when (band) {
    RiskBand.LOW      -> "This app poses little risk."
    RiskBand.MEDIUM   -> "Some permissions to be aware of."
    RiskBand.HIGH     -> "Has significant access to sensitive data."
    RiskBand.CRITICAL -> "Dangerous access combination — review now."
}

// ── Section: Why flagged ──────────────────────────────────────────────────────

@Composable
private fun ReasonRow(reason: String, band: RiskBand) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = if (band >= RiskBand.HIGH) MaterialTheme.colorScheme.error
                   else MaterialTheme.colorScheme.secondary,
            modifier = Modifier
                .size(18.dp)
                .padding(top = 2.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(reason, style = MaterialTheme.typography.bodyMedium)
    }
}

// ── Section: Special access ───────────────────────────────────────────────────

@Composable
private fun SpecialAccessRow(sa: SpecialAccess) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        color = if (sa.active) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(10.dp),
                shape = MaterialTheme.shapes.extraSmall,
                color = if (sa.active) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.tertiary
            ) {}
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = if (sa.active) "ACTIVE" else "Declared only",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (sa.active) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(sa.label, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ── Section: Permissions by category ─────────────────────────────────────────

@Composable
private fun CategoryHeader(category: PermissionCategory) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalDivider(Modifier.weight(1f))
        Text(
            categoryLabel(category),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        HorizontalDivider(Modifier.weight(1f))
    }
}

@Composable
private fun PermissionRow(perm: Permission) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Short name in bold, full name small below
            val short = perm.name.substringAfterLast('.')
            Text(short, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Text(
                perm.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            // Granted / Declared badge
            Surface(
                color = if (perm.granted) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Text(
                    text = if (perm.granted) "Granted" else "Declared",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    color = if (perm.granted) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Protection level label
            Text(
                text = protectionLabel(perm.protectionLevel),
                style = MaterialTheme.typography.labelSmall,
                color = protectionColor(perm.protectionLevel)
            )
        }
    }
}

@Composable
private fun protectionColor(level: ProtectionLevel) = when (level) {
    ProtectionLevel.DANGEROUS  -> MaterialTheme.colorScheme.error
    ProtectionLevel.SIGNATURE  -> MaterialTheme.colorScheme.tertiary
    ProtectionLevel.NORMAL     -> MaterialTheme.colorScheme.onSurfaceVariant
    ProtectionLevel.SPECIAL    -> MaterialTheme.colorScheme.secondary
}

private fun protectionLabel(level: ProtectionLevel) = when (level) {
    ProtectionLevel.NORMAL    -> "normal"
    ProtectionLevel.DANGEROUS -> "dangerous"
    ProtectionLevel.SIGNATURE -> "signature"
    ProtectionLevel.SPECIAL   -> "special"
}

// ── Section: Action buttons ───────────────────────────────────────────────────

@Composable
private fun ActionButtons(
    packageName: String,
    isSystemApp: Boolean,
    onSettings: () -> Unit,
    onUninstall: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionHeader("What can I do?")

        // Open system settings (always available)
        Button(
            onClick  = onSettings,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Settings, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Open App Settings")
        }

        // Uninstall (only for user-installed apps)
        if (!isSystemApp) {
            OutlinedButton(
                onClick  = onUninstall,
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Uninstall App")
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text     = title,
        style    = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

private fun formatDate(epochMillis: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(epochMillis))
