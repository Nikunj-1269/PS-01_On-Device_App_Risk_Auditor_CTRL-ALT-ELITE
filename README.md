# App Risk Auditor

**On-Device App Risk Auditor** — Hackathon PS-01, Mobile Security track.

Scans all installed apps, extracts permissions and special access, and assigns each app a 0–100 risk score with plain-language reasons so any non-technical user can understand their device's exposure.

---

## Build & Run

### Prerequisites
- Android Studio Hedgehog or newer (or command-line Gradle 8.7+)
- Android SDK 35 installed
- Physical device or emulator running API 26+

### From Android Studio (recommended)
1. `File → Open` → select this folder
2. Wait for Gradle sync to finish
3. `Run → Run 'app'` (Shift+F10)

### From command line
```bash
# Debug APK
./gradlew assembleDebug

# Install directly on connected device/emulator
./gradlew installDebug

# Run unit tests
./gradlew test
```

APK location: `app/build/outputs/apk/debug/app-debug.apk`

---

## Project Structure
```
app/src/main/java/com/example/appriskauditor/
├── MainActivity.kt                  # Entry point + minimal nav host
├── model/
│   ├── AppInfo.kt                   # Core data model
│   ├── Permission.kt                # Permission model + enums
│   ├── SpecialAccess.kt             # Special access model
│   ├── RiskResult.kt                # RiskBand + RiskResult
│   └── CategorySummary.kt          # Device-wide category counts
├── data/
│   ├── AppRepository.kt             # PackageManager queries — orchestrates all steps
│   ├── PermissionExtractor.kt       # requestedPermissions + flags → Permission list
│   ├── PermissionCategorizer.kt     # Lookup table: permission → category
│   └── SpecialAccessDetector.kt     # Accessibility / DeviceAdmin / Overlay / etc.
├── engine/
│   └── RiskEngine.kt               # Pure-Kotlin risk scorer (no Android imports)
└── ui/
    ├── MainViewModel.kt             # MVVM: state flows, band filter, category summary
    ├── AppListScreen.kt             # Home: device summary, filter chips, sorted list
    ├── AppDetailScreen.kt           # Detail: score panel, "Why flagged", permissions, actions
    └── theme/
        └── AppRiskAuditorTheme.kt
```

---

## Features
- ✅ Scans all user (+ optionally system) installed apps
- ✅ Extracts all declared + granted permissions with protection levels
- ✅ 9 permission categories with device-wide summary
- ✅ Detects 6 special access types (accessibility, device admin, notification listener, overlay, install packages, usage access)
- ✅ 0–100 risk score with LOW/MEDIUM/HIGH/CRITICAL bands
- ✅ Plain-language reasons for every flagged app
- ✅ Combination rules (SMS + internet + boot + sideloaded, accessibility + overlay, device admin from untrusted source)
- ✅ Filter by risk band
- ✅ App detail screen with "Open Settings" and "Uninstall" buttons
- ✅ 100% on-device — no internet permission, no data leaves the device

---

## Privacy
- 100% on-device. No `INTERNET` permission. No data leaves the device.
- No root or ADB required at runtime.
- `QUERY_ALL_PACKAGES` is declared (permitted for security/audit tools).

---

## Steps completed
- [x] Step 1 — Project setup + app inventory
- [x] Step 2 — Permission extraction
- [x] Step 3 — Categorization
- [x] Step 4 — Special access detection
- [x] Step 5 — Risk scoring engine + unit tests
- [x] Step 6 — Full UI (home screen, detail screen, navigation)
