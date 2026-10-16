package com.example.appriskauditor.engine

import com.example.appriskauditor.model.AppInfo
import com.example.appriskauditor.model.InstallerSource
import com.example.appriskauditor.model.Permission
import com.example.appriskauditor.model.PermissionCategory
import com.example.appriskauditor.model.ProtectionLevel
import com.example.appriskauditor.model.RiskBand
import com.example.appriskauditor.model.SpecialAccess
import com.example.appriskauditor.model.SpecialAccessType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [RiskEngine].
 *
 * All tests run on the JVM (no Android framework needed).
 * Run with:  ./gradlew test
 */
class RiskEngineTest {

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun makeApp(
        packageName: String = "com.test.app",
        installerSource: InstallerSource = InstallerSource.PLAY_STORE,
        targetSdk: Int = 34,
        permissions: List<Permission> = emptyList(),
        specialAccess: List<SpecialAccess> = emptyList()
    ) = AppInfo(
        packageName    = packageName,
        appName        = "Test App",
        icon           = null,
        versionName    = "1.0",
        versionCode    = 1L,
        installTime    = 0L,
        lastUpdateTime = 0L,
        installerSource = installerSource,
        targetSdk      = targetSdk,
        isSystemApp    = false,
        permissions    = permissions,
        specialAccess  = specialAccess
    )

    private fun granted(name: String, cat: PermissionCategory) = Permission(
        name            = "android.permission.$name",
        granted         = true,
        protectionLevel = ProtectionLevel.DANGEROUS,
        category        = cat
    )

    private fun declared(name: String, cat: PermissionCategory) = Permission(
        name            = "android.permission.$name",
        granted         = false,
        protectionLevel = ProtectionLevel.DANGEROUS,
        category        = cat
    )

    // ── Scenario 1: Clean app ─────────────────────────────────────────────────

    @Test
    fun `clean app with only internet scores LOW`() {
        val app = makeApp(
            permissions = listOf(
                granted("INTERNET", PermissionCategory.NETWORK),
                granted("ACCESS_NETWORK_STATE", PermissionCategory.NETWORK)
            )
        )
        val result = RiskEngine.score(app)
        assertEquals(RiskBand.LOW, result.band)
        assertTrue("Score should be < 25 but was ${result.score}", result.score < 25)
    }

    // ── Scenario 2: Sensitive app ─────────────────────────────────────────────

    @Test
    fun `app with camera, mic, location and contacts scores HIGH or CRITICAL`() {
        val app = makeApp(
            permissions = listOf(
                granted("CAMERA",                PermissionCategory.CAMERA_MIC),
                granted("RECORD_AUDIO",          PermissionCategory.CAMERA_MIC),
                granted("ACCESS_FINE_LOCATION",  PermissionCategory.LOCATION),
                granted("READ_CONTACTS",         PermissionCategory.CONTACTS_CALL_LOGS),
                granted("INTERNET",              PermissionCategory.NETWORK)
            )
        )
        val result = RiskEngine.score(app)
        assertTrue("Expected HIGH or CRITICAL, got ${result.band}", result.band >= RiskBand.HIGH)
        assertTrue("High/Critical must have ≥3 reasons", result.reasons.size >= 3)
    }

    // ── Scenario 3: Trojan-like app ───────────────────────────────────────────

    @Test
    fun `sideloaded app with SMS + internet + boot + active accessibility scores CRITICAL`() {
        val app = makeApp(
            installerSource = InstallerSource.SIDELOADED,
            permissions = listOf(
                granted("READ_SMS",              PermissionCategory.SMS),
                granted("RECEIVE_SMS",           PermissionCategory.SMS),
                granted("SEND_SMS",              PermissionCategory.SMS),
                granted("INTERNET",              PermissionCategory.NETWORK),
                granted("RECEIVE_BOOT_COMPLETED", PermissionCategory.BACKGROUND),
                granted("READ_CONTACTS",         PermissionCategory.CONTACTS_CALL_LOGS)
            ),
            specialAccess = listOf(
                SpecialAccess(
                    type   = SpecialAccessType.ACCESSIBILITY_SERVICE,
                    active = true,
                    label  = "Active accessibility service"
                )
            )
        )
        val result = RiskEngine.score(app)
        assertEquals(RiskBand.CRITICAL, result.band)
        assertTrue("Score should be 75-100, was ${result.score}", result.score in 75..100)
        assertTrue("Should have ≥3 reasons", result.reasons.size >= 3)
        // The spyware combo reason must be present
        assertTrue(
            "Expected SMS+internet+boot combination reason",
            result.reasons.any { it.contains("spyware", ignoreCase = true) ||
                                  it.contains("SMS", ignoreCase = true) }
        )
    }

    // ── Scenario 4: Declared-only is scored lower than granted ───────────────

    @Test
    fun `declared-only permissions score less than granted`() {
        val grantedApp = makeApp(permissions = listOf(
            granted("READ_SMS", PermissionCategory.SMS)
        ))
        val declaredApp = makeApp(permissions = listOf(
            declared("READ_SMS", PermissionCategory.SMS)
        ))
        val grantedScore  = RiskEngine.score(grantedApp).score
        val declaredScore = RiskEngine.score(declaredApp).score
        assertTrue("Granted ($grantedScore) should exceed declared ($declaredScore)",
            grantedScore > declaredScore)
    }

    // ── Scenario 5: Score is capped at 100 ───────────────────────────────────

    @Test
    fun `score never exceeds 100`() {
        // Load up every dangerous category + all special access active
        val permissions = listOf(
            granted("READ_SMS",               PermissionCategory.SMS),
            granted("INTERNET",               PermissionCategory.NETWORK),
            granted("RECEIVE_BOOT_COMPLETED", PermissionCategory.BACKGROUND),
            granted("CAMERA",                 PermissionCategory.CAMERA_MIC),
            granted("ACCESS_FINE_LOCATION",   PermissionCategory.LOCATION),
            granted("ACCESS_BACKGROUND_LOCATION", PermissionCategory.LOCATION),
            granted("READ_CONTACTS",          PermissionCategory.CONTACTS_CALL_LOGS),
            granted("MANAGE_EXTERNAL_STORAGE", PermissionCategory.STORAGE),
            granted("READ_PHONE_STATE",       PermissionCategory.PHONE_IDENTITY)
        )
        val specialAccess = SpecialAccessType.entries.map { type ->
            SpecialAccess(type = type, active = true, label = "Active $type")
        }
        val app = makeApp(
            installerSource = InstallerSource.SIDELOADED,
            permissions     = permissions,
            specialAccess   = specialAccess
        )
        val result = RiskEngine.score(app)
        assertTrue("Score ${result.score} must be ≤ 100", result.score <= 100)
    }

    // ── Scenario 6: Active special access always raises score above declared ──

    @Test
    fun `active special access scores higher than declared-only`() {
        val activeApp = makeApp(
            specialAccess = listOf(
                SpecialAccess(SpecialAccessType.ACCESSIBILITY_SERVICE, active = true, label = "")
            )
        )
        val declaredApp = makeApp(
            permissions = listOf(
                declared("BIND_ACCESSIBILITY_SERVICE", PermissionCategory.SPECIAL_ACCESS)
            ),
            specialAccess = listOf(
                SpecialAccess(SpecialAccessType.ACCESSIBILITY_SERVICE, active = false, label = "")
            )
        )
        assertTrue(
            "Active special access should score higher than declared-only",
            RiskEngine.score(activeApp).score > RiskEngine.score(declaredApp).score
        )
    }
}
