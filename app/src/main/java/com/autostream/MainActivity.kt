package com.autostream

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Einstellungen auf dem Handy: Verknüpfungen, Login, Berechtigungen. */
class MainActivity : Activity() {
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(header("So geht's"))
        root.addView(TextView(this).apply {
            text = """
                1. Android Auto → Einstellungen → 10× auf „Version“ tippen → Entwickler-Modus aktivieren.
                2. Drei-Punkte-Menü → Entwicklereinstellungen → „Unbekannte Quellen“ einschalten.
                3. Unten Standort erlauben (für die Stillstand-Sperre).
                4. Bei Netflix & Co. hier auf dem Handy über „Login“ anmelden.
                5. Im Auto „AutoStream“ öffnen. Videos laufen nur, solange das Auto steht.
            """.trimIndent()
        })

        root.addView(Button(this).apply {
            text = "Standort erlauben"
            setOnClickListener {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ), 1
                )
            }
        })

        root.addView(Switch(this).apply {
            text = "Desktop-Modus (für Netflix nötig)"
            isChecked = Shortcuts.desktopMode(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                Shortcuts.setDesktopMode(this@MainActivity, checked)
            }
        })

        root.addView(header("Verknüpfungen"))
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)

        root.addView(header("Neue Verknüpfung"))
        val nameInput = EditText(this).apply { hint = "Name" }
        val urlInput = EditText(this).apply {
            hint = "https://…"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }
        root.addView(nameInput)
        root.addView(urlInput)
        root.addView(Button(this).apply {
            text = "Hinzufügen"
            setOnClickListener {
                val name = nameInput.text.toString().trim()
                var url = urlInput.text.toString().trim()
                if (name.isEmpty() || url.isEmpty()) return@setOnClickListener
                if (!url.startsWith("http")) url = "https://$url"
                Shortcuts.save(context, Shortcuts.load(context) + Shortcut(name, url))
                nameInput.text.clear()
                urlInput.text.clear()
                refresh()
            }
        })

        setContentView(ScrollView(this).apply { addView(root) })
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Bitte Standort erlauben – sonst bleibt die Videowiedergabe im Auto gesperrt.", Toast.LENGTH_LONG).show()
        }
    }

    private fun header(t: String) = TextView(this).apply {
        text = t
        textSize = 18f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, (20 * resources.displayMetrics.density).toInt(), 0, 8)
    }

    private fun refresh() {
        list.removeAllViews()
        val items = Shortcuts.load(this)
        items.forEachIndexed { index, s ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(TextView(this).apply {
                text = "${s.name}\n${s.url}"
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            })
            row.addView(Button(this).apply {
                text = "Login"
                setOnClickListener {
                    startActivity(
                        Intent(context, LoginActivity::class.java)
                            .putExtra(LoginActivity.EXTRA_URL, s.url)
                            .putExtra(LoginActivity.EXTRA_NAME, s.name)
                    )
                }
            })
            row.addView(Button(this).apply {
                text = "✕"
                setOnClickListener {
                    Shortcuts.save(context, items.filterIndexed { i, _ -> i != index })
                    refresh()
                }
            })
            list.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
    }
}
