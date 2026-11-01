package app.focus.lockin

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private val store by lazy { SessionStore(this) }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private var minutes = 50

    private val tick = object : Runnable {
        override fun run() {
            renderStatus()
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
            setBackgroundColor(0xFF0C0C0B.toInt())
        }
        root.addView(TextView(this).apply {
            text = "FOCUS"
            textSize = 14f
            setTextColor(0xFFE4F25A.toInt())
            letterSpacing = 0.22f
        })
        status = TextView(this).apply {
            textSize = 28f
            setTextColor(0xFFF3F0E8.toInt())
            setPadding(0, 24, 0, 24)
        }
        root.addView(status)

        val row = LinearLayout(this)
        for (m in intArrayOf(25, 50, 90)) {
            row.addView(Button(this).apply {
                text = "${m} min"
                setOnClickListener { minutes = m }
            })
        }
        root.addView(row)

        val blocked = store.blockedPackages().toMutableSet()
        for (app in Catalog.apps) {
            root.addView(Button(this).apply {
                text = if (app.pkg in blocked) "Sceller ${app.label}" else "Laisser ${app.label}"
                setOnClickListener {
                    if (store.isSessionActive()) return@setOnClickListener
                    if (app.pkg in blocked) blocked.remove(app.pkg) else blocked.add(app.pkg)
                    store.setBlocked(blocked)
                    text = if (app.pkg in blocked) "Sceller ${app.label}" else "Laisser ${app.label}"
                }
            })
        }

        root.addView(Button(this).apply {
            text = "Lancer la session"
            setOnClickListener {
                if (!isAccessibilityOn()) {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    return@setOnClickListener
                }
                store.start(minutes)
                renderStatus()
            }
        })

        root.addView(Button(this).apply {
            text = "Ignorer l’optimisation batterie"
            setOnClickListener {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        })

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    private fun renderStatus() {
        if (!::status.isInitialized) return
        if (!isAccessibilityOn()) {
            status.text = "Active le service d’accessibilité Focus, puis lance."
            return
        }
        if (!store.isSessionActive()) {
            status.text = "Prêt. ${store.slips()} slips sur la dernière session encore en mémoire tant qu’elle n’est pas relancée."
            return
        }
        val left = store.endsAt() - System.currentTimeMillis()
        val s = (left / 1000L).coerceAtLeast(0L)
        status.text = "Session %02d:%02d · %d slips".format(s / 60, s % 60, store.slips())
        status.setTextColor(ContextCompat.getColor(this, android.R.color.white))
    }

    private fun isAccessibilityOn(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.contains("$packageName/${FocusAccessibilityService::class.java.name}")
            || enabled.contains("$packageName/.FocusAccessibilityService")
    }
}
