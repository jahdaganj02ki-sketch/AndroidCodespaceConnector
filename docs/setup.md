# Setup-Anleitung

## Für Anwender (Moto G84)

1. **OAuth-App anlegen**: <https://github.com/settings/developers> → OAuth Apps → New OAuth App
   → Callback `com.codespacemanager.app://oauth/callback`. Die Werte werden per
   Repository-Secret `GITHUB_CLIENT_ID`/`GITHUB_CLIENT_SECRET` in den Build gegeben – die App
   bringt die OAuth-App also fest mit und startet direkt mit dem Login-Screen.
2. **Secrets hinterlegen**: Repository → Settings → Secrets and variables → Actions →
   `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`. Fehlen sie (z. B. Build aus einem Fork), klappt
   der Login-Screen den Abschnitt **„OAuth-App konfigurieren“** auf; dort lassen sich ID und
   Secret nachträglich eintragen.
3. **APK beziehen**: Actions → Workflow *Android* → grüner Lauf → Artifact
   `codespace-manager-<sha>` → `app-debug.apk` herunterladen.
4. **Installieren**: APK auf das Gerät kopieren, antippen, „Unbekannte Apps“ erlauben.
5. **Konto anmelden**: beim ersten Start erscheint der Login-Screen → „Mit GitHub anmelden“
   (Kontoauswahl) oder „Mit Google anmelden“ (GitHubs volle Anmeldeseite inkl. „Continue with
   Google“). Die Auswahl erfolgt in GitHubs Browser-Seite, nicht in einem Google-Fenster der App.
6. **Codespace starten**: *Repos* → Repo → *Codespace starten* → Bestätigungs-Diff prüfen →
   *Übernehmen* → Fortschritt beobachten → Editor öffnet sich.

## Für Entwickler

### Voraussetzungen

* JDK 17
* Android SDK (Platform 35, Build-Tools 35.0.0)
* Lokal: `ANDROID_HOME` setzen und `sdk.dir` in `local.properties` eintragen – **oder** einfach
  den CI-Workflow nutzen.

### Lokaler Build

```bash
./gradlew test lintDebug assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

### CI

`.github/workflows/android.yml`:

1. `checkout` → `setup-java` (17) → `setup-gradle` (Cache, Gradle 8.11.1)
2. `local.properties` mit `sdk.dir=$ANDROID_HOME`
3. `./gradlew --no-daemon test lintDebug assembleDebug`
4. `upload-artifact` (`codespace-manager-<sha>`, 14 Tage)

`GITHUB_CLIENT_ID`/`GITHUB_CLIENT_SECRET` werden als Environment-Variablen an Gradle gereicht und
landen in `BuildConfig`. Ohne die Secrets bleibt `BuildConfig` leer – der Login-Screen blendet
dann den Abschnitt „OAuth-App konfigurieren“ ein, damit die App trotzdem nutzbar ist.

### Debug-Key

`keystore/debug.keystore` (Passwort `android`, Alias `androiddebugkey`) liegt bewusst im Repo.
Nur dadurch lässt sich ein CI-Build per Sideload **über** eine bestehende Installation legen –
sonst schlägt die Signaturprüfung fehl und die Konten/Tokens gehen verloren.

### Struktur der devcontainer-Dateien

```
.devcontainer/
  devcontainer.json        von der App erzeugt (Template oder Merge)
  setup-code-server.sh     Theme, trusted-origins, root-owned Erweiterungsliste, Entrypoint-Wrapper
  setup-android.sh         nur im Template „Android“
  extensions.txt           dokumentarisch (nicht die Installationsquelle)
  extensions.lock.json     ID → .vsix-URL (Fallback, wenn Open VSX im Container nicht erreichbar ist)
```

Die Erweiterungsliste, aus der der Container tatsächlich installiert, liegt ausschließlich unter
`/usr/local/share/codespace-manager/extensions.txt` (root-owned, außerhalb des Workspace) – so kann
ein fremdes Repository keine Erweiterungen in einen zeitweise anonym erreichbaren Codespace
einschleusen.
