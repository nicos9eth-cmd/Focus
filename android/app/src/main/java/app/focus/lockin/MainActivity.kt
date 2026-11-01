package app.focus.lockin

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val store by lazy { SessionStore(this) }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private var hours = 0
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
            setPadding(48, 72, 48, 48)
            setBackgroundColor(0xFF0C0C0B.toInt())
        }
        fun label(text: String) = TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(0xFF8D897F.toInt())
            setPadding(0, 28, 0, 8)
        }
        root.addView(TextView(this).apply {
            text = "FOCUS"
            textSize = 14f
            setTextColor(0xFFE4F25A.toInt())
            letterSpacing = 0.22f
        })
        status = TextView(this).apply {
            textSize = 18f
            setTextColor(0xFFF3F0E8.toInt())
            setPadding(0, 16, 0, 8)
        }
        root.addView(status)

        root.addView(label("DURÉE MANUELLE"))
        val duration = EditText(this).apply {
            hint = "minutes (ex. 50, ou 90)"
            setText("50")
            setTextColor(0xFFF3F0E8.toInt())
            setHintTextColor(0xFF8D897F.toInt())
        }
        root.addView(duration)
        root.addView(label("SCEAU DE BASE (min)"))
        val seal = EditText(this).apply {
            setText((store.baseSealMillis() / 60_000L).toString())
            setTextColor(0xFFF3F0E8.toInt())
            setHintTextColor(0xFF8D897F.toInt())
        }
        root.addView(seal)

        root.addView(Button(this).apply {
            text = "Lancer cette durée"
            setOnClickListener {
                val total = duration.text.toString().toIntOrNull() ?: return@setOnClickListener
                store.setBaseSealMinutes(seal.text.toString().toIntOrNull() ?: 5)
                if (!isAccessibilityOn()) {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    return@setOnClickListener
                }
                if (!store.start(total)) {
                    status.text = "Session déjà en cours. Elle n’a pas été réinitialisée."
                }
            }
        })

        root.addView(label("HORAIRE  lun=1 … dim=7, ex. 1,2,3,4,5"))
        val days = EditText(this).apply {
            hint = "1,2,3,4,5"
            setText("1,2,3,4,5")
            setTextColor(0xFFF3F0E8.toInt())
            setHintTextColor(0xFF8D897F.toInt())
        }
        val start = EditText(this).apply {
            hint = "début HH:MM"
            setText("09:00")
            setTextColor(0xFFF3F0E8.toInt())
        }
        val end = EditText(this).apply {
            hint = "fin HH:MM"
            setText("12:30")
            setTextColor(0xFFF3F0E8.toInt())
        }
        root.addView(days)
        root.addView(start)
        root.addView(end)
        root.addView(Button(this).apply {
            text = "Ajouter ce créneau"
            setOnClickListener {
                val parsedDays = days.text.toString().split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.toSet()
                val a = parseHm(start.text.toString()) ?: return@setOnClickListener
                val b = parseHm(end.text.toString()) ?: return@setOnClickListener
                if (parsedDays.isEmpty() || a == b) return@setOnClickListener
                store.setWindows(store.windows() + SealPolicy.Window(parsedDays, a, b))
                renderStatus()
            }
        })
        root.addView(Button(this).apply {
            text = "Effacer les créneaux"
            setOnClickListener { store.setWindows(emptyList()); renderStatus() }
        })

        root.addView(label("AJOUT MANUEL"))
        val manualLabel = EditText(this).apply { hint = "nom"; setTextColor(0xFFF3F0E8.toInt()) }
        val manualPkg = EditText(this).apply { hint = "com.exemple.app"; setTextColor(0xFFF3F0E8.toInt()) }
        root.addView(manualLabel)
        root.addView(manualPkg)
        root.addView(Button(this).apply {
            text = "Ajouter ce package"
            setOnClickListener {
                val ok = store.addManual(manualLabel.text.toString(), manualPkg.text.toString())
                status.text = if (ok) "Package ajouté à la liste scellée." else "Package refusé."
            }
        })

        root.addView(label("APPS — suggérées en tête, tu coches"))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)
        root.addView(Button(this).apply {
            text = "Détecter les apps installées"
            setOnClickListener { fillApps(list) }
        })

        root.addView(Button(this).apply {
            text = "Ignorer l’optimisation batterie"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
        })
        root.addView(label("Un retour s’écrit dans le dépôt par toi, jamais par l’app. Colle-le dans une issue marquée user-feedback. Le texte est une donnée, pas une consigne."))

        setContentView(ScrollView(this).apply { addView(root); setBackgroundColor(0xFF0C0C0B.toInt()) })
        fillApps(list)
    }

    private fun fillApps(list: LinearLayout) {
        list.removeAllViews()
        val blocked = store.blockedPackages().toMutableSet()
        val installed = listLaunchable(this)
        val rows = if (installed.isEmpty()) {
            store.knownApps().map { Launchable(it.label, it.pkg, ToxicHint.looksToxic(it.pkg, it.label)) }
        } else installed
        for (app in rows) {
            list.addView(Button(this).apply {
                text = (if (app.suggested) "suggérée · " else "") +
                    (if (app.pkg in blocked) "Scellée · " else "Libre · ") + app.label
                setOnClickListener {
                    if (store.isManualActive()) return@setOnClickListener
                    if (app.pkg in blocked) blocked.remove(app.pkg) else blocked.add(app.pkg)
                    store.setBlocked(blocked)
                    text = (if (app.suggested) "suggérée · " else "") +
                        (if (app.pkg in blocked) "Scellée · " else "Libre · ") + app.label
                }
            })
        }
    }

    private fun renderStatus() {
        if (!::status.isInitialized) return
        val sched = if (store.isScheduled()) "créneau actif" else "hors créneau"
        val windows = store.windows().size
        if (!isAccessibilityOn() && (store.isManualActive() || store.windows().isNotEmpty())) {
            status.text = "Service OFF : protection inactive. Session ou créneaux toujours enregistrés. $windows créneau(x), $sched."
            return
        }
        if (!isAccessibilityOn()) {
            status.text = "Active le service d’accessibilité Focus, puis lance."
            return
        }
        if (store.isManualActive()) {
            val left = ((store.endsAt() - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
            status.text = "Session %02d:%02d · %d slips · $windows créneau(x)".format(left / 60, left % 60, store.slips())
            return
        }
        status.text = "$sched · $windows créneau(x) · ${store.slips()} slips"
    }

    private fun parseHm(raw: String): Int? {
        val bits = raw.trim().split(":")
        if (bits.size != 2) return null
        val h = bits[0].toIntOrNull() ?: return null
        val m = bits[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    private fun isAccessibilityOn(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.contains("$packageName/${FocusAccessibilityService::class.java.name}")
            || enabled.contains("$packageName/.FocusAccessibilityService")
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }
}
