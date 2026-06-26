package com.focus.guard

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.focus.guard.Ui.Radius
import com.focus.guard.Ui.Space
import com.focus.guard.Ui.card
import com.focus.guard.Ui.chip
import com.focus.guard.Ui.dangerButton
import com.focus.guard.Ui.divider
import com.focus.guard.Ui.dp
import com.focus.guard.Ui.dpf
import com.focus.guard.Ui.frame
import com.focus.guard.Ui.gradient
import com.focus.guard.Ui.heroGradient
import com.focus.guard.Ui.icon
import com.focus.guard.Ui.iconChip
import com.focus.guard.Ui.lp
import com.focus.guard.Ui.pill
import com.focus.guard.Ui.primaryButton
import com.focus.guard.Ui.rounded
import com.focus.guard.Ui.row
import com.focus.guard.Ui.stat
import com.focus.guard.Ui.switch
import com.focus.guard.Ui.text
import com.focus.guard.Ui.tile
import com.focus.guard.Ui.tintWash
import com.focus.guard.Ui.title

/**
 * FocusGuard home / dashboard.
 *
 * Information architecture (redesigned to reduce cognitive load):
 *   1. Brand header
 *   2. Hero — the streak (the emotional core / habit anchor)
 *   3. Exam countdown (only when relevant context exists)
 *   4. Protection status — one scannable "is everything armed?" summary
 *   5. Setup — permission grants that RECEDE once satisfied (collapses to a
 *      single "all set" line when nothing is outstanding)
 *   6. Controls — blocking + habit-building actions, the everyday surface
 *   7. Maintenance + Danger zone — visually separated, lower in the page
 *
 * All business logic, SharedPreferences keys, service lifecycle and security
 * calls are preserved exactly from the original implementation.
 */
class MainActivity : ComponentActivity() {

    private lateinit var streak: StreakManager

    // Live views refreshed in onResume / refresh().
    private lateinit var streakValue: TextView
    private lateinit var streakSub: TextView
    private lateinit var bestValue: TextView
    private lateinit var statusContainer: LinearLayout
    private lateinit var setupSection: LinearLayout
    private lateinit var lockBanner: LinearLayout
    private lateinit var maintenanceSection: LinearLayout
    private lateinit var lockdownButton: View
    private lateinit var lockdownInfo: TextView

    private val pickApk: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let { SelfUpdater.installApk(this, it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        streak = StreakManager(this)
        streak.touch()
        GuardDeviceAdminReceiver.applyAllProtections(this)
        ensureServicesRunning()
        AlarmScheduler.rescheduleAll(this)   // re-arm stored alarms on every launch

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(Space.xl), dp(Space.sm), dp(Space.xl), dp(Space.xxxl))
        }

        content.addView(heroCard())
        content.addView(examCountdownCard(), lp(topMargin = dp(Space.md)))

        content.addView(statusCard(), lp(topMargin = dp(Space.xl)))
        content.addView(lockdownBanner().also { lockBanner = it }, lp(topMargin = dp(Space.md)))

        // Setup section (collapses when all granted)
        setupSection = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(setupSection, lp(topMargin = dp(Space.xxl)))

        content.addView(sectionLabel("BLOCKING"), lp(topMargin = dp(Space.xxl)))
        content.addView(tile(R.drawable.ic_grid, "Block apps",
            "Permanently lock distracting apps", Ui.ACCENT) {
            startActivity(Intent(this, AppPickerActivity::class.java))
        }, lp(topMargin = dp(Space.md)))
        content.addView(tile(R.drawable.ic_globe, "Block sites & words",
            "Domains and on-screen keywords", Ui.ACCENT) {
            startActivity(Intent(this, WebsiteBlockActivity::class.java))
        }, lp(topMargin = dp(Space.md)))
        content.addView(dnsFilterTile(), lp(topMargin = dp(Space.md)))

