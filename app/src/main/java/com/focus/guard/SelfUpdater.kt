package com.focus.guard

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.util.Log
import android.widget.Toast

/**
 * Lets FocusGuard update ITSELF without adb.
 *
 * Because the app is Device Owner, its own PackageInstaller sessions are allowed
 * even while `DISALLOW_INSTALL_APPS` blocks every *user-initiated* install. So we
 * can ship a new APK by: download it in a browser (or copy via USB file-transfer
 * — MTP still works, only adb is blocked) -> open FocusGuard -> "Update from file"
 * -> pick the APK. It installs silently and relaunches.
 *
 * This is the escape from the "debugging is blocked so I can't reinstall" trap.
 */
object SelfUpdater {
    const val ACTION_INSTALL_COMPLETE = "com.focus.guard.INSTALL_COMPLETE"
    private const val TAG = "FocusGuardUpdater"

    fun installApk(context: Context, apkUri: Uri) {
        try {
            val resolver = context.contentResolver
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            ).apply { setAppPackageName(context.packageName) }

            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                resolver.openInputStream(apkUri)?.use { input ->
                    session.openWrite("focusguard_update", 0, -1).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                } ?: run {
                    Toast.makeText(context, "Could not read APK", Toast.LENGTH_LONG).show()
                    return
                }

                val intent = Intent(ACTION_INSTALL_COMPLETE).setPackage(context.packageName)
                val pi = PendingIntent.getBroadcast(
                    context, sessionId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                session.commit(pi.intentSender)
            }
            Toast.makeText(context, "Installing update…", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Self-update failed", e)
            Toast.makeText(context, "Update failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
