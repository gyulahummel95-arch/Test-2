package com.autostream

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Einstellungen auf dem Handy: Verknüpfungen, Login, Berechtigungen. */
class MainActivity : Activity() {
    private lateinit var list: LinearLayout
    private lateinit var diagText: TextView

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

        root.addView(Switch(this).apply {
            text = "Ton während der Fahrt weiterspielen (Bild bleibt gesperrt)"
            isChecked = Shortcuts.audioWhileDriving(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                Shortcuts.setAudioWhileDriving(this@MainActivity, checked)
            }
        })

        root.addView(header("Design im Auto"))
        root.addView(TextView(this).apply {
            text = "Tipp: Im Auto 5× schnell auf die Uhr tippen wechselt das Design."
        })
        val themeGroup = RadioGroup(this)
        Shortcuts.THEMES.forEach { (id, label) ->
            themeGroup.addView(RadioButton(this).apply {
                text = label
                tag = id
                isChecked = Shortcuts.theme(this@MainActivity) == id
                setOnClickListener {
                    if (id == "custom" && !Backgrounds.exists(context)) pickBackground()
                    else Shortcuts.setTheme(context, id)
                }
            })
        }
        root.addView(themeGroup)
        root.addView(Button(this).apply {
            text = "Hintergrundbild aus Galerie wählen"
            setOnClickListener { pickBackground() }
        })
        root.addView(Button(this).apply {
            text = "Hintergrundbild entfernen"
            setOnClickListener {
                Backgrounds.clear(context)
                if (Shortcuts.theme(context) == "custom") Shortcuts.setTheme(context, "standard")
                recreate()
            }
        })

        if (isXiaomi()) {
            root.addView(header("Xiaomi / Redmi / POCO"))
            root.addView(TextView(this).apply {
                text = "MIUI/HyperOS beendet Apps im Hintergrund. Bitte alle drei einstellen:"
            })
            root.addView(Button(this).apply {
                text = "1. Autostart erlauben"
                setOnClickListener { openXiaomiAutostart() }
            })
            root.addView(Button(this).apply {
                text = "2. Akku: „Keine Einschränkungen“"
                setOnClickListener { openAppDetails() }
            })
            root.addView(Button(this).apply {
                text = "3. „Pop-up-Fenster im Hintergrund“ erlauben"
                setOnClickListener { openXiaomiPermissions() }
            })
        }

        root.addView(header("Diagnose (letzte Verbindung)"))
        diagText = TextView(this).apply { setTextIsSelectable(true) }
        root.addView(diagText)
        root.addView(Button(this).apply {
            text = "Diagnose zurücksetzen"
            setOnClickListener {
                Diagnostics.clear(context)
                refreshDiagnostics()
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
        refreshDiagnostics()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Bitte Standort erlauben – sonst bleibt die Videowiedergabe im Auto gesperrt.", Toast.LENGTH_LONG).show()
        }
    }

    private fun pickBackground() {
        @Suppress("DEPRECATION")
        startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), REQ_BACKGROUND)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode != REQ_BACKGROUND || resultCode != RESULT_OK || uri == null) return
        val ok = try { Backgrounds.saveFromUri(this, uri) } catch (e: Exception) { false }
        if (ok) Shortcuts.setTheme(this, "custom")
        Toast.makeText(this, if (ok) "Hintergrundbild gesetzt" else "Bild konnte nicht geladen werden", Toast.LENGTH_SHORT).show()
        recreate()
    }

    private fun refreshDiagnostics() {
        val perm = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        diagText.text = Diagnostics.report(this) +
            "\nStandort-Berechtigung: ${if (perm) "erteilt" else "FEHLT"}" +
            "\nHandy: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}"
    }

    private fun isXiaomi() = Build.MANUFACTURER.lowercase().let {
        it.contains("xiaomi") || it.contains("redmi") || it.contains("poco")
    }

    private fun tryStart(intent: Intent): Boolean = try {
        startActivity(intent); true
    } catch (e: Exception) {
        false
    }

    private fun openAppDetails() {
        tryStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun openXiaomiAutostart() {
        val ok = tryStart(Intent().setComponent(ComponentName(
            "com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"
        )))
        if (!ok) openAppDetails()
    }

    private fun openXiaomiPermissions() {
        val ok = tryStart(Intent("miui.intent.action.APP_PERM_EDITOR")
            .putExtra("extra_pkgname", packageName))
        if (!ok) openAppDetails()
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

    companion object {
        private const val REQ_BACKGROUND = 42
    }
}
