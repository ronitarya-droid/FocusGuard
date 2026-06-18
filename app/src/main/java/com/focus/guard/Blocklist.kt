package com.focus.guard

import android.content.Context

/**
 * Persistent store for the user-managed blocklists: apps, website domains, and
 * keywords. The hard-coded social/adult defaults live in
 * [GuardAccessibilityService]; THIS store holds what the user adds at runtime
 * (app picker, website/keyword screen) plus packages auto-captured by
 * [PackageInstallReceiver]. SharedPreferences means choices survive reboot and
 * service restart, which an in-memory set would not.
 */
object Blocklist {
    private const val PREFS = "focusguard_blocklist"
    private const val KEY_APPS = "blocked_apps"
    private const val KEY_DOMAINS = "blocked_domains"
    private const val KEY_KEYWORDS = "blocked_keywords"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun read(context: Context, key: String): MutableSet<String> =
        HashSet(prefs(context).getStringSet(key, emptySet()) ?: emptySet())

    private fun write(context: Context, key: String, set: Set<String>) {
        prefs(context).edit().putStringSet(key, set).apply()
        pushLive(context)
    }

    /** Re-sync the running service's in-memory fast-path sets from storage. */
    fun pushLive(context: Context) {
        GuardAccessibilityService.blockedPackages.addAll(read(context, KEY_APPS))
        GuardAccessibilityService.userDomains.clear()
        GuardAccessibilityService.userDomains.addAll(read(context, KEY_DOMAINS))
        GuardAccessibilityService.userKeywords.clear()
        GuardAccessibilityService.userKeywords.addAll(read(context, KEY_KEYWORDS))
    }

    // ---- apps ----
    fun userBlocked(context: Context): MutableSet<String> = read(context, KEY_APPS)
    fun isBlocked(context: Context, pkg: String): Boolean = pkg in read(context, KEY_APPS)
    fun add(context: Context, pkg: String) {
        val s = read(context, KEY_APPS); if (s.add(pkg)) write(context, KEY_APPS, s)
    }
    fun remove(context: Context, pkg: String) {
        val s = read(context, KEY_APPS); if (s.remove(pkg)) write(context, KEY_APPS, s)
    }
    fun setBlocked(context: Context, pkg: String, blocked: Boolean) =
        if (blocked) add(context, pkg) else remove(context, pkg)

    // ---- domains ----
    fun userDomains(context: Context): MutableSet<String> = read(context, KEY_DOMAINS)
    fun addDomain(context: Context, raw: String) {
        val d = normalizeDomain(raw); if (d.isEmpty()) return
        val s = read(context, KEY_DOMAINS); if (s.add(d)) write(context, KEY_DOMAINS, s)
    }
    fun removeDomain(context: Context, d: String) {
        val s = read(context, KEY_DOMAINS); if (s.remove(d)) write(context, KEY_DOMAINS, s)
    }

    // ---- keywords ----
    fun userKeywords(context: Context): MutableSet<String> = read(context, KEY_KEYWORDS)
    fun addKeyword(context: Context, raw: String) {
        val k = raw.trim().lowercase(); if (k.isEmpty()) return
        val s = read(context, KEY_KEYWORDS); if (s.add(k)) write(context, KEY_KEYWORDS, s)
    }
    fun removeKeyword(context: Context, k: String) {
        val s = read(context, KEY_KEYWORDS); if (s.remove(k)) write(context, KEY_KEYWORDS, s)
    }

    /** Strip scheme/path/www so "https://www.Site.com/x" → "site.com". */
    private fun normalizeDomain(raw: String): String {
        var d = raw.trim().lowercase()
        d = d.removePrefix("https://").removePrefix("http://").removePrefix("www.")
        d = d.substringBefore('/').substringBefore('?').trim()
        return d
    }
}
