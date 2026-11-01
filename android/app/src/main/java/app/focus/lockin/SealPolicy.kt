package app.focus.lockin

/**
 * Pure punishment and schedule rules. No Android types, so JVM tests can cover them.
 */
object SealPolicy {
    const val MAX_MULTIPLIER = 8

    fun multiplier(strikes: Int): Int {
        val n = strikes.coerceAtLeast(1)
        var m = 1
        var i = 1
        while (i < n && m < MAX_MULTIPLIER) {
            m *= 2
            i++
        }
        return m
    }

    fun sealMillis(strikes: Int, baseMillis: Long): Long =
        baseMillis.coerceAtLeast(60_000L) * multiplier(strikes)

    /**
     * A new strike only starts when nothing is currently sealed.
     * Returns the deadline to store, and whether this launch counted as a new slip.
     */
    fun onLaunch(now: Long, currentUntil: Long, strikes: Int, baseMillis: Long): Launch {
        if (now < currentUntil) return Launch(currentUntil, strikes, counted = false)
        val next = strikes + 1
        return Launch(now + sealMillis(next, baseMillis), next, counted = true)
    }

    data class Launch(val until: Long, val strikes: Int, val counted: Boolean)

    data class Window(val days: Set<Int>, val startMin: Int, val endMin: Int)

    /** day: 1 = Monday … 7 = Sunday. minuteOfDay: 0..1439. Overnight windows are allowed. */
    fun inWindow(day: Int, minuteOfDay: Int, windows: List<Window>): Boolean {
        for (w in windows) {
            if (day !in w.days) continue
            val start = w.startMin.coerceIn(0, 1439)
            val end = w.endMin.coerceIn(0, 1439)
            val hit = if (start == end) {
                false
            } else if (start < end) {
                minuteOfDay in start until end
            } else {
                minuteOfDay >= start || minuteOfDay < end
            }
            if (hit) return true
        }
        return false
    }
}

object ToxicHint {
    val packages = setOf(
        "com.instagram.android",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.google.android.youtube",
        "com.twitter.android",
        "com.x.android",
        "com.reddit.frontpage",
        "com.snapchat.android",
        "com.facebook.katana",
        "com.facebook.orca",
        "com.zhiliaoapp.musically.go",
        "com.netflix.mediaclient",
        "tv.twitch.android.app",
        "com.discord",
        "com.pinterest",
        "com.bereal.ft",
        "com.google.android.apps.youtube.music",
    )

    private val words = listOf(
        "tiktok", "instagram", "youtube", "reddit", "twitter", "facebook",
        "snapchat", "netflix", "twitch", "discord", "pinterest", "bereal", "reel",
    )

    fun looksToxic(pkg: String, label: String): Boolean {
        if (pkg in packages) return true
        val hay = "$pkg $label".lowercase()
        return words.any { it in hay }
    }
}
