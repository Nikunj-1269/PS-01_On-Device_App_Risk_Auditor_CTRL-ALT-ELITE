package com.example.appriskauditor.ui

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appriskauditor.model.AppInfo
import com.example.appriskauditor.model.CategorySummary
import com.example.appriskauditor.model.InstallerSource
import com.example.appriskauditor.model.PermissionCategory
import com.example.appriskauditor.model.RiskBand
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home screen: device summary header, band filter chips, sorted app list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    onAppClick: (String) -> Unit = {},
    viewModel: MainViewModel = viewModel()
) {
    val state         by viewModel.state.collectAsStateWithLifecycle()
    val includeSystem by viewModel.includeSystem.collectAsStateWithLifecycle()
    val bandFilter    by viewModel.bandFilter.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("App Risk Auditor", style = MaterialTheme.typography.titleLarge)
                        if (state is AppListState.Success) {
                            val s = state as AppListState.Success
                            Text(
                                "${s.apps.size} apps shown",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadApps() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // System apps toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Include system apps", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = includeSystem, onCheckedChange = { viewModel.setIncludeSystem(it) })
            }
            HorizontalDivider()

            when (state) {
                is AppListState.Loading -> LoadingScreen()
                is AppListState.Error   -> ErrorScreen((state as AppListState.Error).message)
                is AppListState.Success -> {
                    val s = state as AppListState.Success
                    AppListContent(
                        success       = s,
                        bandFilter    = bandFilter,
                        onFilterClick = { viewModel.setBandFilter(it) },
                        onAppClick    = onAppClick
                    )
                }
            }
        }
    }
}

// ── Loading / Error ───────────────────────────────────────────────────────────

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("Scanning apps…", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ErrorScreen(msg: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Error: $msg", color = MaterialTheme.colorScheme.error)
    }
}

// ── Main content ──────────────────────────────────────────────────────────────

@Composable
private fun AppListContent(
    success: AppListState.Success,
    bandFilter: RiskBand?,
    onFilterClick: (RiskBand?) -> Unit,
    onAppClick: (String) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        // 1. Device summary header
        item { DeviceSummaryHeader(success.bandCounts, success.categorySummary) }

        // 2. Band filter chips
        item { BandFilterRow(selected = bandFilter, onSelect = onFilterClick) }
        item { HorizontalDivider() }

        // 3. App rows
        if (success.apps.isEmpty()) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No apps match this filter.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            items(success.apps, key = { it.packageName }) { app ->
                AppRow(app = app, onClick = { onAppClick(app.packageName) })
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            }
        }
    }
}

// ── Device summary header ─────────────────────────────────────────────────────

@Composable
private fun DeviceSummaryHeader(counts: BandCounts, categorySummary: List<CategorySummary>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Device risk summary",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            // Band counts row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BandCountCard("Critical", counts.critical, RiskBand.CRITICAL, Modifier.weight(1f))
                BandCountCard("High",     counts.high,     RiskBand.HIGH,     Modifier.weight(1f))
                BandCountCard("Medium",   counts.medium,   RiskBand.MEDIUM,   Modifier.weight(1f))
                BandCountCard("Low",      counts.low,      RiskBand.LOW,      Modifier.weight(1f))
            }

            if (categorySummary.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Apps holding each access type:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categorySummary.forEach { cs ->
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    "${categoryLabel(cs.category)}: ${cs.appCount}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BandCountCard(label: String, count: Int, band: RiskBand, modifier: Modifier) {
    val (bg, fg) = bandColors(band)
    Surface(color = bg, shape = MaterialTheme.shapes.small, modifier = modifier) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = fg
            )
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = fg)
        }
    }
}

// ── Band filter row ───────────────────────────────────────────────────────────

@Composable
private fun BandFilterRow(selected: RiskBand?, onSelect: (RiskBand?) -> Unit) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == null,
            onClick  = { onSelect(null) },
            label    = { Text("All") }
        )
        RiskBand.entries.forEach { band ->
            FilterChip(
                selected = selected == band,
                onClick  = { onSelect(if (selected == band) null else band) },
                label    = { Text(bandLabel(band)) }
            )
        }
    }
}

// ── App row ───────────────────────────────────────────────────────────────────

