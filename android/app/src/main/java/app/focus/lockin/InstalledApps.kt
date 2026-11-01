package app.focus.lockin

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class Launchable(val label: String, val pkg: String, val suggested: Boolean)

fun listLaunchable(context: Context): List<Launchable> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val seen = LinkedHashMap<String, Launchable>()
    val activities = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
    for (info in activities) {
        val pkg = info.activityInfo.packageName
        if (pkg == context.packageName || pkg in ALWAYS_ALLOWED) continue
        val label = info.loadLabel(context.packageManager)?.toString()?.trim().orEmpty().ifEmpty { pkg }
        val suggested = ToxicHint.looksToxic(pkg, label)
        val prev = seen[pkg]
        if (prev == null || (suggested && !prev.suggested)) {
            seen[pkg] = Launchable(label, pkg, suggested)
        }
    }
    return seen.values.sortedWith(compareByDescending<Launchable> { it.suggested }.thenBy { it.label.lowercase() })
}
