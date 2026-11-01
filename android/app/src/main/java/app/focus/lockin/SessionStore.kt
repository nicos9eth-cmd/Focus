package app.focus.lockin

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

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

    fun labelFor(pkg: String, extra: List<BlockedApp> = emptyList()): String =
        (extra + apps).firstOrNull { it.pkg == pkg }?.label ?: pkg.substringAfterLast('.')
}

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

    fun knownApps(): List<BlockedApp> {
        val extra = mutableListOf<BlockedApp>()
        val raw = prefs.getString("extra", "[]") ?: "[]"
        val arr = JSONArray(raw)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            extra.add(BlockedApp(o.getString("pkg"), o.getString("label"), o.getString("pkg")))
        }
        return extra + Catalog.apps
    }

    fun addManual(label: String, pkg: String): Boolean {
        val clean = pkg.trim()
        if (!PACKAGE.matches(clean)) return false
        if (clean in ALWAYS_ALLOWED) return false
        val current = JSONArray(prefs.getString("extra", "[]"))
        for (i in 0 until current.length()) {
            if (current.getJSONObject(i).getString("pkg") == clean) return true
        }
        current.put(JSONObject().put("label", label.trim().ifEmpty { clean }).put("pkg", clean))
        prefs.edit().putString("extra", current.toString()).apply()
        val blocked = blockedPackages().toMutableSet()
        blocked.add(clean)
        setBlocked(blocked)
        return true
    }

    fun blockedPackages(): Set<String> {
        val raw = prefs.getStringSet("blocked", null) ?: return defaultBlocked()
        return raw
    }

    private fun defaultBlocked(): Set<String> =
        Catalog.apps.map { it.pkg }.toSet() - "com.snapchat.android"

    fun setBlocked(pkgs: Set<String>) {
        prefs.edit().putStringSet("blocked", pkgs).apply()
    }

    fun baseSealMillis(): Long = prefs.getLong("baseSeal", SealPolicyBase)

    fun setBaseSealMinutes(minutes: Int) {
        prefs.edit().putLong("baseSeal", minutes.coerceIn(1, 180) * 60_000L).apply()
    }

    /** Refuses to wipe an active manual session. Returns false if one is already running. */
    fun start(minutes: Int): Boolean {
        if (isManualActive()) return false
        val now = System.currentTimeMillis()
        prefs.edit()
            .putLong("endsAt", now + minutes.coerceIn(1, 24 * 60) * 60_000L)
            .putInt("slips", 0)
            .apply()
        clearSeals()
        return true
    }

    fun end() {
        prefs.edit().remove("endsAt").apply()
        clearSeals()
    }

    fun endsAt(): Long = prefs.getLong("endsAt", 0L)

    fun isManualActive(now: Long = System.currentTimeMillis()): Boolean = now < endsAt()

    fun windows(): List<SealPolicy.Window> {
        val raw = prefs.getString("windows", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val out = mutableListOf<SealPolicy.Window>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val days = mutableSetOf<Int>()
            val d = o.getJSONArray("days")
            for (j in 0 until d.length()) days.add(d.getInt(j))
            out.add(SealPolicy.Window(days, o.getInt("start"), o.getInt("end")))
        }
        return out
    }

    fun setWindows(windows: List<SealPolicy.Window>) {
        val arr = JSONArray()
        for (w in windows) {
            val days = JSONArray()
            w.days.sorted().forEach { days.put(it) }
            arr.put(JSONObject().put("days", days).put("start", w.startMin).put("end", w.endMin))
        }
        prefs.edit().putString("windows", arr.toString()).apply()
    }

    fun isScheduled(now: Calendar = Calendar.getInstance()): Boolean {
        val day = ((now.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return SealPolicy.inWindow(day, minute, windows())
    }

    fun protectionOn(now: Long = System.currentTimeMillis()): Boolean =
        isManualActive(now) || isScheduled()

    fun slips(): Int = prefs.getInt("slips", 0)

    fun sealUntil(pkg: String): Long = prefs.getLong("until_$pkg", 0L)

    fun strikes(pkg: String): Int = prefs.getInt("strike_$pkg", 0)

    fun isSealed(pkg: String, now: Long = System.currentTimeMillis()): Boolean = now < sealUntil(pkg)

    fun noteLaunch(pkg: String, now: Long = System.currentTimeMillis()): Long {
        val launch = SealPolicy.onLaunch(now, sealUntil(pkg), strikes(pkg), baseSealMillis())
        if (launch.counted) {
            prefs.edit()
                .putInt("strike_$pkg", launch.strikes)
                .putLong("until_$pkg", launch.until)
                .putInt("slips", slips() + 1)
                .apply()
        }
        return launch.until
    }

    private fun clearSeals() {
        val ed = prefs.edit()
        for (app in knownApps()) {
            ed.remove("until_${app.pkg}")
            ed.remove("strike_${app.pkg}")
        }
        ed.apply()
    }

    companion object {
        const val SealPolicyBase = 5 * 60_000L
        val PACKAGE = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
    }
}
