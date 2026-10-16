# App Risk Auditor — Scoring Rubric

## 1. Risk Band Thresholds

| Score | Band     | Meaning |
|-------|----------|---------|
| 0–24  | **Low**      | Normal app; no sensitive access of concern |
| 25–49 | **Medium**   | Some permissions worth knowing about |
| 50–74 | **High**     | Significant sensitive access; review recommended |
| 75–100| **Critical** | Dangerous capability combination; act immediately |

---

## 2. Permission Category Weights

All weights are defined in `RiskEngine.Weights` (one place to tune).

### Granted dangerous permissions — full weight

| Category | Weight | Key permissions |
|----------|--------|----------------|
| Special access (active) | **20 / item** | Accessibility service, Device admin, Notification listener, Overlay, Install packages, Usage access |
| Special access (declared only) | **8 / item** | Same as above, but not currently enabled |
| SMS | **15** | READ_SMS, RECEIVE_SMS, SEND_SMS |
| Contacts & call logs | **10** | READ_CONTACTS, READ_CALL_LOG |
| Camera & microphone | **10** | CAMERA, RECORD_AUDIO |
| Location | **8** | ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION |
| Background location bonus | **+5** | ACCESS_BACKGROUND_LOCATION (in addition to base 8) |
| Phone & identity | **6** | READ_PHONE_STATE, READ_PHONE_NUMBERS, CALL_PHONE |
| Storage | **6** | READ_MEDIA_*, READ/WRITE_EXTERNAL_STORAGE |
| MANAGE_EXTERNAL_STORAGE bonus | **+4** | Unrestricted file access |
| Background & persistence | **4** | RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS |
| Network | **1** | INTERNET, ACCESS_NETWORK_STATE |

### Declared-but-not-granted multiplier
`declared weight = full_weight × 0.30` (rounded down)

---

## 3. Combination Rule Bonuses

These bonuses stack on top of the category weights.

| Rule | Condition | Bonus | Plain-language reason |
|------|-----------|-------|-----------------------|
| **SMS exfiltration pattern** | READ_SMS or RECEIVE_SMS **+** INTERNET **+** RECEIVE_BOOT_COMPLETED **+** installer = Sideloaded | **+15** | "Reads SMS + has internet + starts on boot + was sideloaded — classic spyware pattern." |
| **Accessibility + Overlay** | Active accessibility service **+** SYSTEM_ALERT_WINDOW declared | **+12** | "Can read your screen and draw fake UI on top of real apps." |
| **Untrusted device admin** | Active device admin **+** installer ≠ Play Store | **+10** | "Active device administrator from an untrusted source — can lock or wipe the device." |

---

## 4. High/Critical Guarantee Rule

Any app scoring **High or Critical (≥ 50)** is guaranteed to have at least **3 plain-language reasons**. If the scoring algorithm produces fewer, the engine appends filler reasons from:
1. "This app was sideloaded…"
2. "Targets an old Android API (SDK < 28)…"
3. "Holds an unusually large number of granted permissions (N total)."

---

## 5. Score Cap

`finalScore = rawSum.coerceIn(0, 100)`

No app can exceed 100 regardless of how many dangerous features it accumulates.

---

## 6. Special Access Detection Methods

| Type | API used | Active = |
|------|---------|---------|
| Accessibility service | `AccessibilityManager.getEnabledAccessibilityServiceList()` | Package in list |
| Device admin | `DevicePolicyManager.getActiveAdmins()` | Package in list |
| Notification listener | `Settings.Secure "enabled_notification_listeners"` | Package in string |
| Overlay | `AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW` | Op = MODE_ALLOWED |
| Install packages | `AppOpsManager.OPSTR_REQUEST_INSTALL_PACKAGES` | Op = MODE_ALLOWED |
| Usage access | `AppOpsManager.OPSTR_GET_USAGE_STATS` | Op = MODE_ALLOWED |

---

## 7. Example Scores

| Scenario | Score | Band |
|----------|-------|------|
| Simple note-taking app (internet only) | 1 | Low |
| Navigation app (location + internet + boot) | 13 | Low |
| Social media (contacts + camera + mic + location + internet) | 44 | Medium |
| Flashlight app with READ_SMS + INTERNET | 16 | Low (context mismatch flag) |
| Sideloaded app with SMS + accessibility active + overlay | 87 | **Critical** |
| Active device admin from unknown source | 30+ | Medium–High |

---

*Source of truth: [`app/src/main/java/com/example/appriskauditor/engine/RiskEngine.kt`](app/src/main/java/com/example/appriskauditor/engine/RiskEngine.kt)*
