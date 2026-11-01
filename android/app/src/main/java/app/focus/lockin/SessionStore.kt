package app.focus.lockin

import android.content.Context

data class BlockedApp(val id: String, val label: String, val pkg: String)

object Catalog {
    val apps = listOf(
        BlockedApp("instagram", "Instagram", "com.instagram.android"),
        BlockedApp("tiktok", "TikTok", "com.zhiliaoapp.musically"),
        BlockedApp("youtube", "YouTube", "com.google.android.youtube"),
        BlockedApp("x", "X", "com.twitter.android"),
        BlockedApp("reddit", "Reddit", "com.reddit.frontpage"),
        BlockedApp("snap", "Snapchat", "com.snapchat.android"),
    )

    fun labelFor(pkg: String): String =
        apps.firstOrNull { it.pkg == pkg }?.label ?: pkg
}

/**
 * Always allowed so a session can never trap calls, texts, or the way out.
 */
val ALWAYS_ALLOWED = setOf(
    "app.focus.lockin",
    "com.android.systemui",
    "com.android.settings",
    "com.android.phone",
    "com.google.android.dialer",
    "com.samsung.android.dialer",
    "com.google.android.apps.messaging",
    "com.samsung.android.messaging",
    "com.android.mms",
    "com.google.android.apps.nexuslauncher",
    "com.android.launcher3",
    "com.sec.android.app.launcher",
)

class SessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("focus", Context.MODE_PRIVATE)

    fun blockedPackages(): Set<String> {
        val raw = prefs.getStringSet("blocked", null)
        return raw ?: Catalog.apps.map { it.pkg }.toSet() - "com.snapchat.android"
    }

    fun setBlocked(pkgs: Set<String>) {
        prefs.edit().putStringSet("blocked", pkgs).apply()
    }

    fun start(minutes: Int) {
        val now = System.currentTimeMillis()
        prefs.edit()
            .putLong("endsAt", now + minutes * 60_000L)
            .putInt("slips", 0)
            .apply()
        clearSeals()
    }

    fun end() {
        prefs.edit().remove("endsAt").apply()
        clearSeals()
    }

    fun endsAt(): Long = prefs.getLong("endsAt", 0L)

    fun isSessionActive(): Boolean = System.currentTimeMillis() < endsAt()

    fun slips(): Int = prefs.getInt("slips", 0)

    fun sealUntil(pkg: String): Long = prefs.getLong("until_$pkg", 0L)

    fun strikes(pkg: String): Int = prefs.getInt("strike_$pkg", 0)

    fun isSealed(pkg: String): Boolean = System.currentTimeMillis() < sealUntil(pkg)

    /**
     * First open of a blocked app starts a seal. Re-opening while sealed does not
     * stack strikes — otherwise a single fidget would explode the timer.
     * Returns the seal deadline.
     */
    fun noteLaunch(pkg: String): Long {
        if (isSealed(pkg)) return sealUntil(pkg)
        val strikes = strikes(pkg) + 1
        val mult = (1 shl (strikes - 1).coerceAtMost(3)) // 1,2,4,8 → 5,10,20,40 min
        val until = System.currentTimeMillis() + BASE_SEAL_MS * mult
        prefs.edit()
            .putInt("strike_$pkg", strikes)
            .putLong("until_$pkg", until)
            .putInt("slips", slips() + 1)
            .apply()
        return until
    }

    private fun clearSeals() {
        val ed = prefs.edit()
        for (app in Catalog.apps) {
            ed.remove("until_${app.pkg}")
            ed.remove("strike_${app.pkg}")
        }
        ed.apply()
    }

    companion object {
        const val BASE_SEAL_MS = 5 * 60_000L
    }
}
