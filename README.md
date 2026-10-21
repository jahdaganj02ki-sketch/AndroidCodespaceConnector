# Codespace Manager – native Android-App

Native Android-App (Kotlin/Compose), die sich mit mehreren GitHub-Konten verbindet, Repos
anlegt, einen GitHub Codespace startet und den **code-server-Editor direkt in der App** öffnet –
mit Terminal, vorinstalliertem **Kilo Code**, **Cline** und dem Theme **„GitHub Dark Default“**.
Als Alternative steht jederzeit das native Codespaces-UI im Browser (Custom Tab) zur Verfügung.

* Paket: `com.codespacemanager.app`
* Zielgerät: Moto G84 5G (Android 13/14, 20:9)
* Sprache: **UI Deutsch**, Code Englisch
* Build: **nur Debug-APK über GitHub Actions** (fester Debug-Key im Repo → Update per Sideload)

---

## 1. Schnellstart

### 1.1 OAuth-App anlegen (einmalig, manuell)

1. <https://github.com/settings/developers> → **OAuth Apps** → **New OAuth App**
2. Name: `Codespace Manager`, Homepage optional
3. **Authorization callback URL**: `com.codespacemanager.app://oauth/callback`
4. **Generate client secret** und beide Werte notieren.

Die App liefert die OAuth-App fest mit. Für die Anmeldung ist deshalb **kein Setup-Schritt**
nötig – der erste Bildschirm ist die Anmeldung selbst.

### 1.2 CI-Secrets hinterlegen

Im Repository unter **Settings → Secrets and variables → Actions**:

| Secret | Inhalt |
|---|---|
| `GITHUB_CLIENT_ID` | Client-ID der OAuth-App |
| `GITHUB_CLIENT_SECRET` | Client-Secret der OAuth-App |

Die Werte werden als Gradle-Properties injiziert und landen in `BuildConfig`. Fehlen sie (z. B.
bei Builds aus einem Fork), zeigt der Login-Screen den Abschnitt **„OAuth-App konfigurieren"**
geöffnet; nach dem Eintragen von ID und Secret funktioniert die Anmeldung. Die Eingabe hat
Vorrang vor dem BuildConfig-Wert und wird lokal gespeichert.

### 1.3 Build auslösen

```bash
git push origin main
```

Der Workflow `.github/workflows/android.yml` baut `test`, `lintDebug` und `assembleDebug` und
lädt das APK als Artifact `codespace-manager-<sha>` hoch (Aufbewahrung 14 Tage).

### 1.4 Auf dem Gerät installieren

1. Artifact herunterladen und `app-debug.apk` auf das Moto G84 kopieren.
2. APK antippen → „Unbekannte Apps“ erlauben (Einstellungen → Apps → Spezialzugriff → Install
   unbekannter Apps).
3. Der Debug-Key liegt im Repo (`keystore/debug.keystore`, Passwort `android`). Dadurch lässt sich
   ein neues APK per Sideload **über** die installierte Version legen – Konten und Tokens bleiben
   erhalten.

---

## 2. Nutzung

### 2.1 Anmeldung

Beim Start (ohne verbundenes Konto) zeigt die App den **Login-Screen**:

* **„Mit GitHub anmelden“** öffnet GitHubs Autorisierungsseite mit Kontoauswahl
  (`prompt=select_account`).
* **„Mit Google anmelden“** öffnet dieselbe Seite **ohne** `prompt` – GitHub blendet dann seine
  volle Anmeldeseite inkl. **„Continue with Google“** ein. Die Auswahl erfolgt also in
  GitHubs Browser-Seite, **nicht** in einem Google-Fenster der App: die App enthält kein
  Google-SDK und kein Credential Manager. Ein Google-ID-Token lässt sich nicht in ein
  GitHub-Token tauschen – GitHub ist der einzige Token-Aussteller.

Ist der Browser bereits bei GitHub angemeldet, springt GitHub direkt zur Freigabeseite; die
„Continue with Google“-Schaltfläche ist dann nicht sichtbar. Abbrechen in GitHub führt in der
App zu einer Fehlermeldung mit „Erneut versuchen“.

Nach erfolgreicher Anmeldung öffnet die App die Codespaces. Weitere Konten lassen sich über
**Konten → Konto hinzufügen** hinzufügen (dort erscheint derselbe Login-Screen); wird das letzte
Konto entfernt, kommt die App zum Login-Screen zurück.

### 2.2 Codespaces verwalten

1. **Repos** → Repository auswählen (eigene + Organisation-Repos) oder *Neues Repo* anlegen
   (Template **Standard** oder **Android**).
2. **Codespace starten** → die App legt bei Bedarf den Branch `codespace/editor` an, erweitert
   den devcontainer um den code-server-Editor und startet den Codespace.
3. **Editor** öffnet sich in der App (Vollbild-WebView). Die Statusanzeige in der Topbar zeigt
   **„Port öffentlich“** oder **„Port privat“**.

### Port-Sichtbarkeit (wichtig)

Solange der Editor geöffnet ist, läuft der code-server **ohne Anmeldung** und ist unter einer
anonymen Adresse erreichbar. Wer diese Adresse kennt, kann Code ausführen. Deshalb:

