# Rechenwürfel – Web-Version

Dieselben Regeln und Einstellungen wie die Android-App (inklusive abwählbarer Rechenarten), als statische Seite ohne Build-Schritt:
`index.html` + `core.js` (Spiellogik) + `app.js` (Oberfläche, Animationen, Sound).
Läuft in jedem aktuellen Browser, auch auf dem Handy. Sounds und Musik werden mit der
Web Audio API erzeugt, Einstellungen bleiben im `localStorage` des Browsers.

## Lokal ausprobieren

```bash
cd rechenwuerfel/web
npm run serve          # http://localhost:8080/
```

(Oder `index.html` direkt im Browser öffnen.)

## Auf GitHub Pages veröffentlichen

Die Seite braucht nur die drei Dateien `index.html`, `core.js`, `app.js`.

1. In den Repository-Einstellungen **Settings → Pages** die Quelle wählen, z. B. den Branch `gh_pages` (Ordner `/`).
2. Den Ordner `rechenwuerfel/web` in diesen Branch übernehmen (z. B. den Feature-Branch nach `gh_pages` mergen).
3. Danach ist das Spiel unter `https://<benutzer>.github.io/<repo>/rechenwuerfel/web/` erreichbar,
   für dieses Repository also `https://raffelino.github.io/stuff/rechenwuerfel/web/`.

Alternativ die drei Dateien in einen eigenen Ordner (etwa `docs/`) kopieren und diesen in den Pages-Einstellungen auswählen.

## Tests

```bash
npm install            # einmalig, lädt Playwright (Chromium wird separat benötigt)
npm test               # Logik-Tests (node --test) + Browser-Durchlauf (Playwright)
npm run test:unit      # nur Logik-Tests, ohne Browser
```

Der Browser-Test öffnet die Seite in Chromium und spielt komplette Partien durch: Menü, Anleitung, Optionen,
Konfiguration mit Figurentausch, alle Würfelseiten, richtige/falsche Antwort, Zeitablauf, Tastatureingabe,
Sieg, Neustart und zurück ins Menü. Dafür ist der Würfel über `window.Rechenwuerfel.diceOverride`
steuerbar, und `?fast=1` verkürzt die Animationen.
