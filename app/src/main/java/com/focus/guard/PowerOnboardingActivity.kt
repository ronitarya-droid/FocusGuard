package com.focus.guard

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.focus.guard.Ui.Radius
import com.focus.guard.Ui.Space
import com.focus.guard.Ui.card
import com.focus.guard.Ui.dp
import com.focus.guard.Ui.dpf
import com.focus.guard.Ui.heroGradient
import com.focus.guard.Ui.icon
import com.focus.guard.Ui.lp
import com.focus.guard.Ui.pill
import com.focus.guard.Ui.primaryButton
import com.focus.guard.Ui.rounded
import com.focus.guard.Ui.row
import com.focus.guard.Ui.scaffold
import com.focus.guard.Ui.text

/**
 * Oppo / ColorOS survival onboarding. Two independent grants keep FocusGuard's
 * services alive: (1) the AOSP "ignore battery optimization" whitelist, which the
 * framework actually honours, and (2) ColorOS's separate, API-less "Auto-start"
 * toggle that only a human tap can flip. This screen automates #1 and hand-holds
 * #2–#4. All deep-link logic and status reporting is preserved exactly.
 */
class PowerOnboardingActivity : ComponentActivity() {

    private lateinit var statusContainer: LinearLayout
    private val power by lazy { getSystemService(POWER_SERVICE) as PowerManager }

