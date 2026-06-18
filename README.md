# FocusGuard

A personal-use focus / self-control app for **your own** Oppo F21 Pro (ColorOS 14,
Android 14). It blocks distracting & triggering apps, websites, and keywords, and
makes itself hard to remove in a moment of weakness — by design, the **only**
escape hatch is a Recovery Mode factory reset.

> This is a *self-binding* tool (the "Ulysses pact" pattern, like Cold Turkey or
> Opal). It is meant to be installed by you, on a device you own, against your own
> future impulses. Don't install it on anyone else's device without their
> knowledge and consent — doing so is abuse and, in most places, illegal.

---

## What actually works (and what can't)

| Requirement | Mechanism | Status |
|---|---|---|
| Block apps (IG, YT, FB, etc.) | AccessibilityService kicks you to home | ✅ |
| Add ANY installed app to blocklist | In-app "Block apps…" picker → persisted | ✅ |
| Block websites + keywords | AccessibilityService scans on-screen text | ✅ (all browsers) |
| Survive accessibility revoke | Watchdog foreground service (ContentObserver) → re-opens settings + persistent nag overlay | ✅ (the real fix) |
| Block uninstall from Settings | Device Owner `setUninstallBlocked` | ✅ |
| Block ADB / USB debugging | `DISALLOW_DEBUGGING_FEATURES` | ✅ |
| Block Safe Mode | `DISALLOW_SAFE_BOOT` | ✅ |
| Install / update apps + OS updates | (install-blocking REMOVED by design — was breaking updates) | ✅ allowed |
| Minecraft credit economy | study 10pm–12am → earn play credit (24h study = 10min) | ✅ |
| Block factory reset from Settings | `DISALLOW_FACTORY_RESET` | ✅ |
| Lock our permissions on | `setPermissionPolicy(AUTO_GRANT)` | ✅ |
| Pin Private DNS | `setGlobalPrivateDnsModeOpportunistic` | ⚠️ partial |
| Streak tracking | `StreakManager` (SharedPreferences) | ✅ |
| Block split-screen / floating per app | (best handled by blocking the app outright) | ⚠️ partial |
| Un-resettable even via Recovery | **Impossible without a custom ROM** | ❌ |

**The one thing that cannot be done:** no third-party app can survive a Recovery
Mode factory reset. That's enforced below the OS. You already wanted Recovery reset
to be the deliberate escape hatch — so this is exactly the intended behavior, not
a gap. If you ever truly lock yourself out, that wipe is your way back.

---

## Provisioning (the important part)

All the "hardcore" locks depend on FocusGuard being the **Device Owner**. Device
Owner can only be set on a device with **no accounts added**, right after a
factory reset. Do this once:

### 1. Build & keep the APK
```bash
export JAVA_HOME=~/android-studio/jbr
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

### 2. Factory reset the phone
Settings → System → Reset, or via Recovery. **Do not** sign into a Google account
or add any account during setup. Skip all account steps. (Device Owner refuses to
provision if any account exists.)

### 3. Enable Developer Options + USB debugging
Settings → About → tap build number 7×, then enable USB debugging. Connect to PC.

### 4. Install and set Device Owner
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell dpm set-device-owner com.focus.guard/.GuardDeviceAdminReceiver
```
You should see `Success: Device owner set ...`. If you instead get
`not allowed to set the device owner because there are already some accounts` —
you added an account in step 2; reset and retry.

### 5. Open the app
The status screen should show `Device Owner: [ ON ]`. Now grant:
- **Accessibility** → enable FocusGuard (button on the status screen).
- **Overlay** → allow (button on the status screen).

Once Accessibility is on, the app's own `GuardAccessibilityService` will block any
attempt to reach its Settings page to turn it back off.

### After this point
- Uninstall is blocked. USB debugging is blocked (so the `dpm remove-active-admin`
  trick won't work — debugging is off).
- To intentionally remove it: **Recovery Mode → Wipe data / factory reset.**

---

## Customizing blocklists

Two ways now:

1. **In-app (apps only):** open FocusGuard → **"Block apps…"** → tick any installed
   app. Choices persist to `Blocklist` (SharedPreferences), survive reboot, and take
   effect immediately.
2. **In code (sites/keywords):** edit `GuardAccessibilityService.kt`:
   - `blockedPackages` — built-in app package names.
   - `blockedDomains` — website domains (single hit = hard block).
   - `EXPLICIT_KEYWORDS` — explicit adult words (single hit = block).
   - `AMBIGUOUS_KEYWORDS` — study-adjacent words (sex/nude/…); need **two** distinct
     hits before bouncing, so JEE biology text ("sexual reproduction") isn't falsely
     blocked.

Newly installed apps are auto-captured by `PackageInstallReceiver` into the
persistent `Blocklist` (though with `DISALLOW_INSTALL_APPS` active, new installs are
blocked outright anyway).

## The accessibility-revoke defense (the important one)

The revoke screen is the Android **Safety Center**
(`com.google.android.permissioncontroller/...SafetyCenterActivity`). On-device
tracing proved a hard truth: **an accessibility service is blinded to its own
revoke screen** — a 3.3-second event blackout while "Remove access" is tapped. No
in-service overlay can ever trigger there, and a Device Owner **cannot** force the
setting back on (`ENABLED_ACCESSIBILITY_SERVICES` is off the `setSecureSetting`
allowlist; `WRITE_SECURE_SETTINGS` is platform-signature only). True prevention is
impossible without a custom ROM — the same wall BlockerX/AppBlock hit.

So FocusGuard uses **detect-and-react** from a place that ISN'T blinded — the
`GuardForegroundService` watchdog:
- a `ContentObserver` on `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` (plus a
  2 s polling backstop) detects the instant our service is removed;
- it immediately re-opens the accessibility settings page **and** raises a
  persistent full-screen `TYPE_APPLICATION_OVERLAY` nag that stays until the
  service is re-enabled.

Since uninstall and Settings-based factory reset are both locked (Device Owner),
re-enabling becomes the path of least resistance. `setPermittedAccessibilityServices`
also stops a *rogue* a11y service being added to fight FocusGuard.

`adb logcat -s FocusGuardWatch` traces the watchdog; set `VERBOSE=true` in
`GuardAccessibilityService` to log every event under `FocusGuardA11y`.

## UI

Both screens use a dependency-free dark design system (`Ui.kt`): rounded surfaces,
status pills, gradient CTAs, a streak hero card, and the app picker with real app
icons + toggle switches. Theme is `Theme.FocusGuard` (Material3, no action bar).

---

## Known limitations / honest notes

- **Accessibility text-scanning is heuristic.** It catches blocked words when they
  render on screen, but a determined user can move fast. It's a speed bump plus the
  Device Owner locks, not an unbreakable wall.
- **Private DNS pinning** uses opportunistic mode; ColorOS may still expose the
  toggle. Combine with `DISALLOW_CONFIG_VPN` (already applied) for stronger
  coverage. A future `VpnService`-based domain filter (see TODO) would be airtight.
- **Battery:** the guard is a single lightweight foreground service plus the
  accessibility callback. Keep FocusGuard whitelisted from ColorOS battery
  optimization so it isn't killed.
- **Per-app split-screen/floating** isn't reliably blockable on ColorOS 14; the
  practical answer is to block the distracting app entirely.

## TODO / future hardening
- [ ] `VpnService` local DNS filter for browser-agnostic domain blocking.
- [ ] Room database for blocklists editable from the UI (currently code-defined).
- [ ] Time-window schedules (e.g. allow YouTube 6–7pm).
- [ ] "Supervised unlock" flow using `releaseProtections()` behind a long delay.