        content.addView(sectionLabel("BUILD THE HABIT"), lp(topMargin = dp(Space.xxl)))
        content.addView(tile(R.drawable.ic_book, "Study & rewards",
            "Log study, earn Minecraft credit", Ui.GREEN) {
            startActivity(Intent(this, StudyLogActivity::class.java))
        }, lp(topMargin = dp(Space.md)))
        content.addView(tile(R.drawable.ic_lock, "Private notes",
            "PIN-protected journal", Ui.GREEN) {
            startActivity(Intent(this, NotesActivity::class.java))
        }, lp(topMargin = dp(Space.md)))
        content.addView(tile(R.drawable.ic_alarm, "Alarms",
            "Walk 100 steps + maths to dismiss", Ui.AMBER) {
            startActivity(Intent(this, AlarmListActivity::class.java))
        }, lp(topMargin = dp(Space.md)))

        // Maintenance (hidden in full lockdown)
        maintenanceSection = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        maintenanceSection.addView(sectionLabel("MAINTENANCE"), lp(topMargin = dp(Space.xxl)))
        maintenanceSection.addView(tile(R.drawable.ic_download, "Update from APK file",
            "Install a newer build", Ui.TEXT_DIM) {
            pickApk.launch(arrayOf("application/vnd.android.package-archive", "*/*"))
        }, lp(topMargin = dp(Space.md)))
        maintenanceSection.addView(tile(R.drawable.ic_refresh, "Re-enable ADB",
            "Maintenance mode — unlock debugging", Ui.TEXT_DIM) {
            getSharedPreferences("focusguard_policy", MODE_PRIVATE)
                .edit()
                .putBoolean("maintenance_mode", true)
                .putBoolean("lockdown_confirmed", false)
                .apply()
            GuardDeviceAdminReceiver.unlockDebugging(this)
            refresh()
            toast("ADB re-enabled — debugging unlocked")
        }, lp(topMargin = dp(Space.md)))
        maintenanceSection.addView(tile(R.drawable.ic_heartcrack, "I slipped — reset streak",
            "Be honest. Start again from zero.", Ui.RED) {
            confirmRelapse()
        }, lp(topMargin = dp(Space.md)))
        content.addView(maintenanceSection)

        // Danger zone
        content.addView(dangerZone(), lp(topMargin = dp(Space.xxl)))