    private val batteryOptLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(scaffold(
            title = "Keep alive",
            subtitle = "Oppo / ColorOS survival setup",
            onBack = { finish() }
        ) { content ->
            content.addView(hero(), lp(topMargin = dp(Space.sm)))

            content.addView(stepCard(1, R.drawable.ic_battery, "Battery optimization",
                "Lets FocusGuard run while the screen is off. Opens the official system " +
                "dialog — just tap \"Allow\".", "Allow background run") {
                requestBatteryOptimizationIgnore()
            }, lp(topMargin = dp(Space.lg)))

            content.addView(stepCard(2, R.drawable.ic_bolt, "Auto-start (ColorOS)",
                "ColorOS blocks sideloaded apps from self-starting. This is separate from " +
                "battery optimization and has no API — find FocusGuard in the list and turn " +
                "its switch ON.", "Open Auto-start list") {
                openAutoStartList()
            }, lp(topMargin = dp(Space.md)))

            content.addView(stepCard(3, R.drawable.ic_settings, "App power details",
                "Set \"Battery usage\" to Unrestricted and turn OFF \"Smart power saving\" / " +
                "\"Sleep standby optimization\" if present.", "Open power details") {
                openAppBatteryDetail()
            }, lp(topMargin = dp(Space.md)))

            content.addView(stepCard(4, R.drawable.ic_lock, "Lock in Recents",
                "Open recent apps, find the FocusGuard card and pull it DOWN (or tap the " +
                "lock icon) so ColorOS won't clear it. No button — just do it once.", null) {},
                lp(topMargin = dp(Space.md)))

            content.addView(text("LIVE STATUS", 12f, Ui.TEXT_DIM, bold = true).apply {
                letterSpacing = 0.12f; setPadding(dp(Space.xs), 0, 0, 0)
            }, lp(topMargin = dp(Space.xxl)))
            statusContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            content.addView(statusContainer, lp(topMargin = dp(Space.md)))
        })
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun hero(): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = heroGradient(
                0xFF2A1208.toInt(), 0xFF24121A.toInt(), 0xFF2A0A18.toInt(), dpf(Radius.xl)
            ).apply { setStroke(dp(1), 0x44FF7A18) }
            setPadding(dp(22), dp(22), dp(22), dp(22))
            elevation = dpf(6f)
        }
        c.addView(icon(R.drawable.ic_battery, Ui.ACCENT, 34))
        c.addView(text("Don't let FocusGuard get killed", 19f, Ui.TEXT, bold = true).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(Space.md), 0, 0)
        })
        c.addView(text(
            "ColorOS aggressively freezes background apps. Without these settings, the " +
            "guard service gets killed overnight and blocking silently stops.",
            13f, Ui.TEXT_DIM).apply {
                gravity = Gravity.CENTER; setPadding(0, dp(Space.sm), 0, 0)
            })
        return c
    }

    private fun stepCard(n: Int, iconRes: Int, title: String, body: String,
                         cta: String?, action: () -> Unit): LinearLayout {
        val c = card(padding = 18)
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        // Numbered step badge
        head.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = rounded(Ui.tintWash(Ui.ACCENT), dpf(Radius.sm))
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)).apply { rightMargin = dp(Space.md) }
            addView(text("$n", 14f, Ui.ACCENT, bold = true))
        })
        head.addView(icon(iconRes, Ui.TEXT, 18).apply {
            (layoutParams as LinearLayout.LayoutParams).rightMargin = dp(Space.sm)
        })
        head.addView(text(title, 15.5f, Ui.TEXT, bold = true).apply { layoutParams = row(0, 1f) })
        c.addView(head)
        c.addView(text(body, 13f, Ui.TEXT_DIM).apply { setPadding(0, dp(Space.md), 0, 0) })
        if (cta != null) c.addView(primaryButton(cta) { action() }, lp(topMargin = dp(Space.md)))
        return c
    }

    private fun refreshStatus() {
        if (!::statusContainer.isInitialized) return
        statusContainer.removeAllViews()
        val ignoring = power.isIgnoringBatteryOptimizations(packageName)
        statusContainer.addView(pill("Battery optimization ignored", ignoring, R.drawable.ic_battery))
        statusContainer.addView(pill("App auto-start", getAutoStartHint(), R.drawable.ic_bolt),
            lp(topMargin = dp(Space.sm)))
        statusContainer.addView(text(
            "Standby bucket: ${currentBucketLabel()} — ColorOS may still show RESTRICTED " +
            "even after step 1; that's normal. The battery grant is what the framework honours.",
            11.5f, Ui.TEXT_MUTE).apply {
                background = rounded(Ui.SURFACE_LO, dpf(Radius.md))
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }, lp(topMargin = dp(Space.md)))
    }

    private fun getAutoStartHint(): Boolean = power.isIgnoringBatteryOptimizations(packageName)

    private fun currentBucketLabel(): String = when (standbyBucket()) {
        5 -> "RESTRICTED"
        10 -> "ACTIVE"
        20 -> "WORKING_SET"
        30 -> "FREQUENT"
        40 -> "RARE"
        else -> "EXEMPTED/other(${standbyBucket()})"
    }

    private fun standbyBucket(): Int = try {
        val um = getSystemService("usagestats") as android.app.usage.UsageStatsManager
        um.appStandbyBucket
    } catch (_: Exception) { -1 }

    // ──────────────────── actions (preserved) ────────────────────

    private fun requestBatteryOptimizationIgnore() {
        try {
            val intent = if (power.isIgnoringBatteryOptimizations(packageName)) {
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            } else {
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
            }
            batteryOptLauncher.launch(intent)
        } catch (_: Exception) {
            try {
                batteryOptLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                toast("Could not open battery settings. Open Settings › Apps › FocusGuard › Battery manually.")
            }
        }
    }

    private fun openAutoStartList() {
        val candidates = listOf(
            "com.oplus.battery/com.oplus.startupapp.view.StartupAppListActivity",
            "com.coloros.safecenter/com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.oppo.safe/com.oppo.safe.permission.startup.StartupAppListActivity",
            "com.oplus.battery/com.oplus.startupapp.view.OptimizationAutoStartActivity"
        )
        for (cn in candidates) {
            if (launchComponent(cn)) return
        }
        toast("Auto-start screen not found. Open Settings › Battery › App Auto-start manually.")
        openAppBatteryDetail()
    }

    private fun openAppBatteryDetail() {
        if (launchComponent(null, action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) return
        try {
            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (_: Exception) {
            toast("Could not open app details.")
        }
    }

    private fun launchComponent(component: String?, action: String? = null): Boolean {
        try {
            val i = Intent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (action != null) i.action = action
            if (component != null) {
                val parts = component.split("/")
                if (parts.size != 2) return false
                i.setClassName(parts[0], parts[1])
            }
            val pm = packageManager
            if (i.resolveActivity(pm) == null) return false
            startActivity(i)
            return true
        } catch (_: Exception) {
            return false
        }
    }

    private fun toast(m: String) =
        android.widget.Toast.makeText(this, m, android.widget.Toast.LENGTH_LONG).show()
}
