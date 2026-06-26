package com.focus.guard

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class GuardAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "FocusGuardA11y"
        // Flip to true to log every received event (type/pkg/cls) for debugging
        // which screens reach the service. Read with: adb logcat -s FocusGuardA11y
        private const val VERBOSE = false

        val blockedPackages = mutableSetOf(
            // --- social / distraction ---
            "com.instagram.android", "com.instagram.lite",
            "com.twitter.android", "com.x.android",
            "com.pinterest", "com.google.android.youtube", "app.revanced.android.youtube",
            "com.google.android.youtube.tv", "com.google.android.apps.youtube.music",
            "org.telegram.messenger", "org.telegram.messenger.web", "com.discord",
            "com.reddit.frontpage",
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
            "com.snapchat.android", "com.facebook.katana", "com.facebook.lite",
            "com.facebook.orca", "com.linkedin.android",
            "com.quora.android", "tv.twitch.android.app", "com.tumblr",
            "com.terabox.app", "com.bsbportal.music",
            // --- adult / triggering ---
            "net.xvideo", "com.xnxx.app", "com.pornhub.app", "com.xhamster.app",
            "com.redtube.app", "com.youporn.app", "com.adultapp", "com.sex.app",
            // --- ai companion / girlfriend ---
            "ai.character.app", "com.chai", "com.replika",
            "com.anima.android", "com.dreamgf.app", "com.kupid.android",
            "com.janitorai", "com.crushon.app", "com.candy.ai",
            "com.nastia.ai", "com.polybuzz", "com.muah.ai",
            "com.gptgirlfriend", "com.romanticai",
            // --- vpn / proxy / doh (bypass DNS filter) ---
            "com.cloudflare.onedotonedotonedotone",  // 1.1.1.1
            "com.nordvpn.android", "com.expressvpn.vpn", "com.surfshark.vpn.android",
            "com.protonvpn.android", "net.freevpn.freevpn", "com.tunnelbear.android",
            "com.hotspotshield.android", "org.torproject.android", "com.psiphon3",
            "com.windscribe.vpn", "windscribe.vpn", "com.vyprvpn",
            "com.anchorfree.hss", "com.anchorfree.mb",  // Hotspot Shield
            "xyz.zeronet.android", "com.bypassvpn",
            // --- app stores (third-party) ---
            "org.fdroid.fdroid", "org.fdroid.fdroid.privileged",
            "com.aptoide.partners", "com.aptoide", "com.apkpure.aegon",
            "com.apkmirror.io", "com.samsung.android.app.galaxyapps",
            "com.amazon.mShop.android.shopping",
            "com.aurora.store", "com.torroLibre.android",
            "com.oplus.appmarket", "com.coloros.appmarket", "com.heytap.market",
            // --- dating / hookup ---
            "com.tinder", "com.okcupid.okcupid", "com.badoo.android",
            "com.happn.happn", "com.match.android", "com.pof.android",
            "com.zoosk", "com.eharmony"
        )

        /** Packages the user explicitly wants to keep unblocked. Any package in
         *  this set is removed from both the in-memory blockedPackages set AND
         *  the persisted Blocklist storage every time the service connects, so
         *  even auto-captured installs get cleaned up. Visible to the package
         *  so PackageInstallReceiver can skip them when a new install is detected. */
        internal val WHITELISTED_PACKAGES = setOf(
            "com.whatsapp"                    // user needs WhatsApp + Web WhatsApp
        )

        /** Hard-block adult DOMAINS aggressively — these never appear in study
         *  material, so a single hit is a confident block. */
        val blockedDomains = setOf(
            // social (kept from before)
            "instagram.com","twitter.com","x.com","pinterest.com","youtube.com","youtu.be",
            "t.me","telegram.org","discord.com","discordapp.com","reddit.com",
            "tiktok.com","snapchat.com","facebook.com","messenger.com","quora.com",
            "twitch.tv","tumblr.com","terabox.com","teraboxapp.com",
            // adult — expanded
            "pornhub.com","xvideos.com","xnxx.com","xhamster.com","redtube.com",
            "youporn.com","tube8.com","brazzers.com","bangbros.com","spankbang.com",
            "youjizz.com","tnaflix.com","empflix.com","drtuber.com","nuvid.com",
            "porntrex.com","eporner.com","txxx.com","hclips.com","upornia.com",
            "hqporner.com","porn300.com","porngo.com","fuq.com","xmoviesforyou.com",
            "motherless.com","beeg.com","sunporno.com","porndig.com","4tube.com",
            "porn.com","sex.com","adult.com","xxx.com","fapello.com","fapello.is",
            "onlyfans.com","fansly.com","manyvids.com",
            "chaturbate.com","cam4.com","myfreecams.com","bongacams.com","stripchat.com",
            "livejasmin.com","camsoda.com","flirt4free.com",
            "hentai.com","nhentai.net","hentaihaven.xxx","hanime.tv","rule34.xxx",
            "e-hentai.org","hentai2read.com","fakku.net","hentaifox.com",
            "javhd.com","javmost.com","javlibrary.com","missav.com","javguru.com",
            "thumbzilla.com","gelbooru.com","danbooru.donmai.us","luscious.net",
            "iwara.tv","theporndude.com","pornpics.com","sex.xxx","camwhores.tv",
            // DNS-over-HTTPS providers: stop browser-level DNS bypass attempts.
            "dns.google","dns.quad9.net","cloudflare-dns.com","security.cloudflare-dns.com",
            "family.cloudflare-dns.com","mozilla.cloudflare-dns.com","doh.opendns.com",
            "doh.cloudflare-dns.com",
            // ai companion / girlfriend / roleplay
            "character.ai","chub.ai","chai.ml","replika.com","emochi.app","kindroid.ai",
            "janitorai.com","crushon.ai","candy.ai","nastia.ai","poe.com","talkie-ai.com",
            "anima.ai","dreamgf.ai","kupid.ai","myanima.ai","romanticai.com",
            "polybuzz.ai","aiscout.net","muah.ai","gptgirlfriend.com",
            "tavern.ai","sillytavern.ai","pygmalion.chat","agnt.chat",
            "pephop.ai","botify.ai","glimpse.chat","nomi.ai",
            // app stores (web)
            "aptoide.com","apkpure.com","apkmirror.com","f-droid.org",
            // dating
            "tinder.com","badoo.com","okcupid.com","happn.com",
            "match.com","pof.com","zoosk.com","eharmony.com"
        )

        /** User-added domains and keywords (from the website/keyword screen),
         *  loaded from [Blocklist] on service connect and refreshed live on edit.
         *  User-added keywords bounce on a SINGLE hit — the user chose them. */
        val userDomains = mutableSetOf<String>()
        val userKeywords = mutableSetOf<String>()

        /** Explicit adult keywords — single hit bounces. These are words that do
         *  NOT occur in legitimate biology/chemistry/JEE study text. */
        private val EXPLICIT_KEYWORDS = setOf(
            "pornhub","xvideos","xnxx","xhamster","brazzers","onlyfans","redtube",
            "youporn","spankbang","chaturbate","stripchat","bongacams","camgirl",
            "hentai","nhentai","javhd","milf","creampie","cumshot","blowjob",
            "deepthroat","gangbang","bukkake","handjob","threesome","fap",
            "porno","porn ","xxx ","nsfw",
            // ai companion / girlfriend
            "ai girlfriend","ai boyfriend","ai companion","character.ai","replika",
            "nsfw character","roleplay ai","ai chatbot","ai waifu","sexbot",
            "ai sexting","spicychat","c.ai "
        )

        /** Ambiguous words that CAN appear in JEE biology ("sexual reproduction",
         *  "nude descriptive geometry" is rare but possible). Require TWO distinct
         *  ambiguous hits before bouncing, so a single textbook word never trips. */
        private val AMBIGUOUS_KEYWORDS = setOf(
            "porn","sex","nude","naked","erotic","adult","escort","camsex","webcam"
        )

        /** BROWSER WHITELIST — the ONLY browsers allowed on this device. Any
         *  OTHER app that can open http(s) links is hard-blocked at runtime
         *  (see [onServiceConnected] → [blockRogueBrowsers]). This closes the
         *  "I'll just install a tiny privacy browser / in-app browser from a
         *  random store" bypass: only the famous, useful browsers below run,
         *  and they ALL get the full domain/keyword/DoH blocking applied. */
        private val ALLOWED_BROWSERS = setOf(
            "com.android.chrome", "com.chrome.beta", "com.chrome.dev",
            "com.microsoft.emmx",                           // Edge
            "org.mozilla.firefox", "org.mozilla.focus",
            "com.brave.browser",
            "com.duckduckgo.mobile.android",
            "com.heytap.browser", "com.coloros.browser",   // Oppo/ColorOS stock
            "com.sec.android.app.sbrowser"                  // Samsung Internet
        )

        /** Packages that resolve an http(s) intent but are NOT in the whitelist.
         *  Populated once on service connect and refreshed lazily. */
        private val rogueBrowsers = mutableSetOf<String>()

        /** Resource-id suffixes of the URL/address bar across common browsers.
         *  We read the domain ONLY from a node whose viewIdResourceName ends with
         *  one of these — so a link merely shown on the page never triggers. */
        private val URL_BAR_ID_HINTS = listOf(
            "url_bar", "url_field", "location_bar_edit_text", "urlbar",
            "mozac_browser_toolbar_url_view", "url", "address_bar",
            "search_box_text", "omnibox"
        )

        /** System surfaces we must NEVER kick out of — doing so breaks
         *  recents/home/notification shade and traps the user. The live
         *  thumbnails shown in Recents contain blocked-app text, which is
         *  exactly what caused "clearing recent apps" to bounce home. */
        private val SYSTEM_UI_PACKAGES = setOf(
            "com.android.systemui",
            "com.android.launcher", "com.android.launcher3",
            "com.oppo.launcher", "com.coloros.launcher", "com.oplus.launcher",
            "com.android.intentresolver", "android"
        )

        /** Settings / security apps where we DO defend ourselves.
         *  Includes ColorOS / Oppo variants. */
        private val SETTINGS_PACKAGES = setOf(
            "com.android.settings",
            "com.coloros.settings", "com.oppo.settings",
            "com.coloros.securitypermission", "com.coloros.safecenter",
            "com.coloros.phonemanager", "com.oppo.safe",
            "com.android.permissioncontroller", "com.google.android.permissioncontroller",
            "com.coloros.optimize", "com.coloros.privacypermissionsentry"
        )

        /** Apps that are PARTIALLY allowed — "scoped block". The local / offline
         *  features run normally, but the in-app browser + online-downloader
         *  surfaces are bounced. This is for apps the user genuinely needs (e.g.
         *  a video player for study lectures) whose only temptation vector is
         *  the built-in web browser / "online download" tab.
         *
         *  - Key   = package name
         *  - Value = lowercase class-name substrings that identify a BLOCKED
         *            screen (the browser / downloader surfaces). Every other
         *            screen in the app is allowed.
         *
         *  Scoped apps are auto-removed from the user blocklist on service
         *  connect (see onServiceConnected), so they can never get stuck in a
         *  whole-app bounce. The blocked-domain / blocked-keyword web rules
         *  ALSO apply inside these apps, so the downloader can't reach blocked
         *  sites even from a screen we didn't enumerate. */
        internal val SCOPED_APPS: Map<String, List<String>> = mapOf(
            // XPlayer (video.player.videoplayer): allow local playback only.
            // ONLY the in-app web delegate is blocked by class name — everything
            // else (FileExplorerActivity, PlayerActivity, MusicPlayActivity) runs
            // normally. We deliberately do NOT match on "explore"/"search"/"web"
            // because those substrings collide with local-file screens
            // (e.g. FileExplorerActivity → the local picker the app opens at launch).
            // The online-downloader is also caught by the URL-bar scan below.
            "video.player.videoplayer" to listOf("webdelegate", "webscreen", "webview"),
            // Google Play Store: block discovery surfaces. System package installer
            // handles updates so those keep working.
            "com.android.vending" to listOf(
                "topcharts", "categories", "browseapps", "browsegames"
            )
        )

        /** True if [pkg] is under a scoped (partial) block — i.e. allowed to run
         *  but with its browser/downloader screens bounced. */
        fun isScoped(pkg: String): Boolean = pkg in SCOPED_APPS

        /** Packages that exist ONLY to manage/revoke security & permissions.
         *  A window or event from any of these is a hard self-defense block,
         *  regardless of class name or on-screen text. This is what catches the
         *  ColorOS "Security & privacy → this app has your accessibility →
         *  Remove access" banner that the old text-only scan missed. */
        private val PURE_DANGER_PACKAGE_HINTS = listOf(
            "permissioncontroller",        // Android Safety Center + per-app revoke (CONFIRMED on CPH2363)
            "safetycenter",                // safety-center resources / styles
            "securitypermission",          // com.oplus.securitypermission / coloros variant
            "privacypermissionsentry",     // com.coloros.privacypermissionsentry
            "safecenter",                  // com.oplus.safecenter / coloros.safecenter
            "oppo.safe", "oplus.safe"      // oppo/oplus safe
        )

        /** Substrings of Settings activity/fragment class names that lead to
         *  places where FocusGuard could be weakened. Matching on className is
         *  far more robust than reading localized on-screen text — and it fires
         *  the instant the screen OPENS (typeWindowStateChanged), before the user
         *  can tap "Remove access". This is the primary fix for the one-tap
         *  accessibility-revoke bypass. */
        // ALWAYS-block class hints: these screens exist only to manage
        // accessibility/device-admin/security and have no legitimate non-FocusGuard
        // use we need to allow. Safe to block unconditionally.
        private val DANGER_CLASS_HINTS = listOf(
            "accessibility",          // AccessibilitySettings, *AccessibilityServiceActivity
            "deviceadmin",            // DeviceAdminSettings / DeviceAdminAdd
            "deviceadminadd",
            "securitypermission",
            "privacypermission",
            "safetycenter",           // Android 14 / ColorOS "Security & Privacy" hub
            "safetycentersubpages"
        )

        // CONTEXT-SENSITIVE class hints: these are the generic per-app screens
        // (app info, uninstall, force-stop, manage apps, overlay/usage access,
        // per-app permission revoke). They are used for EVERY app on the phone, so
        // blocking them unconditionally also blocks the user managing OTHER apps
        // and even the launcher dock. We block these ONLY when the screen text
        // actually names FocusGuard (handled in onAccessibilityEvent via the
        // window-text scan + isSelfDefenseScreen). This is the fix for "I can't
        // disable other apps / can't edit my dock".
        private val APP_SCOPED_CLASS_HINTS = listOf(
            "appinfo",                // App info page (uninstall/forcestop/disable)
            "installedappdetails",
            "manageapplications",
            "appops",                 // special app access (ColorOS)
            "overlay",                // display over other apps
            "usageaccess",
            "specialaccess",
            "permissionapps",         // PermissionController per-app revoke screens
            "apppermissions",
            "managestandardpermission"
        )

        /** Browser-internal settings screens (chromium scheme + about: URIs).
         *  These are where a user can enable DNS-over-HTTPS inside the browser,
         *  which would tunnel DNS queries around the system Private DNS lock
         *  (Cloudflare Family). Blocking these screens is the only way to
         *  prevent the DoH bypass without root — the browser-internal DoH
         *  resolver uses direct HTTPS to 1.1.1.1 / 8.8.8.8 and ignores the
         *  system Private DNS setting entirely.
         *
         *  We match on className substrings because Android surfaces
         *  chrome://settings as "org.chromium.chrome.browser.settings.Settings"
         *  and Firefox's about:preferences as "org.mozilla.gecko.preferences".
         *  Generic class-name fragments cover both + Kiwi + Brave + Edge. */
        private val BROWSER_SETTINGS_CLASS_HINTS = listOf(
            "settings.settings",          // chromium Settings activity
            "settings.activity",          // generic chromium settings
            "preferences",                // mozilla.gecko.preferences / firefox
            "aboutconfig",                // firefox about:config
            "flagsactivity",              // chrome://flags (can disable SafeSearch)
            "advancedsettings",           // chrome advanced privacy/security
            "privacysettings",
            "securitysettings"
        )

        /** IP literals that, if seen in a browser URL bar, mean the user is
         *  trying to reach a DNS-over-HTTPS / DNS-over-TLS resolver directly
         *  to bypass the Cloudflare Family lock. Matching is on the URL bar
         *  text only (not arbitrary on-screen text) so a search result listing
         *  "8.8.8.8" doesn't false-trigger. */
        private val DOH_IP_LITERALS = setOf(
            "1.1.1.1", "1.0.0.1",        // Cloudflare (non-family)
            "8.8.8.8", "8.8.4.4",        // Google
            "9.9.9.9", "149.112.112.112",// Quad9
            "208.67.222.222", "208.67.220.220", // OpenDNS
            "94.140.14.14", "94.140.15.15",     // AdGuard
            "76.76.19.19", "76.223.122.150",   // Control D
            "0.0.0.0"                    // sometimes used as a placeholder for "off"
        )
    }

    // Throttle the window-scan (battery friendliness).
    private var lastScanAt = 0L
    private var lastBlockAt = 0L

    // Cached "alarm ringing or due soon" flag — refreshed every 30s so we
    // don't read SharedPreferences on every accessibility event.
    private var alarmImminentCache = false
    private var alarmImminentCheckedAt = 0L

    // --- Minecraft credit economy ---
    private val credits by lazy { CreditManager(this) }
    private var minecraftForeground = false
    private val creditTicker = object : Runnable {
        override fun run() {
            if (!minecraftForeground) return
            if (credits.hasCredit()) {
                credits.spendSeconds(1)
                mainHandler.postDelayed(this, 1000L)
            } else {
                // Out of credit → kick out and show a notice overlay.
                showCreditOverlay()
                goHome()
            }
        }
    }

    // The BlockerX-style "block screen": a TYPE_ACCESSIBILITY_OVERLAY that covers
    // a danger screen and consumes touches so "Remove access" can never be tapped.
    private var overlay: View? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    // Backstop: if we somehow never see a "safe" event to tear the overlay down,
    // remove it anyway so the phone can never get stuck behind a black screen.
    private val overlayBackstop = Runnable { removeBlockOverlay() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Merge the user's persisted blocklist into the in-memory fast-path set so
        // app-picker choices and captured installs survive reboot / service restart.
        try { Blocklist.pushLive(this) } catch (_: Exception) {}
        // Allow-list + scoped apps must NEVER be whole-app blocked. Both groups
        // get the same treatment (remove from the live set AND persisted store),
        // so auto-capture from a fresh install can never re-trap them.
        for (pkg in WHITELISTED_PACKAGES + SCOPED_APPS.keys) {
            blockedPackages.remove(pkg)
            try { Blocklist.remove(this, pkg) } catch (_: Exception) {}
        }
        // BROWSER WHITELIST ENFORCEMENT: find every installed app that can open
        // http(s) links and hard-block any that isn't on the whitelist. This runs
        // once per service connect (cheap) and catches fresh browser installs on
        // the next reconnect.
        blockRogueBrowsers()
        Log.i(TAG, "Service connected; ${blockedPackages.size} apps, " +
                "${userDomains.size} sites, ${userKeywords.size} keywords (user).")
    }

    /** Scan installed apps for http(s) handlers that aren't on the browser
     *  whitelist and add them to the live block set. Idempotent and cheap —
     *  PackageManager query is the only cost. */
    private fun blockRogueBrowsers() {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(android.net.Uri.parse("https://"))
            val pm = packageManager
            val flags = android.content.pm.PackageManager.GET_RESOLVED_FILTER
            val safe = ALLOWED_BROWSERS + SCOPED_APPS.keys + WHITELISTED_PACKAGES +
                       blockedPackages + setOf(packageName)
            pm.queryIntentActivities(intent, flags).forEach { ri ->
                val pkg = ri.activityInfo.packageName
                if (pkg !in safe) {
                    blockedPackages.add(pkg)
                    rogueBrowsers.add(pkg)
                }
            }
            if (rogueBrowsers.isNotEmpty()) {
                Log.i(TAG, "Rogue browsers blocked: $rogueBrowsers")
            }
        } catch (_: Exception) {}
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        // Only process window events — skip notifications and announcements to
        // prevent notification text from triggering false domain/keyword blocks.
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {}
            else -> return
        }
        val evtPkg = event.packageName?.toString() ?: return
        val evtPkgLc = evtPkg.lowercase()
        val evtCls = event.className?.toString()?.lowercase() ?: ""

        if (VERBOSE) Log.d(TAG, "evt type=${event.eventType} pkg=$evtPkg cls=$evtCls")

        // NEVER act on our own app — its status screen literally lists
        // "Accessibility"/"FocusGuard", which would self-trigger every block.
        if (evtPkgLc == packageName.lowercase()) return

        // POWER-OFF BLOCK (alarm anti-dodge): when an alarm is ringing or due
        // within 30 min, intercept the SystemUI power-off / restart dialog.
        // This is the ONLY SystemUI surface we ever act on — it stops the user
        // from shutting the phone down to dodge a pending or active alarm.
        // (The long-press power menu is handled by SystemUI before any app sees
        // the key event, so dispatchKeyEvent alone can't catch it — only the
        // accessibility service can dismiss the resulting dialog.)
        if (evtPkg in SYSTEM_UI_PACKAGES && alarmActiveOrImminent()) {
            if (isPowerOffDialog(evtCls) || hasPowerOffText()) {
                Log.d(TAG, "Power-off dialog blocked (alarm active/imminent) cls=$evtCls")
                performGlobalAction(GLOBAL_ACTION_BACK)
                return
            }
        }

        // NEVER act on the launcher / system UI. Editing the dock, the app drawer,
        // recents, and the notification shade all live here — blocking them traps
        // the user. (Fix for "can't add app to dock".)
        if (evtPkg in SYSTEM_UI_PACKAGES) return

        // 0. CHEAP self-defense check on EVERY event, BEFORE any throttle. The
        //    accessibility-revoke screen (Safety Center / permissioncontroller on
        //    CPH2363) must never be missed because a throttle skipped its event.
        //    Match on the event package OR class name as a substring — robust to
        //    OEM package naming (coloros / oppo / oplus / google permissioncontroller).
        if (PURE_DANGER_PACKAGE_HINTS.any { it in evtPkgLc } ||
            DANGER_CLASS_HINTS.any { it in evtCls }) {
            Log.d(TAG, "Self-defense block [fast] pkg=$evtPkg cls=$evtCls")
            blockNow()
            return
        }

        // 0a. BROWSER SETTINGS BLOCK. Chromium / Firefox / Kiwi / Brave / Edge
        //     all expose DNS-over-HTTPS toggles inside their own settings screens.
        //     If the user opens ANY browser's settings page, bounce them — they
        //     can't reach the DoH toggle that would tunnel around Cloudflare
        //     Family. This is the strongest non-root defense against browser
        //     DoH bypass. (Settings activity class names contain "settings" /
        //     "preferences" fragments; matched only when fired by a browser.)
        if (evtPkg in ALLOWED_BROWSERS &&
            BROWSER_SETTINGS_CLASS_HINTS.any { it in evtCls }) {
            Log.d(TAG, "Browser settings block (DoH bypass prevention): pkg=$evtPkg cls=$evtCls")
            blockNow()
            return
        }

        // 0b. MINECRAFT credit gate. Minecraft is allowed ONLY while credits last.
        //     Entering with no credit bounces immediately; entering with credit
        //     starts a 1-second-per-second spend ticker (handled by creditTicker).
        if (evtPkg == CreditManager.MINECRAFT_PKG) {
            if (credits.hasCredit()) {
                if (!minecraftForeground) {
                    minecraftForeground = true
                    mainHandler.removeCallbacks(creditTicker)
                    mainHandler.post(creditTicker)
                }
            } else {
                minecraftForeground = false
                mainHandler.removeCallbacks(creditTicker)
                showCreditOverlay()
                goHome()
            }
            return
        } else if (minecraftForeground) {
            // Left Minecraft → stop spending.
            minecraftForeground = false
            mainHandler.removeCallbacks(creditTicker)
        }

        // 1. Hard app block via the EVENT package (fast path for launching apps).
        if (evtPkg in blockedPackages) {
            Log.d(TAG, "App block: $evtPkg")
            goHome()
            return
        }

        // 1b. SCOPED (partial) block. The app itself is allowed (e.g. a video
        //     player the user needs), but its in-app browser / online-downloader
        //     screens are bounced. We match on the activity CLASS name, because
        //     these surfaces ship as distinct activities (e.g. X Player's
        //     "PlayerActivity$WebDelegate"). The event className carries the
        //     activity (incl. inner-class names like "$WebDelegate") on
        //     TYPE_WINDOW_STATE_CHANGED, which is exactly when a blocked screen
        //     opens — so we bounce the instant it appears.
        SCOPED_APPS[evtPkg]?.let { blockedScreens ->
            if (blockedScreens.any { it in evtCls }) {
                Log.d(TAG, "Scoped block (browser/downloader): pkg=$evtPkg cls=$evtCls")
                goHome()
                return
            }
        }

        // Throttle the heavier window scan.
        val now = SystemClock.uptimeMillis()
        if (now - lastScanAt < 150L) return
        lastScanAt = now

        // 2. Deeper settings defense via the live window list (event.packageName is
        //    often stale on ColorOS — it may point at a background app while a
        //    danger screen is actually foreground).
        var danger = false
        var reason = ""

        val sb = StringBuilder()
        try {
            for (w in windows) {
                val root = w.root ?: continue
                val wpkg = (root.packageName?.toString() ?: "").lowercase()
                if (wpkg == packageName.lowercase()) continue   // skip our own windows
                if (!danger && PURE_DANGER_PACKAGE_HINTS.any { it in wpkg }) {
                    danger = true; reason = "pure-pkg:$wpkg"
                }
                collectText(root, sb)
            }
        } catch (_: Exception) {}
        // Protected access — AccessibilityNodeInfo may be recycled by the system.
        try { event.source?.let { collectText(it, sb) } } catch (_: Exception) {}

        val text = sb.toString().lowercase()

        // (c) text fallback: ONLY on a genuine app-management screen (app info /
        //     uninstall / force-stop / permission revoke) where our app is named
        //     with a STRONG weakening verb. Gating on the class name prevents
        //     false blocks on ordinary screens — e.g. per-app NOTIFICATION
        //     settings, which list "FocusGuard" + "turn off" but are harmless.
        val onAppScopedScreen = APP_SCOPED_CLASS_HINTS.any { it in evtCls }
        if (!danger && onAppScopedScreen && isSelfDefenseScreen(text)) {
            danger = true; reason = "text"
        }

        if (danger) {
            Log.d(TAG, "Self-defense block [$reason] pkg=$evtPkg cls=$evtCls")
            blockNow()
            return
        }

        // Foreground is no longer a danger screen → tear the block screen down.
        removeBlockOverlay()

        // 3. WEB BLOCK — PRECISE. Only inside a real browser (or a scoped app's
        //    browser surface), and only by reading the ADDRESS BAR. A blocked
        //    domain merely shown as a link in chat or in a list of search results
        //    does NOTHING — you're bounced only when you actually navigate to the
        //    site. This is the anti-false-positive fix.
        if (evtPkg in SYSTEM_UI_PACKAGES) return
        // A scoped app (e.g. X Player) is treated like a browser for URL-bar
        // scanning: if its in-app browser navigates to a blocked domain / DoH
        // IP, bounce. This catches the downloader reaching blocked sites even
        // when the browser screen's class name didn't match SCOPED_APPS.
        val isBrowserLike = evtPkg in ALLOWED_BROWSERS || evtPkg in SCOPED_APPS
        if (!isBrowserLike) return

        val urlBar = readUrlBar()

        // 3a. SCOPED-APP BROWSE BLOCK — primary: URL bar found.
        //     The user explicitly allowed this app ONLY for its offline features
        //     (local playback / files). Any online navigation — to ANY site —
        //     is bounced.
        if (evtPkg in SCOPED_APPS) {
            if (urlBar != null && looksLikeOnlineUrl(urlBar)) {
                Log.d(TAG, "Scoped browse block (URL bar online): $urlBar")
                goHome(); return
            }
            // 3b. SCOPED-APP WEBVIEW FALLBACK. Obfuscated browsers (e.g. XPlayer)
            //     run inside a WebView embedded in the main Activity — the URL bar
            //     uses non-standard resource IDs so readUrlBar() returns null.
            //     Detection: a WebView node is present in the tree + the collected
            //     screen text contains a blocked domain, online URL, or adult keyword.
            //     This is safe because local video playback does NOT use WebView.
            if (urlBar == null && hasWebViewNode() && scopedTextHits(text)) {
                Log.d(TAG, "Scoped WebView block (text scan)")
                goHome(); return
            }
            return // scoped app: no browser activity detected → allow
        }

        // 4. ALLOWED-BROWSER DOMAIN / KEYWORD / DOH BLOCK.
        //    Only whitelisted browsers reach here; urlBar is their standard bar.
        val bar = urlBar ?: return
        if (blockedDomains.any { it in bar } || userDomains.any { it in bar }) {
            Log.d(TAG, "Domain block: $bar")
            goHome()
        } else if (userKeywords.any { it in bar }) {
            Log.d(TAG, "Keyword-in-URL block: $bar")
            goHome()
        } else if (isDoHBypassAttempt(bar)) {
            // User typed a DoH resolver IP (1.1.1.1 / 8.8.8.8 / 9.9.9.9 / ...)
            // directly in the address bar — they're trying to reach a
            // DNS-over-HTTPS endpoint to tunnel around the Cloudflare Family
            // lock. Hard-bounce.
            Log.d(TAG, "DoH bypass attempt (IP literal in URL bar): $urlBar")
            goHome()
        }
    }

    /** Returns true if the URL-bar text contains a known DNS-over-HTTPS /
     *  DNS-over-TLS resolver IP literal. Matched on the URL bar ONLY so a
     *  search result listing "8.8.8.8" never false-triggers. */
    private fun isDoHBypassAttempt(urlBar: String): Boolean {
        // Use word-boundary matching so "18.8.8.8" or "8.8.8.81" don't false-fire.
        // The IP regex matches IPv4 literals; we then check membership in the set.
        val ipRegex = Regex("(?<![\\d.])(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})(?![\\d.])")
        return ipRegex.findAll(urlBar).any { it.groupValues[1] in DOH_IP_LITERALS }
    }

    /** True if a URL-bar string is an ONLINE address (not a local file path).
     *  Used to bounce ALL browsing inside scoped apps — the app is allowed only
     *  for offline use, so any http(s) URL or dotted domain means the user has
     *  opened the in-app browser / downloader. Local paths like
     *  "/storage/emulated/0/Movies/lecture.mp4" deliberately do NOT match. */
    private fun looksLikeOnlineUrl(urlBar: String): Boolean {
        if (urlBar.startsWith("http://") || urlBar.startsWith("https://")) return true
        // a.b domain (e.g. "example.com", "sub.example.co.in"). Requires a dot
        // AND a TLD-ish suffix; rejects bare filenames and /storage/ paths.
        val domain = Regex("(?<=[\\s./])([a-z0-9-]+\\.)+[a-z]{2,}(?=/|\\s|$|:)")
        return domain.containsMatchIn(urlBar)
    }

    /** Read the current browser address-bar text, or null if not found. We walk
     *  the active windows looking for an editable/text node whose resource-id
     *  ends with a known URL-bar id. This guarantees we react to NAVIGATION, not
     *  to links sitting on a page or in chat. */
    private fun readUrlBar(): String? {
        try {
            for (w in windows) {
                val root = w.root ?: continue
                val hit = findUrlBar(root)
                if (hit != null) return hit.lowercase()
            }
        } catch (_: Exception) {}
        return null
    }

    private fun findUrlBar(node: AccessibilityNodeInfo): String? {
        val id = node.viewIdResourceName?.lowercase()
        if (id != null && URL_BAR_ID_HINTS.any { id.endsWith(it) || id.endsWith("id/$it") }) {
            val t = node.text?.toString()
            if (!t.isNullOrBlank()) return t
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                val r = findUrlBar(child)
                if (r != null) return r
            }
        }
        return null
    }

    /** Detect a WebView node anywhere in the active windows. Obfuscated
     *  browsers (e.g. XPlayer) run inside an android.webkit.WebView embedded
     *  in the main Activity — no separate Activity class fires. A WebView
     *  node is strong evidence of active in-app browsing. */
    private fun hasWebViewNode(): Boolean {
        try {
            for (w in windows) {
                val root = w.root ?: continue
                if (findWebView(root)) return true
            }
        } catch (_: Exception) {}
        return false
    }

    private fun findWebView(node: AccessibilityNodeInfo): Boolean {
        if (node.className?.toString()?.contains("WebView") == true) return true
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { if (findWebView(it)) return true }
        }
        return false
    }

    /** Check whether the on-screen text from a scoped app contains any
     *  blocked domain, online URL pattern, or adult keyword. Used as a
     *  fallback when the URL bar can't be read (obfuscated resource IDs)
     *  but a WebView node confirms browsing is active. */
    private fun scopedTextHits(text: String): Boolean {
        if (blockedDomains.any { it in text }) return true
        if (userDomains.any { it in text }) return true
        if (userKeywords.any { it in text }) return true
        if (keywordHit(text)) return true
        // Catch any dotted domain (e.g. "instagram.com" visible in page text)
        if (looksLikeOnlineUrl(text)) return true
        return false
    }

    /**
     * True when the current Settings screen is one that could be used to
     * weaken FocusGuard: device-admin list, accessibility config, our app's
     * info/permissions page, or special-access toggles.
     */
    private fun isSelfDefenseScreen(text: String): Boolean {
        val mentionsUs = "focusguard" in text || "focus guard" in text ||
                         "com.focus.guard" in text
        if (!mentionsUs) return false

        // STRONG verbs only — actions that genuinely weaken FocusGuard. Loose
        // words like "disable" / "turn off" / "permissions" / "accessibility" were
        // removed because they appear on harmless screens (e.g. per-app
        // NOTIFICATION settings) and caused false blocks.
        val dangerVerb =
            "remove access" in text || "uninstall" in text || "force stop" in text ||
            "clear data" in text || "clear cache" in text ||
            "full device access" in text ||
            "view your screen and perform actions" in text ||
            "deactivate" in text || "device admin" in text ||
            "device administrator" in text || "stop service" in text

        return dangerVerb
    }

    /** Smart adult-keyword detection that won't trip on JEE study material.
     *  Explicit terms bounce on a single hit; ambiguous textbook-adjacent words
     *  (sex, nude, …) need TWO distinct hits, so "sexual reproduction" is safe. */
    private fun keywordHit(text: String): Boolean {
        if (EXPLICIT_KEYWORDS.any { it in text }) return true
        if (userKeywords.any { it in text }) return true   // user chose these → single hit
        val ambiguous = AMBIGUOUS_KEYWORDS.count { it in text }
        return ambiguous >= 2
    }

    /** Block a self-defense screen the BlockerX way: NEVER force-kill the screen
     *  (that is what crashed PermissionController — see lessons). Instead:
     *   1. Cover it with a touch-consuming accessibility overlay so "Remove access"
     *      can't be tapped. The overlay does NOT background PermissionController,
     *      so ColorOS won't kill it → no "keeps stopping" crash.
     *   2. Issue a SINGLE GLOBAL_ACTION_BACK (no HOME intent, no startActivity) on a
     *      ~1.2s cooldown. BACK gently pops the screen; repeated BACK + cooldown
     *      walks the user out of the whole flow without ever force-killing a
     *      process. The overlay tears itself down (in onAccessibilityEvent) the
     *      moment the foreground stops being a danger screen, with a backstop timer
     *      so it can never get stuck. */
    private fun blockNow() {
        showBlockOverlay()
        val now = SystemClock.uptimeMillis()
        if (now - lastBlockAt < 1200L) return   // already bouncing; don't spam
        lastBlockAt = now
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /** Add the full-screen block overlay if not already shown. Idempotent: the
     *  `overlay != null` guard prevents the add-spam that previously crashed. */
    private fun showBlockOverlay() {
        // Refresh the backstop on every danger event so the overlay only auto-
        // removes after the user has genuinely been away from the screen a while.
        mainHandler.removeCallbacks(overlayBackstop)
        mainHandler.postDelayed(overlayBackstop, 5000L)

        if (overlay != null) return
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val root = FrameLayout(this).apply {
            // Near-opaque so the dangerous buttons aren't even visible to aim at.
            setBackgroundColor(0xF21A1A1A.toInt())
            isClickable = true   // consume taps so nothing reaches the screen below
            isFocusable = false  // don't steal key focus → GLOBAL_ACTION_BACK still works
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }
        column.addView(TextView(this).apply {
            text = "🛡  Blocked by FocusGuard"
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
        })
        column.addView(TextView(this).apply {
            text = "This screen can weaken FocusGuard, so it's off-limits while a lockdown is active."
            setTextColor(0xFFBBBBBB.toInt())
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(28))
        })
        column.addView(Button(this).apply {
            text = "Return"
            setOnClickListener {
                removeBlockOverlay()
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        })
        root.addView(column, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ).apply { gravity = Gravity.CENTER })

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // NOT_FOCUSABLE: consume touches but leave key events to the framework
            // so our GLOBAL_ACTION_BACK keeps popping the underlying screen.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).addView(root, lp)
            overlay = root
        } catch (_: Exception) {
            overlay = null
        }
    }

    /** Brief toast-style notice when Minecraft is blocked for lack of credit. */
    private fun showCreditOverlay() {
        try {
            android.widget.Toast.makeText(
                this,
                "⛏ Minecraft locked — out of credits. Study tonight (10pm–12am) to earn more.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        } catch (_: Exception) {}
    }

    private fun removeBlockOverlay() {
        mainHandler.removeCallbacks(overlayBackstop)
        val v = overlay ?: return
        overlay = null
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v)
        } catch (_: Exception) {}
    }

    /** Force the device to the home screen. Uses an explicit HOME intent (which
     *  ColorOS honors even when GLOBAL_ACTION_HOME is suppressed for a
     *  foreground app) plus the global action as a belt-and-braces fallback. */
    private fun goHome() {
        try {
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(home)
        } catch (_: Exception) {}
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /** Cached check: is an alarm ringing or due within 30 min? The cache
     *  refreshes every 30s so we avoid SharedPreferences reads on every event.
     *  RINGING is always checked live (no cache) so we react instantly. */
    private fun alarmActiveOrImminent(): Boolean {
        if (AlarmRingActivity.RINGING) return true
        val now = SystemClock.uptimeMillis()
        if (now - alarmImminentCheckedAt < 30_000L) return alarmImminentCache
        alarmImminentCheckedAt = now
        alarmImminentCache = try {
            AlarmScheduler.alarmActiveOrImminent(this)
        } catch (_: Exception) { false }
        return alarmImminentCache
    }

    /** Detect the SystemUI global-actions (power-off / restart) dialog by
     *  class-name fragments. Covers AOSP + common OEMs (ColorOS, Samsung, etc.). */
    private fun isPowerOffDialog(cls: String): Boolean {
        val hints = listOf(
            "globalactions", "globalaction",     // AOSP power dialog
            "shutdown", "poweroff", "powerdialog", "powermenu"  // OEM variants
        )
        return hints.any { it in cls }
    }

    /** Fallback: scan SystemUI windows for power-off / restart confirmation
     *  text. Only runs when an alarm is active/imminent, so the cost is
     *  negligible and false positives can't happen outside that window. */
    private fun hasPowerOffText(): Boolean {
        val powerWords = listOf(
            "power off", "shut down", "shutdown", "restart", "reboot"
        )
        try {
            for (w in windows) {
                val root = w.root ?: continue
                val wpkg = (root.packageName?.toString() ?: "").lowercase()
                if (wpkg !in SYSTEM_UI_PACKAGES) continue
                val sb = StringBuilder()
                collectText(root, sb)
                val t = sb.toString().lowercase()
                if (powerWords.any { it in t }) return true
            }
        } catch (_: Exception) {}
        return false
    }

    private fun collectText(node: AccessibilityNodeInfo, out: StringBuilder = StringBuilder()): String {
        node.text?.let { out.append(it).append(' ') }
        node.contentDescription?.let { out.append(it).append(' ') }
        for (i in 0 until node.childCount) node.getChild(i)?.let { collectText(it, out) }
        return out.toString()
    }

    override fun onInterrupt() {
        removeBlockOverlay()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        removeBlockOverlay()
        return super.onUnbind(intent)
    }
}
