# AutoStream

Android-App, die in **Android Auto** erscheint und Web-Streamingdienste
(Netflix, YouTube, Disney+, Prime Video, Twitch, eigene URLs) auf dem
Autodisplay anzeigt – **nur, solange das Fahrzeug steht**.

## Funktionsweise

- Die App meldet sich als Android-Auto-App der Kategorie *Navigation* an.
  Dadurch bekommt sie eine frei bemalbare Fläche (Surface) auf dem Autodisplay.
- Auf diese Fläche wird ein virtuelles Display gelegt, auf dem eine WebView
  läuft (`car/WebRenderer.kt`). Tippen, Wischen (Schwenk-Modus ✥) und Zoomen
  am Autodisplay werden an die WebView weitergegeben.
- Desktop-User-Agent + Widevine-Freigabe, damit Netflix & Co. im Browser abspielen.
- **Stillstand-Sperre** (`car/MotionMonitor.kt`): Fahrzeuggeschwindigkeit über
  Android Auto und GPS des Handys. Fährt das Auto oder ist der Zustand
  unbekannt, wird das Video pausiert und überdeckt; Eingaben werden ignoriert.

## Installation

1. APK bauen: GitHub → *Actions* → *Build APK* → Artefakt `AutoStream-debug-apk`
   herunterladen (oder lokal `./gradlew assembleDebug` mit Android SDK).
2. APK auf dem Handy installieren (Installation aus unbekannten Quellen erlauben).
3. Android Auto → Einstellungen → 10× auf **Version** tippen → Entwicklermodus.
   Drei-Punkte-Menü → **Entwicklereinstellungen** → **Unbekannte Quellen** an.
4. AutoStream auf dem Handy öffnen → **Standort erlauben** → bei den Diensten
   über **Login** anmelden (Login-Cookies gelten auch im Auto).
5. Im Auto erscheint *AutoStream* im App-Launcher.

## Bekannte Einschränkungen

- Netflix verlangt einen „echten“ Browser; die Wiedergabe in einer WebView
  (Widevine L3) kann je nach Gerät abgelehnt werden oder auf niedrige Auflösung
  begrenzt sein. YouTube, Twitch & Co. funktionieren in der Regel problemlos.
- Texteingabe am Autodisplay ist nicht möglich – Logins/Suchen auf dem Handy.
- Neuere Android-Auto-Versionen können Apps aus unbekannten Quellen jederzeit
  wieder einschränken. Die App ist nicht für den Play Store gedacht.
- Ohne GPS-Fix (Tiefgarage) und ohne Fahrzeug-Geschwindigkeitsdaten bleibt die
  Wiedergabe gesperrt.
