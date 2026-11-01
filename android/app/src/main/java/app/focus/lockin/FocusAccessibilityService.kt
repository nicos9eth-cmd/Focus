package app.focus.lockin

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class FocusAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in ALWAYS_ALLOWED) return
        val store = SessionStore(this)
        if (!store.isSessionActive()) return
        if (pkg !in store.blockedPackages() && !store.isSealed(pkg)) return
        val until = store.noteLaunch(pkg)
        val intent = Intent(this, ShameActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(ShameActivity.EXTRA_PKG, pkg)
            putExtra(ShameActivity.EXTRA_UNTIL, until)
        }
        startActivity(intent)
    }

    override fun onInterrupt() = Unit
}
