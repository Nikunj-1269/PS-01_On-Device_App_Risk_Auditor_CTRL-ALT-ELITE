# App Risk Auditor — 3-Minute Demo Script

**Target audience:** Hackathon judges (non-technical to semi-technical)
**Device:** Any Android phone or emulator running API 26+ with a mix of apps installed.
**APK:** `app/build/outputs/apk/debug/app-debug.apk`

---

## Before you start (30 sec setup)

1. Install the APK: `adb install app/build/outputs/apk/debug/app-debug.apk`
2. Open **App Risk Auditor** from the launcher.
3. The app will scan all installed apps automatically (5–15 seconds).

---

## Part 1 — Device Summary (30 sec)

**What to show:** The header on the home screen.

**Script:**
> "The moment the app opens, you get a device-wide risk summary.
> Here I have [N] Critical apps and [N] High apps.
> These four cards tell you instantly: how many apps on this phone pose serious risk.
> Below that, the access-type chips show — for example — that 12 apps can read your location
> and 3 apps have internet access combined with SMS.
> A non-technical user doesn't need to know what a permission is — they just see the numbers."

**Tap:** The "Critical" filter chip to show only the most dangerous apps.

---

## Part 2 — Top Critical App + "Why Flagged" panel (60 sec)

**What to show:** Tap the top-ranked app (highest score).

**Script:**
> "Let's look at the riskiest app on this device.
> The score is [87] — Critical band.
> Notice it was [Sideloaded / from an unknown source].
> Now I tap it."

*[Tap the app row]*

> "The detail screen shows:
> First — the score at a glance: 87 out of 100, Critical.
> Then — 'Why is this app flagged?' in plain English, no jargon:
> [Read the top 2–3 reasons aloud, e.g.:]
> • 'Active accessibility service — can read everything on your screen, including passwords.'
> • 'Can draw over other apps — could display a fake login screen on top of your banking app.'
> • 'Reads SMS + has internet + starts on boot + was sideloaded — classic spyware pattern.'
>
> A grandmother can understand this.
> We didn't just show raw permission names — we translated them into real-world consequences."

---

## Part 3 — Settings / Uninstall buttons (30 sec)

**What to show:** Scroll to the bottom of the detail screen.

**Script:**
> "The app tells you not just *what* is wrong but *what to do right now*.
> 'Open App Settings' takes you directly to the system permission manager
> where you can revoke individual permissions — no hunting through menus.
> 'Uninstall App' — one tap to remove it."

*[Tap "Open App Settings" — the system settings page opens]*

> "Everything happens on-device. No data leaves the phone. No server, no account, no internet."

*[Press back]*

---

## Part 4 — Combination rule / Context mismatch (30 sec, if time)

**What to show:** The "Why flagged" panel for an app that triggered a combination rule.

**Script (combination rule):**
> "The engine doesn't just score permissions individually — it looks for *patterns*.
> This sideloaded app holds SMS access, internet access, AND starts on boot.
> That exact combination is a textbook spyware signature.
> We add a bonus to the score and surface a dedicated warning:
> '[read the combination reason]'."

*OR*

**Script (filter demo):**
> "I can filter by band — let me tap 'High' to see only the High-risk apps.
> [N] apps on this device are in the High band.
> Each one has a top reason right on the list — no need to tap in."

---

## Wrap-up (15 sec)

> "App Risk Auditor answers three questions every phone user should be able to answer:
> **Which apps can do the most harm? Why? What do I do about it?**
> It works completely offline, requires no root, and runs in seconds.
> The scoring weights and combination rules are in a single config object —
> easy to tune, easy to audit, easy to extend."

---

## Fallback: if no Critical apps exist on the judge's device

Use the **"Include system apps"** toggle to expand the scan — system apps often
hold high-privilege permissions (accessibility, device admin, overlay) and will
push several into the High or Critical band.

Alternatively, demonstrate the filter chips on Medium apps — the "Why flagged"
panel is just as illustrative for a Medium-risk app.