@Composable
private fun AppRow(app: AppInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        AppIcon(drawable = app.icon, contentDescription = app.appName)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            // Name + score badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                app.riskResult?.let { ScoreBadge(score = it.score, band = it.band) }
            }

            // Top reason
            val topReason = app.riskResult?.reasons?.firstOrNull()
            if (topReason != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = topReason,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (app.riskResult!!.band >= RiskBand.HIGH)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }

            Spacer(Modifier.height(4.dp))

            // Meta row: installer badge, target SDK
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = installerLabel(app.installerSource),
                    style = MaterialTheme.typography.labelSmall,
                    color = installerColor(app.installerSource)
                )
                Text(
                    text = "SDK ${app.targetSdk}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "v${app.versionName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Category chips (compact, scroll horizontally)
            AppCategoryChips(app)
        }
    }
}

// ── Shared Composables ────────────────────────────────────────────────────────

@Composable
fun AppIcon(drawable: Drawable?, contentDescription: String, size: Int = 48) {
    val bitmap = remember(drawable) {
        runCatching { drawable?.toBitmap(size, size) }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = Modifier.size(size.dp)
        )
    } else {
        Surface(
            modifier = Modifier.size(size.dp),
            color    = MaterialTheme.colorScheme.surfaceVariant,
            shape    = MaterialTheme.shapes.small
        ) {}
    }
}

@Composable
private fun AppCategoryChips(app: AppInfo) {
    val categories = remember(app.packageName) {
        app.permissions
            .filter { it.granted && it.category != PermissionCategory.UNCATEGORIZED }
            .map { it.category }
            .distinct()
    }
    if (categories.isEmpty()) return
    Spacer(Modifier.height(4.dp))
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        categories.forEach { cat ->
            AssistChip(
                onClick  = {},
                label    = { Text(categoryLabel(cat), style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(24.dp)
            )
        }
    }
}

/** Coloured pill showing the numeric risk score. */
@Composable
fun ScoreBadge(score: Int, band: RiskBand) {
    val (bg, fg) = bandColors(band)
    Surface(color = bg, shape = MaterialTheme.shapes.small, modifier = Modifier.padding(start = 6.dp)) {
        Text(
            text     = "$score  ${bandLabel(band)}",
            style    = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color    = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/** Returns (background, foreground) colors for a band. */
@Composable
fun bandColors(band: RiskBand): Pair<Color, Color> = when (band) {
    RiskBand.LOW      -> MaterialTheme.colorScheme.primaryContainer   to MaterialTheme.colorScheme.onPrimaryContainer
    RiskBand.MEDIUM   -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    RiskBand.HIGH     -> MaterialTheme.colorScheme.errorContainer     to MaterialTheme.colorScheme.onErrorContainer
    RiskBand.CRITICAL -> MaterialTheme.colorScheme.error              to MaterialTheme.colorScheme.onError
}

fun bandLabel(band: RiskBand): String = when (band) {
    RiskBand.LOW      -> "Low"
    RiskBand.MEDIUM   -> "Medium"
    RiskBand.HIGH     -> "High"
    RiskBand.CRITICAL -> "Critical"
}

fun categoryLabel(cat: PermissionCategory): String = when (cat) {
    PermissionCategory.SPECIAL_ACCESS     -> "Special Access"
    PermissionCategory.SMS                -> "SMS"
    PermissionCategory.CONTACTS_CALL_LOGS -> "Contacts/Calls"
    PermissionCategory.CAMERA_MIC         -> "Camera/Mic"
    PermissionCategory.LOCATION           -> "Location"
    PermissionCategory.PHONE_IDENTITY     -> "Phone/Identity"
    PermissionCategory.STORAGE            -> "Storage"
    PermissionCategory.BACKGROUND         -> "Background"
    PermissionCategory.NETWORK            -> "Network"
    PermissionCategory.UNCATEGORIZED      -> "Other"
}

@Composable
private fun installerColor(source: InstallerSource) = when (source) {
    InstallerSource.PLAY_STORE -> MaterialTheme.colorScheme.primary
    InstallerSource.SIDELOADED -> MaterialTheme.colorScheme.error
    InstallerSource.OTHER      -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun installerLabel(source: InstallerSource) = when (source) {
    InstallerSource.PLAY_STORE -> "Play Store"
    InstallerSource.SIDELOADED -> "⚠ Sideloaded"
    InstallerSource.OTHER      -> "Other"
}

private fun formatDate(epochMillis: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(epochMillis))