* Der Port wird **automatisch privat** geschaltet beim Verlassen des Editors, beim Stoppen oder
  Löschen des Codespaces und nach **15 Minuten Inaktivität** (jede Interaktion im Editor setzt den
  Timer zurück).
* Der Schalter in der Topbar erlaubt das manuelle Umschalten.
* Die Adresse wird nirgends geteilt – bitte nicht weitergeben.

> **Org-Policies:** Manche Organisationen erlauben keine öffentlichen Ports. Die App prüft das
> (`runtime_constraints.allowed_port_privacy_settings`) und arbeitet dann mit privatem Port weiter –
> der Editor ist dann nur über das Codespaces-UI nutzbar.

---

## 3. Wie der Editor entsteht

| Schritt |Ort | Beschreibung |
|---|---|---|
| devcontainer | Repo, Branch `codespace/editor` | code-server-Feature (`ghcr.io/coder/devcontainer-features/code-server:2`, Version 4.141.0), `auth:none`, `host:0.0.0.0`, Port `13337`, Telemetrie/Update-Check/Workspace-Trust aus. `image`, fremde Features, `customizations`, `env`, `ports` bleiben unangetastet. |
| Setup-Skript | `.devcontainer/setup-code-server.sh` | legt die Erweiterungsliste **root-owned außerhalb des Workspace** ab, installiert Theme + `trusted-origins` und wrappt den code-server-Entrypoint |
| Erweiterungen | Container-Start | `ensure-extensions.sh` installiert fehlende Erweiterungen **synchron vor** dem code-server-Start (Gallery → `.vsix`-Fallback aus `extensions.lock.json` → Hinweis, kein Abbruch) |
| Editor-URL | `https://<codespace>-13337.app.github.dev` | der Tunnel-Port wird über die Dev-Tunnels-API öffentlich geschaltet (ACE `anonymous`) |

Details zu den API-Verträgen stehen in [`docs/api-reference.md`](docs/api-reference.md), der
Spike-Protokoll in [`docs/spike-codespace-port.md`](docs/spike-codespace-port.md).

---

## 4. Projektstruktur

```
app/src/main/java/com/codespacemanager/app/
  core/        ApiException, NetworkException
  ui/          theme/ components/ navigation/ screens/{login,accounts,repos,repodetail,codespaces,start,editor,settings,help}
  domain/      model/ gateway/ usecase/   (Ports & Adapter, ohne Android-Abhängigkeiten)
  data/        remote/{api,tunnel,openvsx,auth,editor,update} local/{db,datastore,tokenstore,assets} repository/
  platform/    webview/ customtabs/ crypto/ auth/
  di/          Hilt-Module
```

* `domain/` enthält die gesamte Logik (State-Machine, devcontainer-Merge, PKCE, Port-Policy) und
  ist ohne Android-Abhängigkeiten testbar.
* `data/` implementiert die Gateways (`RepoGateway`, `CodespaceGateway`, `PortGateway`, …).
* `ui/screens/login/` enthält den Anmeldescreen; er ist zugleich Start-Gate (ohne Konto) und
  Route „Konto hinzufügen“.

---

## 5. Tests & Qualität

```bash
./gradlew test lintDebug assembleDebug
```

Unit-Tests (JVM, ohne Emulator): PKCE (RFC-7636-Testvektor), OAuth-URL (prompt-Variante
GitHub/Google), Persistenz des laufenden OAuth-Flows, Namensvalidierung, devcontainer-
Template und -Merge, Browse-URL, State-Machine, ACE-Payloads und Pfadvarianten-Fallback der
Tunnel-API (MockWebServer), Token-Verschlüsselung, Script-Rendering, Kontingent-Schätzung.

---

## 6. Bekannte Grenzen

* Nur **github.com** (keine GitHub Enterprise Server).
* Kein Light-Modus (die App ist bewusst dark-only).
* Keine Play-Store-Veröffentlichung, kein Hintergrund-Start von Codespaces.
* Provider-Keys der Agenten werden **nicht** von der App gespeichert – sie werden einmalig im
  Erweiterungs-UI des Codespaces hinterlegt und überleben dort Neustarts.
* Der Zugriff auf private Ports über `X-Tunnel-Authorization` funktioniert nur für HTTP, nicht für
  WebSockets (Terminal). Deshalb ist der öffentliche Port der Hauptweg.

## 7. Sicherheitshinweise

* `client_secret` liegt im Debug-APK (GitHub verlangt es beim Web-Flow und stuft Secrets in
  nativen Apps ohnehin als öffentlich ein). Das Secret allein erlaubt keinen Zugriff ohne
  Nutzerinteraktion – PKCE bindet den Autorisierungscode an den Verifier, den nur die App kennt.
* Der Anmeldeflow wird im DataStore gespeichert: Wird der Prozess während des
  Browser-Rundgangs beendet, läuft der Login nach dem Zurückkehren in die App weiter.
* Tokens werden mit einem AES/GCM-Schlüssel aus dem **Android Keystore** verschlüsselt und in
  `EncryptedSharedPreferences` abgelegt; sie werden beim Entfernen eines Kontos gelöscht.
* Der Zugriffsschutz des Codespaces selbst liegt bei GitHub (Codespaces-Kontingent, Org-Policies).
