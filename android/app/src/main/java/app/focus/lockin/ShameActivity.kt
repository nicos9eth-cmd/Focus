package app.focus.lockin

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class ShameActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var clock: TextView
    private var until: Long = 0L
    private var pkg: String = ""

    private val tick = object : Runnable {
        override fun run() {
            val left = until - System.currentTimeMillis()
            if (left <= 0L) {
                finish()
                return
            }
            clock.text = format(left)
            handler.postDelayed(this, 200L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pkg = intent.getStringExtra(EXTRA_PKG).orEmpty()
        until = intent.getLongExtra(EXTRA_UNTIL, 0L)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 120, 48, 48)
            setBackgroundColor(0xFF0C0C0B.toInt())
        }
        val kicker = TextView(this).apply {
            text = "SCELLÉ"
            textSize = 12f
            setTextColor(0xFFFF5A3C.toInt())
            letterSpacing = 0.28f
        }
        val title = TextView(this).apply {
            text = Catalog.labelFor(pkg)
            textSize = 40f
            setTextColor(0xFFF3F0E8.toInt())
            setPadding(0, 24, 0, 0)
        }
        val body = TextView(this).apply {
            text = "Tu l’as ouvert. Il reste fermé jusqu’au bout du compteur. Le reste du téléphone marche."
            textSize = 16f
            setTextColor(0xFF8D897F.toInt())
            setPadding(0, 28, 0, 0)
        }
        clock = TextView(this).apply {
            textSize = 64f
            setTextColor(0xFFF3F0E8.toInt())
            setPadding(0, 72, 0, 0)
        }
        root.addView(kicker)
        root.addView(title)
        root.addView(body)
        root.addView(clock)
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // No exit. Home still works; opening the app again lands here.
            }
        })
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pkg = intent.getStringExtra(EXTRA_PKG).orEmpty()
        until = intent.getLongExtra(EXTRA_UNTIL, until)
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    private fun format(ms: Long): String {
        val s = (ms / 1000L).coerceAtLeast(0L)
        return "%02d:%02d".format(s / 60L, s % 60L)
    }

    companion object {
        const val EXTRA_PKG = "pkg"
        const val EXTRA_UNTIL = "until"
    }
}