        setContentView(frame(brandHeader(), content))
        refresh()
        nagBatteryIfMissing()
    }

    /** One-time nudge: if FocusGuard isn't on the battery whitelist yet, surface a
     *  dialog on first launch (and again if the user previously dismissed it) so the
     *  Oppo survival setup can't be silently forgotten. */
    private fun nagBatteryIfMissing() {
        if (isIgnoringBatteryOptimizations()) return
        val prefs = getSharedPreferences("focusguard_policy", MODE_PRIVATE)
        if (prefs.getBoolean("battery_nag_dismissed", false)) return
        AlertDialog.Builder(this)
            .setTitle("Keep FocusGuard alive on your Oppo?")
            .setMessage(
                "Oppo / ColorOS freezes background apps by default — without a one-time " +
                "battery setup, FocusGuard's blocking will stop working overnight.\n\n" +
                "This takes about 30 seconds and fixes most \"it stopped working\" reports."
            )
            .setPositiveButton("Set up now") { _, _ ->
                startActivity(Intent(this, PowerOnboardingActivity::class.java))
            }
            .setNegativeButton("Later") { _, _ ->
                prefs.edit().putBoolean("battery_nag_dismissed", true).apply()
            }
            .setCancelable(false)
            .show()
    }

    override fun onResume() {
        super.onResume()
        GuardDeviceAdminReceiver.applyAllProtections(this)
        ensureDnsFilterRunning()
        refresh()
    }

    // ──────────────────────── header ────────────────────────

    private fun brandHeader(): LinearLayout {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(Space.xl), dp(Space.lg), dp(Space.xl), dp(Space.md))
        }
        header.addView(iconChip(R.drawable.ic_shield, Ui.ACCENT, tintWash(Ui.ACCENT), 46, Radius.md))
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = row(0, 1f)
            setPadding(dp(Space.md), 0, 0, 0)
        }
        col.addView(text("FocusGuard", 22f, Ui.TEXT, bold = true).apply {
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            letterSpacing = -0.01f
        })
        col.addView(text("Your commitment, enforced.", 12.5f, Ui.TEXT_DIM))
        header.addView(col)
        return header
    }

    // ──────────────────────── hero ────────────────────────

    private fun heroCard(): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = heroGradient(
                0xFF2A1208.toInt(), 0xFF21111E.toInt(), 0xFF2A0A18.toInt(), dpf(Radius.xl)
            ).apply { setStroke(dp(1), 0x44FF7A18) }
            setPadding(dp(22), dp(22), dp(22), dp(22))
            elevation = dpf(8f)
        }

        val eyebrow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        eyebrow.addView(icon(R.drawable.ic_flame, Ui.ACCENT, 16).apply {
            (layoutParams as LinearLayout.LayoutParams).rightMargin = dp(7)
        })
        eyebrow.addView(text("CURRENT STREAK", 12f, Ui.ACCENT, bold = true).apply {
            letterSpacing = 0.14f
        })
        c.addView(eyebrow)

        val rowVal = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(0, dp(8), 0, 0)
        }
        streakValue = stat("0", 56f, Ui.TEXT)
        rowVal.addView(streakValue)
        streakSub = text("days clean", 16f, Ui.TEXT_DIM).apply {
            setPadding(dp(10), 0, 0, dp(13))
        }
        rowVal.addView(streakSub)
        c.addView(rowVal)

        c.addView(divider(Space.lg, Space.md))

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        footer.addView(icon(R.drawable.ic_trophy, Ui.AMBER, 16).apply {
            (layoutParams as LinearLayout.LayoutParams).rightMargin = dp(8)
        })
        footer.addView(text("Best", 14f, Ui.TEXT_DIM).apply { layoutParams = row(0, 1f) })
        bestValue = text("0 days", 14f, Ui.AMBER, bold = true)
        footer.addView(bestValue)
        c.addView(footer)
        return c
    }

    private fun examCountdownCard(): LinearLayout {
        val hasExam = ExamManager.hasExam(this)
        val c = card(padding = 18)
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(iconChip(R.drawable.ic_target, Ui.BLUE, tintWash(Ui.BLUE), 36, Radius.sm).also {
            (it.layoutParams as LinearLayout.LayoutParams).rightMargin = dp(Space.md)
        })
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; layoutParams = row(0, 1f)
        }
        col.addView(text("EXAM COUNTDOWN", 11.5f, Ui.TEXT_DIM, bold = true).apply {
            letterSpacing = 0.1f
        })
        val days = ExamManager.daysLeft(this)
        if (hasExam) {
            col.addView(text("${ExamManager.dateLabel(ExamManager.examDay(this))} · $days days left",
                15f, Ui.TEXT, bold = true), lp(topMargin = dp(3)))
        } else {
            col.addView(text("No exam date set", 15f, Ui.TEXT, bold = true), lp(topMargin = dp(3)))
            col.addView(text("Set it in Study to get a daily target", 12.5f, Ui.TEXT_MUTE),
                lp(topMargin = dp(2)))
        }
        head.addView(col)
        c.addView(head)
        c.setOnClickListener { startActivity(Intent(this, StudyLogActivity::class.java)) }
        c.isClickable = true
        return c
    }

    // ──────────────────────── protection status ────────────────────────

    private fun statusCard(): LinearLayout {
        val c = card()
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(title("PROTECTION STATUS").apply { layoutParams = row(0, 1f) })
        head.addView(text("", 12f, Ui.GREEN, bold = true).also { armedBadge = it })
        c.addView(head)
        statusContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(Space.md), 0, 0)
        }
        c.addView(statusContainer)
        return c
    }

    private lateinit var armedBadge: TextView

    private fun lockdownBanner(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(Space.lg), dp(14), dp(Space.lg), dp(14))
        }
    }

    private fun sectionLabel(t: String) = title(t).apply { setPadding(dp(Space.xs), 0, 0, 0) }

    // ──────────────────────── DNS tile ────────────────────────

    private fun dnsFilterTile(): LinearLayout {
        val prefs = getSharedPreferences("focusguard_policy", MODE_PRIVATE)
        val lockedOn = prefs.getBoolean("dns_filter_enabled", false)
        val running = DnsFilterService.isRunning
        val enabled = lockedOn || running

        val rowV = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Ui.SURFACE, dpf(Radius.md), Ui.STROKE, dp(1))
            setPadding(dp(14), dp(14), dp(16), dp(14))
            isClickable = true
        }
        val tint = if (enabled) Ui.GREEN else Ui.ACCENT
        rowV.addView(iconChip(
            if (enabled) R.drawable.ic_shield_lock else R.drawable.ic_globe,
            tint, tintWash(tint), 42, Radius.md
        ))
        val labelCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, dp(10), 0)
            layoutParams = row(0, 1f)
        }
        labelCol.addView(text("DNS adult filter", 15.5f, Ui.TEXT, bold = true))
        labelCol.addView(text(
            if (enabled) "Cloudflare Family · locked on" else "Cloudflare Family · one-way switch",
            12.5f, Ui.TEXT_MUTE
        ).apply { setPadding(0, dp(2), 0, 0); maxLines = 1 })
        rowV.addView(labelCol)

        if (enabled) {
            rowV.addView(text(if (running) "LOCKED" else "REPAIR", 11.5f, Ui.GREEN, bold = true).apply {
                background = rounded(Ui.GREEN_SOFT, dpf(Radius.pill)); setPadding(dp(11), dp(5), dp(11), dp(5))
            })
            rowV.setOnClickListener {
                if (!running) startDnsFilter()
                toast("DNS filter is locked on")
            }
        } else {
            val sw = switch(false, enabled = true) { isChecked ->
                if (isChecked) {
                    prefs.edit().putBoolean("dns_filter_enabled", true).apply()
                    startDnsFilter()
                    refresh()
                }
            }
            rowV.addView(sw, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                leftMargin = dp(Space.sm)
            })
            rowV.setOnClickListener { sw.isChecked = true }
        }
        return rowV
    }

    // ──────────────────────── danger zone ────────────────────────

    private fun dangerZone(): LinearLayout {
        val zone = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        zone.addView(sectionLabel("DANGER ZONE"))
        lockdownButton = dangerButton("Engage full lockdown", R.drawable.ic_shield_lock) {
            confirmLockdown()
        }
        zone.addView(lockdownButton, lp(topMargin = dp(Space.md)))
        lockdownInfo = text(
            "Lockdown blocks ADB & Developer Options. Removal afterward = Recovery factory reset only.",
            12f, Ui.TEXT_MUTE
        ).apply {
            gravity = Gravity.CENTER
            setPadding(dp(Space.sm), dp(Space.md), dp(Space.sm), 0)
        }
        zone.addView(lockdownInfo)
        return zone
    }

    // ──────────────────────── state / refresh ────────────────────────

    private fun refresh() {
        val owner = GuardDeviceAdminReceiver.isDeviceOwner(this)
        val a11y = isAccessibilityEnabled()
        val overlay = Settings.canDrawOverlays(this)
        val battery = isIgnoringBatteryOptimizations()
        val prefs = getSharedPreferences("focusguard_policy", MODE_PRIVATE)
        val maintenance = prefs.getBoolean("maintenance_mode", true)
        val lockdownConfirmed = prefs.getBoolean("lockdown_confirmed", false)
        val dnsOn = prefs.getBoolean("dns_filter_enabled", false) || DnsFilterService.isRunning

        val s = streak.currentStreakDays()
        streakValue.text = s.toString()
        streakSub.text = if (s == 1) "day clean" else "days clean"
        bestValue.text = "${streak.bestStreakDays()} days"

        // Protection status — scannable summary with armed count.
        val scopedInstalled = GuardAccessibilityService.SCOPED_APPS.keys.any {
            try { packageManager.getApplicationInfo(it, 0); true } catch (_: Exception) { false }
        }
        data class Stat(val label: String, val on: Boolean, val icon: Int, val sub: String?)
        val stats = listOf(
            Stat("Device Owner", owner, R.drawable.ic_shield, "Full system policy control"),
            Stat("Accessibility", a11y, R.drawable.ic_accessibility, "Live screen guard"),
            Stat("Overlay", overlay, R.drawable.ic_layers, "Block screens on top"),
            Stat("DNS filter", dnsOn, R.drawable.ic_shield_lock, "Cloudflare Family"),
            Stat("Hardcore locks", owner && !maintenance, R.drawable.ic_lock, "ADB & installs locked"),
            Stat("Battery survival", battery, R.drawable.ic_battery, "Runs while screen off"),
            Stat("Scoped block", scopedInstalled, R.drawable.ic_globe, "In-app browsers blocked")
        )
        statusContainer.removeAllViews()
        stats.forEachIndexed { i, st ->
            statusContainer.addView(
                pill(st.label, st.on, st.icon, st.sub),
                if (i == 0) lp() else lp(topMargin = dp(Space.sm))
            )
        }
        val armed = stats.count { it.on }
        armedBadge.text = "$armed/${stats.size} armed"
        armedBadge.setTextColor(if (armed >= stats.size - 1) Ui.GREEN else Ui.AMBER)

        // Setup section — only show grants that are still outstanding.
        rebuildSetup(a11y, overlay, battery)

        // Lockdown banner
        lockBanner.removeAllViews()
        val locked = owner && !maintenance && lockdownConfirmed
        val (bg, stroke, glyph, msg, color) = when {
            locked -> Quint(Ui.GREEN_SOFT, 0x5534D399, R.drawable.ic_shield_lock, "Full lockdown active", Ui.GREEN)
            owner -> Quint(Ui.AMBER_SOFT, 0x55FBBF24, R.drawable.ic_wrench, "Maintenance mode — ADB open", Ui.AMBER)
            else -> Quint(Ui.RED_SOFT, 0x55F87171, R.drawable.ic_warning, "Not Device Owner — run dpm setup", Ui.RED)
        }
        lockBanner.background = rounded(bg, dpf(Radius.md), stroke, dp(1))
        lockBanner.addView(icon(glyph, color, 20).apply {
            (layoutParams as LinearLayout.LayoutParams).rightMargin = dp(12)
        })
        lockBanner.addView(text(msg, 13.5f, color, bold = true))

        // In full lockdown: hide every UI escape hatch so only recovery reset remains.
        val showMaintenance = !locked
        maintenanceSection.visibility = if (showMaintenance) View.VISIBLE else View.GONE
        lockdownButton.visibility = if (showMaintenance) View.VISIBLE else View.GONE
        lockdownInfo.visibility = if (showMaintenance) View.VISIBLE else View.GONE
    }

    /** Setup section recedes as grants are satisfied: each outstanding permission
     *  is an actionable card; when all are granted it collapses to one calm line. */
    private fun rebuildSetup(a11y: Boolean, overlay: Boolean, battery: Boolean) {
        setupSection.removeAllViews()
        data class Setup(val label: String, val sub: String, val icon: Int,
                         val granted: Boolean, val action: () -> Unit)
        val items = listOf(
            Setup("Grant accessibility", "Required for live blocking", R.drawable.ic_accessibility, a11y) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            Setup("Grant overlay permission", "Lets FocusGuard cover blocked screens", R.drawable.ic_layers, overlay) {
                if (!Settings.canDrawOverlays(this)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            },
            Setup("Keep alive (battery)", "Oppo / ColorOS survival setup", R.drawable.ic_battery, battery) {
                startActivity(Intent(this, PowerOnboardingActivity::class.java))
            }
        )
        val outstanding = items.filter { !it.granted }
        setupSection.addView(sectionLabel("SETUP & PERMISSIONS"))
        if (outstanding.isEmpty()) {
            val done = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(Ui.GREEN_SOFT, dpf(Radius.md), 0x3334D399, dp(1))
                setPadding(dp(Space.lg), dp(14), dp(Space.lg), dp(14))
            }
            done.addView(icon(R.drawable.ic_check, Ui.GREEN, 20).apply {
                (layoutParams as LinearLayout.LayoutParams).rightMargin = dp(12)
            })
            done.addView(text("All permissions granted", 14f, Ui.GREEN, bold = true))
            setupSection.addView(done, lp(topMargin = dp(Space.md)))
        } else {
            outstanding.forEach {
                setupSection.addView(
                    tile(it.icon, it.label, it.sub, Ui.BLUE, it.action),
                    lp(topMargin = dp(Space.md))
                )
            }
        }
    }

    private data class Quint(val a: Int, val b: Int, val c: Int, val d: String, val e: Int)

    private fun confirmRelapse() {
        AlertDialog.Builder(this)
            .setTitle("Reset your streak?")
            .setMessage("This banks your current run as your best (if higher) and starts again from zero. Be honest with yourself.")
            .setPositiveButton("Reset") { _, _ -> streak.recordRelapse(); refresh() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun confirmLockdown() {
        AlertDialog.Builder(this)
            .setTitle("Engage FULL LOCKDOWN?")
            .setMessage(
                "⚠️  THIS IS PERMANENT.\n\n" +
                "This will:\n" +
                "• Block ADB & USB debugging\n" +
                "• Block app installs & uninstalls\n" +
                "• Block Safe Mode\n" +
                "• Block factory reset from Settings\n" +
                "• Block VPN\n" +
                "• Hide all maintenance options from the app\n\n" +
                "The ONLY way out is a Recovery Mode hardware factory reset " +
                "(Power + Volume Down).\n\n" +
                "Proceed?"
            )
            .setPositiveButton("LOCK DOWN") { _, _ ->
                getSharedPreferences("focusguard_policy", MODE_PRIVATE)
                    .edit()
                    .putBoolean("maintenance_mode", false)
                    .putBoolean("lockdown_confirmed", true)
                    .apply()
                GuardDeviceAdminReceiver.applyAllProtections(this)
                refresh()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun ensureServicesRunning() {
        try {
            val svc = Intent(this, GuardForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc) else startService(svc)
        } catch (_: Exception) {}
        ensureDnsFilterRunning()
    }

    private fun ensureDnsFilterRunning() {
        val prefs = getSharedPreferences("focusguard_policy", MODE_PRIVATE)
        if (prefs.getBoolean("dns_filter_enabled", false) && !DnsFilterService.isRunning) {
            startDnsFilter()
        }
    }

    private fun startDnsFilter() {
        // System-DNS lock: no VPN consent prompt, no notification, instant.
        SystemDnsEnforcer.enable(this) { ok ->
            if (ok) {
                DnsFilterService.start(this)
            } else {
                showDeviceOwnerRequiredDialog()
            }
        }
    }

    private fun showDeviceOwnerRequiredDialog() {
        val owner = GuardDeviceAdminReceiver.isDeviceOwner(this)
        val adbCmd = "adb shell dpm set-device-owner com.focus.guard/.GuardDeviceAdminReceiver"
        val msg = if (!owner) {
            "To lock the system DNS, FocusGuard must be Device Owner. " +
            "Run this command once on a freshly-wiped device with no accounts:\n\n" +
            adbCmd + "\n\n" +
            "Once set, DNS lock works without a VPN and cannot be removed from Settings."
        } else {
            "Could not write Private DNS. This usually means the system rejected the " +
            "hostname (e.g. the network is blocking DNS-over-TLS probes, or an OEM " +
            "policy is in the way). See logcat tag 'SystemDns' for the exact return code. " +
            "Try toggling the switch again, or reboot once to clear stale state."
        }
        AlertDialog.Builder(this)
            .setTitle("DNS lock unavailable")
            .setMessage(msg)
            .setPositiveButton(if (owner) "Close" else "Copy ADB command") { _, _ ->
                if (!owner) {
                    val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("adb", adbCmd))
                    toast("Command copied")
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expected = "$packageName/$packageName.GuardAccessibilityService"
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) return true
        }
        return false
    }

    /** True if we're on the AOSP device-idle whitelist (the flag the framework
     *  actually honours for letting foreground services survive screen-off). */
    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun toast(m: String) =
        android.widget.Toast.makeText(this, m, android.widget.Toast.LENGTH_SHORT).show()
}
