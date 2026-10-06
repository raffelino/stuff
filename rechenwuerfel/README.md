# 🎲 Rechenwürfel – Android-Rechenspiel

Ein Brettspiel für 1–4 Spieler: würfeln, Rechenaufgabe lösen, Figur vorziehen. Wer zuerst im Ziel ist, gewinnt.

## Spielregeln

* Jeder Spieler wählt eine Figur (Emoji) und einen Namen.
* Wer dran ist, tippt auf **Würfeln**. Der Würfel hat sechs Seiten:
  `+`, `−`, `×`, `÷`, **Joker** 🃏 (Rechenart selbst wählen) und **Aussetzen** 💤.
* Es erscheint eine Aufgabe im eingestellten Zahlenraum (Standard: bis 100). Die Antwort wird über die Zahlentastatur eingegeben.
* Richtig innerhalb der Zeit → die Figur zieht vor: **Plus 1**, **Minus 2**, **Mal 3**, **Geteilt 4** Felder (einstellbar).
* Falsch oder Zeit abgelaufen → die Figur bleibt stehen.
* Aufgaben haben nie negative Ergebnisse und keine Reste beim Teilen.
* Einzelne Rechenarten lassen sich beim Spielstart abwählen (z. B. nur Plus und Minus). Der Würfel zeigt dann nur erlaubte Seiten, der Joker bietet nur erlaubte Rechenarten an.

## Einstellbar beim Spielstart

| Einstellung | Standard |
|---|---|
| Spieleranzahl, Namen, Figuren | 2 Spieler |
| Zahlenraum | 100 |
| Zeit pro Aufgabe | 30 s |
| Anzahl Felder auf dem Brett | 30 |
| Felder pro Rechenart (+ − × ÷) | 1 / 2 / 3 / 4 |
| Rechenarten im Spiel (abwählbar, mindestens eine) | alle vier |

Über **Optionen** (Hauptmenü) oder die Symbole oben im Spiel lassen sich Hintergrundmusik und Soundeffekte getrennt ein- und ausschalten.

## Tests

Alle Tests laufen auf der JVM, ein Emulator ist nicht nötig:

* **Unit-Tests**: Aufgaben-Generator (kein negatives Ergebnis, kein Rest, Zahlenraum eingehalten), Einstellungs-Grenzen, Timer.
* **Oberflächentests mit Robolectric**: Hauptmenü (Anleitung, Optionen-Dialog, Navigation), Setup (Spieleranzahl, Namen, Figuren-Tausch, Chips, Speichern/Laden, ungültige Eingaben), Spiel (jede Würfelseite, richtige und falsche Antwort, Zeitablauf, Joker, Aussetzen, Tastatur, Timer-Pause, Zurück-Dialog, Sieg, Neustart) sowie ein **Ende-zu-Ende-Durchlauf** von Menü über Konfiguration bis zum Sieg und zurück.

```bash
./gradlew testDebugUnitTest          # Android Studio / mit SDK
tools/nosdk/build-apk.sh             # ohne SDK: Tests sind Teil des APK-Builds
```

Für Tests ist der Würfel über `DiceRoller.override` steuerbar; die Aufgaben werden aus der Anzeige gelesen und gelöst.

## Technik

* Kotlin, nur Android-Framework (keine AndroidX-Abhängigkeiten), `minSdk 26`, `targetSdk 34`.
* Spielbrett und Würfel sind eigene `View`s mit Canvas-Zeichnung und Animationen.
* Alle Sounds sind synthetisch erzeugt (`tools/gen_sounds.py`) und liegen als WAV in `app/src/main/res/raw`.

### Projektstruktur

```
app/src/main/java/com/raffelino/rechenwuerfel/
  MainActivity.kt     Hauptmenü, Anleitung, Optionen
  SetupActivity.kt    Spieler + Einstellungen
  GameActivity.kt     Spielablauf (würfeln, Aufgabe, Timer, Zug, Sieg)
  BoardView.kt        Spielbrett (Schlangenpfad) + Figuren-Animation
  DiceView.kt         Würfel + Würfel-Animation
  MathTask.kt         Aufgaben-Generator
  GameSettings.kt     Einstellungen (SharedPreferences)
  SoundManager.kt     Soundeffekte + Hintergrundmusik
app/src/test/        Unit-Tests für den Aufgaben-Generator
tools/gen_sounds.py  erzeugt die Sounddateien neu
tools/nosdk/         Build der APK ohne Android SDK (siehe unten)
```

## Bauen

### Variante A: Android Studio (empfohlen)

1. Ordner `rechenwuerfel` in Android Studio öffnen (Gradle-Sync lädt SDK-Komponenten nach).
2. **Build ▸ Build Bundle(s) / APK(s) ▸ Build APK(s)** oder in der Konsole:

   ```bash
   ./gradlew assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk
   ./gradlew assembleRelease      # unsigniert, Signatur in Android Studio einrichten
   ./gradlew testDebugUnitTest    # Unit-Tests
   ```

### Variante B: ohne Android SDK (nur JDK 17+, Gradle, python3, curl, unzip)

```bash
tools/nosdk/build-apk.sh         # -> build-nosdk/rechenwuerfel-1.2.apk
```

Das Skript lädt aapt2, das Framework-Jar, `dx`, `apksig` und ProGuard von Maven Central, kompiliert Kotlin über ein kleines Gradle-Hilfsprojekt, führt alle Tests aus (Robolectric mit einem schlanken Ersatz für `androidx.test`, siehe `tools/nosdk/androidx-test-stubs`), schrumpft die Kotlin-Stdlib und portiert `invokedynamic`-Lambdas mit ProGuard auf normale Klassen zurück, erzeugt `classes.dex`, prüft die Dex auf `invoke-custom`-Aufrufstellen, paketiert (mit 4-Byte-Alignment) und signiert die APK (Signatur-Schema v2).

Hintergrund: `dx` reicht `invokedynamic` aus der Kotlin-Stdlib unverändert durch, Android kann diese Aufrufe aber nicht ausführen. Ohne den ProGuard-Schritt stürzt die App ab, sobald eine betroffene Stdlib-Methode aufgerufen wird.

Der Schlüssel `tools/nosdk/rechenwuerfel.p12` (Passwort `rechenwuerfel`) ist ein reiner **Entwicklungsschlüssel**. Für eine Veröffentlichung im Play Store muss ein eigener, geheimer Schlüssel verwendet werden.

## Web-Version

Unter `web/` liegt das Spiel zusätzlich als statische Webseite (HTML, JavaScript, Web Audio), die sich direkt
auf GitHub Pages hosten lässt. Anleitung, lokaler Start und Tests: [web/README.md](web/README.md).

## Installation auf dem Handy

APK aufs Gerät kopieren, antippen und die Installation aus unbekannter Quelle erlauben – oder per USB:

```bash
adb install -r rechenwuerfel-1.2.apk
```

Wird später eine mit anderem Schlüssel signierte Version (z. B. Debug-Build aus Android Studio) installiert, muss die alte App vorher deinstalliert werden.
