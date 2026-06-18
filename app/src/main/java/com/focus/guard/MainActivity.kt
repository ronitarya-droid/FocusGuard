package com.focus.guard

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.focus.guard.Ui.card
import com.focus.guard.Ui.dangerButton
import com.focus.guard.Ui.dp
import com.focus.guard.Ui.gradient
import com.focus.guard.Ui.lp
import com.focus.guard.Ui.pill
import com.focus.guard.Ui.primaryButton
import com.focus.guard.Ui.rounded
import com.focus.guard.Ui.text
import com.focus.guard.Ui.tile
import com.focus.guard.Ui.title

class MainActivity : ComponentActivity() {

    private lateinit var streak: StreakManager

    // Live views we refresh in onResume.
    private lateinit var streakValue: TextView
    private lateinit var bestValue: TextView
    private lateinit var statusContainer: LinearLayout
    private lateinit var lockBanner: LinearLayout
    private lateinit var maintenanceSection: LinearLayout
    private lateinit var lockdownButton: TextView
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
            setBackgroundColor(Ui.BG)
            setPadding(dp(20), dp(28), dp(20), dp(32))
        }

        content.addView(brandRow())
        content.addView(heroCard(), lp(topMargin = dp(20)))
        content.addView(examCountdownCard(), lp(topMargin = dp(14)))
        content.addView(statusCard(), lp(topMargin = dp(16)))
        content.addView(lockdownBanner().also { lockBanner = it }, lp(topMargin = dp(16)))
        content.addView(sectionLabel("BLOCKING"), lp(topMargin = dp(24)))
        content.addView(tile("📵", "Block apps") {
            startActivity(Intent(this, AppPickerActivity::class.java))
        }, lp(topMargin = dp(10)))
        content.addView(tile("🌐", "Block sites & words") {
            startActivity(Intent(this, WebsiteBlockActivity::class.java))
        }, lp(topMargin = dp(10)))
        content.addView(dnsFilterTile(), lp(topMargin = dp(10)))

        content.addView(sectionLabel("REWARDS"), lp(topMargin = dp(24)))
        content.addView(tile("⛏", "Log study & Minecraft credit") {
            startActivity(Intent(this, StudyLogActivity::class.java))
        }, lp(topMargin = dp(10)))
        content.addView(tile("📝", "Private notes (PIN)") {
            startActivity(Intent(this, NotesActivity::class.java))
        }, lp(topMargin = dp(10)))
        content.addView(tile("⏰", "Alarms (maths to dismiss)") {
            startActivity(Intent(this, AlarmListActivity::class.java))
        }, lp(topMargin = dp(10)))

        content.addView(sectionLabel("SETUP & PERMISSIONS"), lp(topMargin = dp(20)))
        content.addView(tile("♿", "Grant accessibility") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, lp(topMargin = dp(10)))
        content.addView(tile("🪟", "Grant overlay permission") {
            if (!Settings.canDrawOverlays(this))
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }, lp(topMargin = dp(10)))

        maintenanceSection = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        maintenanceSection.addView(sectionLabel("MAINTENANCE"))
        maintenanceSection.addView(tile("⬆️", "Update from APK file") {
            pickApk.launch(arrayOf("application/vnd.android.package-archive", "*/*"))
        }, lp(topMargin = dp(10)))
        maintenanceSection.addView(tile("🔓", "Re-enable ADB (maintenance)") {
            getSharedPreferences("focusguard_policy", MODE_PRIVATE)
                .edit()
                .putBoolean("maintenance_mode", true)
                .putBoolean("lockdown_confirmed", false)
                .apply()
            GuardDeviceAdminReceiver.unlockDebugging(this)
            refresh()
            toast("ADB re-enabled — debugging unlocked")
        }, lp(topMargin = dp(10)))
        maintenanceSection.addView(tile("🩹", "I slipped — reset streak") {
            confirmRelapse()
        }, lp(topMargin = dp(10)))
        content.addView(maintenanceSection)

        lockdownButton = primaryButton("🔒  Engage Full Lockdown") { confirmLockdown() }
        content.addView(lockdownButton, lp(topMargin = dp(28)))
        lockdownInfo = text(
            "Lockdown blocks ADB & Developer Options. Removal afterward = Recovery factory reset only.",
            12f, Ui.TEXT_MUTE).apply {
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(14), dp(8), 0)
            }
        content.addView(lockdownInfo, lp(topMargin = 0))

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Ui.BG)
            isFillViewport = true
            addView(content)
        })
        refresh()
    }

    override fun onResume() {
        super.onResume()
        GuardDeviceAdminReceiver.applyAllProtections(this)
        ensureDnsFilterRunning()
        refresh()
    }

    // ---- composed sections ----

    private fun brandRow(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val badge = TextView(this).apply {
            text = "🛡"
            textSize = 22f
            background = gradient(Ui.ACCENT, Ui.ACCENT_2, dp(14).toFloat())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
            setPadding(0, dp(4), 0, 0)
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        col.addView(text("FocusGuard", 22f, Ui.TEXT, bold = true).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        })
        col.addView(text("Your commitment, enforced.", 13f, Ui.TEXT_DIM))
        row.addView(badge); row.addView(col)
        return row
    }

    private fun heroCard(): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = gradient(0xFF2A1208.toInt(), 0xFF2A0A18.toInt(), dp(24).toFloat()).apply {
                setStroke(dp(1), 0x44FF7A18)
            }
            setPadding(dp(22), dp(22), dp(22), dp(22))
        }
        c.addView(text("CURRENT STREAK", 12f, Ui.ACCENT, bold = true).apply {
            letterSpacing = 0.16f
        })
        val rowVal = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(0, dp(6), 0, 0)
        }
        streakValue = TextView(this).apply {
            text = "0"; setTextColor(Ui.TEXT); textSize = 52f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        rowVal.addView(streakValue)
        rowVal.addView(text("days clean", 16f, Ui.TEXT_DIM).apply {
            setPadding(dp(10), 0, 0, dp(12))
        })
        c.addView(rowVal)

        val divider = android.view.View(this).apply {
            setBackgroundColor(0x22FFFFFF)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
                .apply { topMargin = dp(16); bottomMargin = dp(14) }
        }
        c.addView(divider)
        val best = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        best.addView(text("🏆  Best  ", 14f, Ui.TEXT_DIM))
        bestValue = text("0 days", 14f, Ui.AMBER, bold = true)
        best.addView(bestValue)
        c.addView(best)
        return c
    }

    private fun examCountdownCard(): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Ui.SURFACE, dp(20).toFloat(), Ui.STROKE, dp(1))
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        c.addView(text("EXAM COUNTDOWN", 12f, Ui.TEXT_DIM, bold = true))
        val days = ExamManager.daysLeft(this)
        val target = ExamManager.targetPerDay(this)
        val line = if (ExamManager.hasExam(this)) {
            "${ExamManager.dateLabel(ExamManager.examDay(this))} · $days days left · target ${target.toInt()}% / day"
        } else {
            "Set exam date in Study to calculate daily syllabus target"
        }
        c.addView(text(line, 15f, Ui.TEXT, bold = true), lp(topMargin = dp(5)))
        c.addView(text("Syllabus done: ${ExamManager.syllabusPercent(this)}%", 12.5f, Ui.TEXT_MUTE), lp(topMargin = dp(4)))
        return c
    }

    private fun statusCard(): LinearLayout {
        val c = card()
        c.addView(title("PROTECTION STATUS"))
        statusContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, 0)
        }
        c.addView(statusContainer)
        return c
    }

    private fun lockdownBanner(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
    }

    private fun sectionLabel(t: String) = title(t).apply {
        setPadding(dp(4), 0, 0, 0)
    }

    private fun dnsFilterTile(): LinearLayout {
        val prefs = getSharedPreferences("focusguard_policy", MODE_PRIVATE)
        val lockedOn = prefs.getBoolean("dns_filter_enabled", false)
        val running = DnsFilterService.isRunning
        val enabled = lockedOn || running

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Ui.SURFACE, dp(16).toFloat(), Ui.STROKE, dp(1))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            isClickable = true
        }
        val badge = TextView(this).apply {
            text = if (enabled) "🔒" else "🌐"
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
            gravity = Gravity.CENTER
        }
        val labelCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        labelCol.addView(TextView(this).apply {
            text = "DNS Adult Filter"
            setTextColor(Ui.TEXT)
            textSize = 15f
            setPadding(0, 0, 0, dp(2))
        })
        labelCol.addView(TextView(this).apply {
            text = if (enabled) {
                "family.cloudflare-dns.com · locked ON · no notification"
            } else {
                "Cloudflare Family Filter · one-way switch · OFF"
            }
            setTextColor(Ui.TEXT_MUTE)
            textSize = 12f
        })
        val status = TextView(this).apply {
            text = if (enabled) {
                if (running) "LOCKED" else "REPAIR"
            } else {
                "OFF"
            }
            setTextColor(if (enabled) Ui.GREEN else Ui.RED)
            textSize = 12.5f
            setPadding(dp(6), 0, 0, 0)
        }
        val switch = Switch(this).apply {
            isChecked = enabled
            isEnabled = !lockedOn && !running
            setTextColor(if (enabled) Ui.GREEN else Ui.TEXT)
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    prefs.edit().putBoolean("dns_filter_enabled", true).apply()
                    startDnsFilter()
                    refresh()
                }
            }
        }
        row.addView(badge)
        row.addView(labelCol)
        row.addView(status)
        row.addView(switch, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp(12)
        })
        row.setOnClickListener {
            if (enabled) {
                if (!running) startDnsFilter()
                toast("DNS Filter is locked ON")
            } else {
                switch.isChecked = true
            }
        }
        return row
    }

    // ---- state ----

    private fun refresh() {
        val owner = GuardDeviceAdminReceiver.isDeviceOwner(this)
        val a11y = isAccessibilityEnabled()
        val overlay = Settings.canDrawOverlays(this)
        val prefs = getSharedPreferences("focusguard_policy", MODE_PRIVATE)
        val maintenance = prefs.getBoolean("maintenance_mode", true)
        val lockdownConfirmed = prefs.getBoolean("lockdown_confirmed", false)

        streakValue.text = streak.currentStreakDays().toString()
        bestValue.text = "${streak.bestStreakDays()} days"

        statusContainer.removeAllViews()
        statusContainer.addView(pill("Device Owner", owner))
        statusContainer.addView(pill("Accessibility", a11y), lp(topMargin = dp(8)))
        statusContainer.addView(pill("Overlay", overlay), lp(topMargin = dp(8)))
        statusContainer.addView(pill("DNS Filter (Cloudflare Family)", prefs.getBoolean("dns_filter_enabled", false) || DnsFilterService.isRunning), lp(topMargin = dp(8)))
        statusContainer.addView(pill("Hardcore locks", owner && !maintenance), lp(topMargin = dp(8)))

        lockBanner.removeAllViews()
        val locked = owner && !maintenance && lockdownConfirmed
        val (bg, stroke, glyph, msg, color) = if (locked)
            Quint(0x2234D399, 0x5534D399, "🔒", "FULL LOCKDOWN ACTIVE", Ui.GREEN)
        else if (owner)
            Quint(0x22FBBF24, 0x55FBBF24, "🛠", "Maintenance mode — ADB open", Ui.AMBER)
        else
            Quint(0x22F87171, 0x55F87171, "⚠", "Not Device Owner — run dpm setup", Ui.RED)
        lockBanner.background = rounded(bg, dp(16).toFloat(), stroke, dp(1))
        lockBanner.addView(text(glyph, 18f, color))
        lockBanner.addView(text(msg, 13.5f, color, bold = true).apply {
            setPadding(dp(12), 0, 0, 0)
        })

        // In full lockdown: hide every UI escape hatch so only recovery reset remains.
        val showMaintenance = !locked
        maintenanceSection.visibility = if (showMaintenance) View.VISIBLE else View.GONE
        lockdownButton.visibility = if (showMaintenance) View.VISIBLE else View.GONE
        lockdownInfo.visibility = if (showMaintenance) View.VISIBLE else View.GONE
    }

    private data class Quint(val a: Int, val b: Int, val c: String, val d: String, val e: Int)

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
                // Surface the reason — the lock genuinely could not be applied.
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

    private fun toast(m: String) =
        android.widget.Toast.makeText(this, m, android.widget.Toast.LENGTH_SHORT).show()
}
