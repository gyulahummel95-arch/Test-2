# Fester Signaturschlüssel

Damit sich neue AutoStream-Versionen über die alte installieren lassen (ohne Logins
und Einstellungen zu verlieren), muss jede APK mit demselben Schlüssel signiert sein.
Der Schlüssel liegt **nicht** im Repository (das ist öffentlich), sondern in zwei
GitHub-Secrets:

| Secret | Inhalt |
|---|---|
| `SIGNING_KEYSTORE` | Keystore als Base64-Text |
| `SIGNING_PASSWORD` | Passwort für Keystore und Schlüssel (Alias `autostream`) |

Eintragen: GitHub → Repository → *Settings* → *Secrets and variables* → *Actions*
→ *New repository secret*.

Ohne Secrets baut der Workflow weiter, aber mit zufälligem Schlüssel (Warnung im Lauf).

Erwarteter Zertifikats-Fingerabdruck (SHA-256), steht im Schritt „Show APK signature“:
`62:6B:0E:F4:B7:28:FC:C6:15:31:2E:D4:B5:87:64:93:B4:38:72:38:F8:5B:A0:39:20:44:A4:AF:61:C0:21:CB`

**Keystore und Passwort sicher aufbewahren.** Geht der Schlüssel verloren, muss die
App für das nächste Update einmal deinstalliert werden.
