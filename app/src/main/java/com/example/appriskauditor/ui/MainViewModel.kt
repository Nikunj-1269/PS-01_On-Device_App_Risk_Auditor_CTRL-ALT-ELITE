package com.example.appriskauditor.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.appriskauditor.data.AppRepository
import com.example.appriskauditor.model.AppInfo
import com.example.appriskauditor.model.CategorySummary
import com.example.appriskauditor.model.PermissionCategory
import com.example.appriskauditor.model.RiskBand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Band counts for the device summary header. */
data class BandCounts(
    val critical: Int = 0,
    val high: Int     = 0,
    val medium: Int   = 0,
    val low: Int      = 0
)

/** UI state for the app list screen */
sealed interface AppListState {
    object Loading : AppListState
    data class Success(
        val apps: List<AppInfo>,
        val categorySummary: List<CategorySummary>,
        val bandCounts: BandCounts
    ) : AppListState
    data class Error(val message: String) : AppListState
}

/**
 * ViewModel shared by list + detail screens.
 * Exposes StateFlows so Compose can collect them efficiently.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)

    private val _state = MutableStateFlow<AppListState>(AppListState.Loading)
    val state: StateFlow<AppListState> = _state.asStateFlow()

    private val _includeSystem = MutableStateFlow(false)
    val includeSystem: StateFlow<Boolean> = _includeSystem.asStateFlow()

    /** Currently selected band filter; null = show all */
    private val _bandFilter = MutableStateFlow<RiskBand?>(null)
    val bandFilter: StateFlow<RiskBand?> = _bandFilter.asStateFlow()

    /** All loaded apps (unfiltered); used by detail screen lookups */
    private var allApps: List<AppInfo> = emptyList()

    init {
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = AppListState.Loading
            try {
                val apps = repository.getInstalledApps(_includeSystem.value)
                allApps = apps
                _state.value = buildSuccess(apps)
            } catch (e: Exception) {
                _state.value = AppListState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun setIncludeSystem(include: Boolean) {
        _includeSystem.value = include
        loadApps()
    }

    /** Set or clear the band filter (pass null to show all). */
    fun setBandFilter(band: RiskBand?) {
        _bandFilter.value = band
        val current = allApps
        if (current.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = buildSuccess(current)
        }
    }

    /** Look up a single app by package name (for the detail screen). */
    fun findApp(packageName: String): AppInfo? = allApps.find { it.packageName == packageName }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun buildSuccess(apps: List<AppInfo>): AppListState.Success {
        val filter = _bandFilter.value
        val displayed = apps
            .filter { filter == null || it.riskResult?.band == filter }
            .sortedByDescending { it.riskResult?.score ?: 0 }

        return AppListState.Success(
            apps            = displayed,
            categorySummary = buildCategorySummary(apps),   // always device-wide
            bandCounts      = buildBandCounts(apps)
        )
    }

    private fun buildBandCounts(apps: List<AppInfo>) = BandCounts(
        critical = apps.count { it.riskResult?.band == RiskBand.CRITICAL },
        high     = apps.count { it.riskResult?.band == RiskBand.HIGH },
        medium   = apps.count { it.riskResult?.band == RiskBand.MEDIUM },
        low      = apps.count { it.riskResult?.band == RiskBand.LOW }
    )

    private fun buildCategorySummary(apps: List<AppInfo>): List<CategorySummary> {
        val counts = mutableMapOf<PermissionCategory, Int>()
        for (app in apps) {
            val cats = app.permissions
                .filter { it.granted && it.category != PermissionCategory.UNCATEGORIZED }
                .map { it.category }
                .toSet()
            for (cat in cats) counts[cat] = (counts[cat] ?: 0) + 1
        }
        return counts.entries
            .sortedBy { it.key.ordinal }
            .map { CategorySummary(it.key, it.value) }
    }
}
