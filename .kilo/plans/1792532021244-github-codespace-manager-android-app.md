# GitHub Codespace Manager – native Android-App

Ziel: Native Android-App (Kotlin/Compose), die sich mit mehreren GitHub-Konten verbindet, Repos anlegt/auswählt, einen GitHub Codespace startet und **direkt in der App** (eingebetteter code-server-Editor mit Terminal, Kilo Code + Cline vorinstalliert) sowie per Custom Tab im Browser nutzbar macht. APK wird ausschließlich über GitHub Actions gebaut.

## 1. Kontext

- Repo `/workspaces/AndroidCodespaceConnector` ist leer (nur `.kilo/plans/`). Kein Android SDK/Gradle lokal → Projekt entsteht komplett neu, Build nur in CI.
- Zielgerät: **Moto G84 5G** (Snapdragon 695, 8/12 GB RAM, pOLED 2400×1080 @120 Hz, 20:9, Android 13/14).
- UI-Sprache **Deutsch**, Code englisch. Paket `com.codespacemanager.app`, App-Name „Codespace Manager", OAuth-Scheme `com.codespacemanager.app`.

## 2. Entscheidungen (vom User bestätigt)

| Thema | Entscheidung |
|---|---|
| Arbeitsfläche | **Hybrid**: code-server in Vollbild-WebView **plus** Custom Tab auf das native Codespaces-UI |
| GitHub-Login | **OAuth App + Code + PKCE (S256)**, Custom-Scheme-Redirect; Client-ID/Secret per CI injiziert, in den App-Einstellungen überschreibbar |
| Neues Repo | Template-Auswahl: „Standard" (schnell) oder „Android" (JDK 17 + Android SDK) |
| Repo **ohne** devcontainer | App legt Branch `codespace/editor` mit `.devcontainer/devcontainer.json` an; Default-Branch bleibt unangetastet |
| Repo **mit** eigenem devcontainer | App **erweitert** die vorhandene Datei (JSON-Merge, s. §5) auf `codespace/editor`; Image/Toolchain bleiben erhalten |
| Build | **Nur Debug-APK** via GitHub Actions, **fester Debug-Key im Repo** → Update per Sideload möglich, Konten/Tokens bleiben erhalten |
| Port-Sichtbarkeit | **Nur während der Nutzung öffentlich**; Editor verlassen / gestoppt / 15 min inaktiv → automatisch privat. Status + Button in der Topbar |
| Multi-Account | mehrere Konten parallel, umschaltbar, Tokens keystore-verschlüsselt |
| Erweiterungen | **Kilo Code + Cline** (+ GitHub-Theme) in **jedem** Codespace vorinstalliert – auch in bereits benutzten – und startklar |
| API-Key der Agents | wird **im Erweiterungs-UI** im Editor hinterlegt (pro Codespace, überlebt Neustarts); die App speichert keine Provider-Keys |
| UI-Stil | **Modern monochrom** wie GitHub Codespaces: GitHub-Dark-Graustufen + **#1F6FEB als einziger Akzent**, Dark-only, kein Material-You; Editor mit echten **„GitHub Dark Default"**-Theme |

## 3. Verifizierte API-Verträge (Quelle: GitHub-Doku, gh-CLI-Quellcode, dev-tunnels-SDK, code-server-Quellcode)

**OAuth** (PKCE seit 07/2025 von GitHub empfohlen; Custom-Scheme-Redirects erlaubt → GitHub blendet den Account-Picker automatisch ein)
- `GET https://github.com/login/oauth/authorize?client_id=…&redirect_uri=com.codespacemanager.app://oauth/callback&scope=repo%20codespace%20read:org&state=…&code_challenge=…&code_challenge_method=S256&prompt=select_account`
- `POST https://github.com/login/oauth/access_token` – Body `client_id, client_secret, code, redirect_uri, code_verifier`, Header `Accept: application/json`
- Scopes: `repo` (Repos anlegen), `codespace` (Pflicht), `read:org` (Org-Repos). OAuth-Tokens laufen nicht ab.
- Alle REST-Calls: `Authorization: Bearer …`, `Accept: application/vnd.github+json`, `X-GitHub-Api-Version: 2026-03-10`, Paginierung via `Link`-Header (`per_page=100`).

**Codespaces**
| Zweck | Aufruf |
|---|---|
| Anlegen | `POST /user/codespaces` Body `{repository_id, ref, machine, geo, idle_timeout_minutes, devcontainer_path, display_name, multi_repo_permissions_opt_out}` → 201/202; 401 + `allow_permissions_url` = devcontainer-Freigabe im Custom Tab |
| Lesen | `GET /user/codespaces/{name}` → `state` (`Available|Shutdown|Starting|Rebuilding|ShuttingDown`), `machine`, `location`, `web_url`, `devcontainer_path`, `git_status{ref,ahead,behind,…}`, `billable_owner`, `runtime_constraints.allowed_port_privacy_settings` |
| Tunnel-Credentials | `GET /user/codespaces/{name}?internal=true&refresh=true` → `connection.tunnelProperties{serviceUri, tunnelId, clusterId, domain, connectAccessToken, managePortsAccessToken}` |
| Lifecycle | `POST /user/codespaces/{name}/start|stop`, `PATCH` (display_name/idle_timeout/machine), `DELETE /user/codespaces/{name}` |
| Listen | `GET /user/codespaces`, `GET /repos/{o}/{r}/codespaces` |
| Hilfen | `GET /repos/{o}/{r}/codespaces/new` (403 = kein Codespace möglich), `GET /repositories/{repoId}/codespaces/machines?location=…&ref=…&devcontainer_path=…`, `GET /repositories/{repoId}/codespaces/devcontainers?ref=…`, `GET /repositories/{repoId}/codespaces/permissions_check?ref=…&devcontainer_path=…` |
| `geo` vs `location` | Doku: `geo` (EuropeWest/UsEast/UsWest/SoutheastAsia); `location` wird abgeschaltet → App sendet `geo`, bei 400 Fallback `location` (Spike entscheidet) |

**Dev Tunnels** (nicht `api.github.com`; `serviceUri` aus `tunnelProperties`)
- Basis `serviceUri` (z. B. `https://use.rel.tunnels.api.visualstudio.com/`). **Pfadvarianten**: aktueller SDK `…/tunnels/{id}/ports`, beobachteter Traffic `…/api/v1/tunnels/{id}/ports` → App probiert beide, merkt sich die funktionierende Variante (DataStore).
- Header: `Authorization: Tunnel <managePortsAccessToken>`, `Content-Type: application/json;charset=UTF-8`, Query `api-version=2023-09-27-preview` (+ `includePorts=true` beim Listen).
- Port lesen `GET {base}/tunnels/{tunnelId}/ports/{port}`; anlegen/ändern **`PUT`** `{base}/tunnels/{tunnelId}/ports/{portNumber}` mit `If-None-Match: *` (create) bzw. `If-Match: *` (update); 409/412 tolerieren.
- Body (JSON-Feldnamen aus `TunnelPort`):
  ```json
  { "portNumber": 13337, "protocol": "http", "labels": ["UserForwardedPort"],
    "accessControl": { "entries": [ { "type": "anonymous", "subjects": [], "scopes": ["connect"] } ] } }
  ```
  **öffentlich** = ACE `{type:"anonymous",subjects:[],scopes:["connect"]}`; **privat** = `{type:"organization",subjects:["1"],scopes:["connect"],provider:"github",isDeny:true}` (gh-CLI-Default) bzw. ACE entfernen → Tunnel-ACL erben.
- Browse-URL: `https://<codespace-name>-<port>.app.github.dev` (gh-CLI-Konstruktion) – **öffentliche Ports brauchen keine Auth**; Fallback `portForwardingUris[0]`.
- `X-Tunnel-Skip-AntiPhishing-Page: true` unterdrückt die Interstitial-Seite → im WebView per `shouldInterceptRequest` injiziert.
- `X-Tunnel-Authorization: tunnel <connectAccessToken>` erlaubt HTTP-Zugriff auf private Ports – **WebSockets (Terminal) können aber keine Header setzen** → öffentlicher Port bleibt Hauptweg.
- Kontingent: Free-Privatkonto 120 Core-Stunden + 15 GB-Monat/Monat; 2-Core-Maschine = 2 Core-Stunden/Std.

**code-server / Devcontainer** (Quellcode geprüft)
- `ghcr.io/coder/devcontainer-features/code-server:2`: ohne `cert`/`certHost`/`certKey` läuft code-server als **reines HTTP** → Tunnel-`protocol:"http"`. Optionen: `auth:"none"`, `host:"0.0.0.0"`, `port:"13337"`, `workspace:"/workspaces/${localWorkspaceFolderBasename}"`, `disableTelemetry`, `disableUpdateCheck`, `disableWorkspaceTrust`, `trustedOrigins`, `extensions`.
- Das Feature erzeugt `/usr/local/bin/code-server-entrypoint` (Hintergrunddienst bei jedem Container-Start).
- **Origin-Check**: code-server lehnt Requests ab, deren `Origin` nicht zum `Host` passt; Dev-Tunnel-Relays schreiben den Host auf `localhost` um → Primär-URL ist die GitHub-Browse-URL, `trustedOrigins` muss die Codespace-Origin enthalten (zur Bauzeit unbekannt → `${containerEnv:CODESPACE_NAME}`).
- `portsAttributes.visibility` existiert **nicht** in der devcontainer-Spec → Sichtbarkeit nur über die Tunnel-API. `portsAttributes` kennt nur `label`, `onAutoForward`, `protocol`, `requireLocalPort`, `elevateIfNeeded`.
- code-server **v4.141.0 = VS Code 1.141.0**; Gallery ist **Open VSX** (seit 4.0.1). Die code-server-REST-API (`/api/v1/…`) existiert in aktuellen Releases nicht → keine Laufzeit-API nutzbar.

## 4. Architektur

**Stack**: Kotlin 2.x, AGP 8.7, Gradle 8.11.1, compileSdk/targetSdk 35, minSdk 26, JDK 17, Compose + Material 3, Hilt, Retrofit/OkHttp + kotlinx.serialization, Room, DataStore, androidx.security-crypto, Coroutines/Flow, androidx.browser (Custom Tabs). Ein Modul `:app`, Versionen im `gradle/libs.versions.toml` gepinnt.

**Client-ID/Secret**: `buildConfigField` aus Gradle-Property `GITHUB_CLIENT_ID`/`GITHUB_CLIENT_SECRET`; Quelle ist `local.properties` (gitignoriert) für lokale Builds bzw. CI-Secrets; fehlen sie, zeigt die App einen Setup-Screen mit Eingabefeldern (Override zur Laufzeit).

**Paketstruktur**
```
app/src/main/java/com/codespacemanager/app/
  ui/            theme/{Color,Theme,Type,Shape}.kt  screens/{accounts,repos,codespaces,editor,settings}  components/
  domain/        usecase/{PrepareRepo,StartCodespace,SyncExtensions}.kt  model/  statemachine/
  data/          remote/{auth,repo,contents,git,codespace,tunnel}  local/{db,datastore,tokenstore}  repository/
  platform/      webview/  customtabs/  crypto/  workmanager/
```

**Datenmodell**
- `AccountEntity(id, login, avatarUrl, accessTokenEnc, scopes, createdAt)` – Token via Android Keystore (AES/GCM, EncryptedSharedPreferences).
- `CodespaceEntity(name, accountId, repoFullName, state, machine, location, webUrl, portPublic, lastOpenedAt)` – Cache.
- DataStore `Settings(defaultRegion=EuropeWest, defaultMachine=basicLinux32gb, idleTimeoutMin=30, clientIdOverride, clientSecretOverride, autoPrivatePortOnExit=true, idlePrivatePortMin=15, tunnelApiPathVariant, extensionIds)` – kein Theme-Setting (dark-only).

**Screens** (Bottom-Navigation: Konten, Repos, Codespaces, Einstellungen)
1. **Konten** – hinzufügen (OAuth), umschalten, entfernen, Token-Status.
2. **Repos** – eigene + Org-Repos (`GET /user/orgs` → `GET /orgs/{org}/repos`), Suche, FAB „Neues Repo".
3. **Repo-Detail** – Codespaces des Repos, „Codespace starten", „Im Browser öffnen", Stoppen/Löschen, Git-Status.
4. **Codespaces (kontoweit)** – `GET /user/codespaces`: Status, Maschine, „zuletzt genutzt", Git-Status; Aktionen öffnen/stoppen/löschen/umbenennen; Kopfzeile mit **Kontingent-Schätzung** (Σ Kerne × Stunden seit `last_used_at`, als Schätzung markiert + Link auf github.com/settings/codespaces).
5. **Start-Fortschritt** – Statuszeilen der State-Machine, jederzeit „Im Browser öffnen" / Abbrechen.
6. **Editor** – Vollbild-WebView + schmale Topbar (Repo, Port-Status, Tastatur-Toggle, Port privat, Browser, Reload).
7. **Einstellungen** – Client-ID/Secret, Standard-Region/Maschine/Idle-Timeout, Erweiterungsliste, „Update prüfen", Hilfe.

**State-Machine „Codespace starten"** (sealed class + `when`, im ViewModel)
`PrüfeRepo → BereiteRepo(optional) → ErzeugeCodespace → WarteAufCodespace → VerbindePort → WarteAufEditor → Editor`
1. `PrüfeRepo` – devcontainer + `permissions_check` prüfen; 401-Freigabe-URL im Custom Tab.
2. `BereiteRepo` – nur wenn nötig (Merge-Regeln s. §5); Branch `codespace/editor` anlegen (`POST /repos/{o}/{r}/git/refs` mit SHA von `GET /repos/{o}/{r}/commits/{default_branch}`), Datei via Contents-API (`PUT …/contents/<pfad>`, base64, bei Update mit `sha`).
3. `ErzeugeCodespace` – erst `GET /repos/{o}/{r}/codespaces`: vorhandenen Codespace wiederverwenden/`start`en; sonst `POST /user/codespaces` mit `ref` + `devcontainer_path` + `display_name` („<repo> (App)"). *Edge Case*: Codespace aus der Web-UI mit anderem devcontainer → dort läuft kein code-server → Port antwortet nicht → Angebot „Neuen Codespace mit Editor-Devcontainer erstellen". *Read-only-Kollaborator*: der Codespace arbeitet dann auf einem Auto-Fork – App weist darauf hin.
4. `WarteAufCodespace` – `GET /user/codespaces/{name}` alle 5 s, max 10 min, Abbruch möglich.
5. `VerbindePort` – Tunnel-Credentials holen → Port 13337 per `PUT` anlegen/ändern → ACE anonymous. Erster Start eines Codespaces hängt davor die Extension-Installation (1–3 min) → Statuszeile „Erweiterungen werden vorbereitet".
6. `WarteAufEditor` – `GET https://<name>-13337.app.github.dev/` pollen bis 200 + code-server-Kennzeichen; 502/503 = „startet noch" (max. 3 min), Timeout = Fehler + Browser-Fallback.
7. `Editor` – WebView lädt `https://<name>-13337.app.github.dev/?folder=/workspaces/<repo>`.

**Port-Policy**
- Editor geöffnet → Port öffentlich (anonymous ACE), Status „Port öffentlich" in der Topbar.
- Editor verlassen / Codespace gestoppt / gelöscht → Port privat (Best-Effort, Fehler werden geloggt, nicht blockierend). App > 15 min im Hintergrund → privat; **Timer wird durch jede Editor-Interaktion und durch Vordergrund-Wechsel zurückgesetzt**.
- Manuell umschaltbar.
- **Recovery**: Bei Verbindungsfehler blendet die App statt der Fehlerseite ein Overlay ein: „Port wurde geschlossen / Codespace gestoppt" → „Port wieder öffnen" (ACE erneut setzen + Reload) / „Codespace neu starten". `onResume` prüft den Codespace-Status.

**WebView (Moto G84)**: `javaScriptEnabled`, `domStorageEnabled`, `databaseEnabled`, `mediaPlaybackRequiresUserGesture=false`, `setSupportZoom(false)`, `textZoom=100`, `windowSoftInputMode="adjustNothing"`, `android:largeHeap="true"`, Hardware-Accelerated, `usesCleartextTraffic=false` (Netzwerk-Security-Config: nur HTTPS auf `github.com`, `api.github.com`, `*.app.github.dev`, `*.githubpreview.dev`, `*.devtunnels.ms`), `shouldInterceptRequest` (nur bekannter Codespace-Host + `X-Tunnel-Skip-AntiPhishing-Page: true`), Navigation außerhalb der Allowlist → Custom Tab, `onReceivedSslError` nur für den bekannten Host freigeben, `saveState/restoreState` bei Rotation, `onReceivedError` → Recovery-Overlay.

## 5. Design-System, Devcontainer, Erweiterungen

### 5.1 UI (monochrom GitHub Dark, dark-only, `dynamicColor=false`)

| Rolle | Hex | Einsatz |
|---|---|---|
| `Canvas` | `#0D1117` | Scaffold, Editorfläche |
| `Surface` | `#161B22` | Cards, Dialoge, Bottom-Sheets, Listenzeilen |
| `SurfaceInset` | `#010409` | TopAppBar, NavigationBar, Panels |
| `SurfaceRaised` | `#21262D` | Overlays, Menüs, Tooltips |
| `Border` / `BorderStrong` | `#30363D` / `#484F58` | 1dp-Trennlinien, Fokus |
| `TextPrimary` / `Secondary` / `Muted` | `#E6EDF3` / `#8B949E` / `#7D8590` | Text, Labels, Platzhalter |
| `Accent` / `Hover` / `Pressed` | `#1F6FEB` / `#388BFD` / `#1158C7` | **einziger Farbton**: Primäraktion, Links, aktiver Zustand, Fokus |
| `OnAccent` / `AccentSubtle` / `Selection` | `#FFFFFF` / `#1F6FEB22` / `#264F78` | Text auf Akzent, dezente Fläche, Auswahl |

- Keine weiteren Farbtöne: Fehler/destruktiv über Text, Icon, kräftigeren Rahmen und Bestätigungsdialog (kein Rot).
- Muster: 1dp-Rahmen statt Schatten, Radien 8/12/16 dp, Touch-Ziele ≥ 48 dp, Listenzeilen 56–64 dp (Avatar 24 dp, Titel 14 dp, Subline 12 dp `TextMuted` in Monospace, rechts Status-Chip + Chevron), Buttons als Pills (40 dp; Primär = Akzent gefüllt, Sekundär = `Surface` + 1dp Rahmen), Status-Chip 24 dp (`SurfaceRaised`, 6-dp-Punkt in `Accent`/`BorderStrong`/`TextMuted`), Karten `Surface` + 1dp `Border` + 12 dp Radius, Fortschritt als 2-dp-`Accent`-Linie unter der TopAppBar, Icons monochrom (Material Symbols, `tint=TextPrimary`), App-Icon adaptive mit hellem Glyphen auf `#0D1117` + `monochrome`-Layer.

### 5.2 Devcontainer (Standard / Android) und Merge-Regel

Feature-Optionen (verbindlich):
```json
"ghcr.io/coder/devcontainer-features/code-server:2": {
  "version": "4.141.0", "auth": "none", "host": "0.0.0.0", "port": "13337",
  "workspace": "/workspaces/${localWorkspaceFolderBasename}",
  "disableTelemetry": true, "disableUpdateCheck": true, "disableWorkspaceTrust": true,
  "extensions": "kilocode.kilo-code,saoudrizwan.claude-dev,GitHub.github-vscode-theme",
  "trustedOrigins": "https://${containerEnv:CODESPACE_NAME}-13337.app.github.dev"
}
```
- *Standard*: image `mcr.microsoft.com/devcontainers/base:ubuntu-24.04` + Features code-server, `devcontainers/features/git:1`, `devcontainers/features/github-cli:1`; `forwardPorts:[13337]`; `portsAttributes:{"13337":{label:"Editor",onAutoForward:"silent",protocol:"http"}}`; `onCreateCommand: "bash .devcontainer/setup-code-server.sh"`.
- *Android*: Standard + `devcontainers/features/java:1` (`version:"17"`) + `postCreateCommand: "bash .devcontainer/setup-android.sh"` (cmdline-tools, `yes | sdkmanager --licenses`, `platform-tools`, `platforms;android-35`, `build-tools;35.0.0`; `ANDROID_HOME=/opt/android-sdk` in `containerEnv`, PATH in `~/.bashrc`).

**Merge-Regel** (Repo hat eigenes devcontainer → App erweitert es auf `codespace/editor`):
- `features`: code-server-Feature ergänzen; falls vorhanden → nur `auth/host/port/workspace/disable*/trustedOrigins` überschreiben, `version` des Users unangetastet.
- `forwardPorts`: `13337` ergänzen (Duplikate raus). `portsAttributes["13337"]`: ergänzen, Fremdeinträge bleiben.
- `onCreateCommand`: **nicht überschreiben** → eigenes Skript vorne einhängen (String → Array, Array → Element vorne).
- `image`, fremde `features`, `customizations`, `mounts`, `env`, `ports` bleiben unverändert; Ergebnis vor dem Schreiben validieren (JSON parsebar) und **Diff im Bestätigungsdialog** anzeigen; Original bleibt im Default-Branch (Merge per PR möglich).
- Codespace-Start: `ref: "codespace/editor"` + `devcontainer_path` = Pfad der (ggf. erweiterten) Datei.

### 5.3 Erweiterungen (Kilo Code, Cline, Theme)

| Erweiterung | ID | Version (Stand 2026-10) | Engine-Anforderung |
|---|---|---|---|
| Kilo Code | `kilocode.kilo-code` (Marketplace-ID `kilocode.Kilo-Code`) | 7.8.15 (universal + `linux-x64`) | VS Code ≥ 1.105.1 |
| Cline | `saoudrizwan.claude-dev` | 4.1.25 (universal) | VS Code ≥ 1.101.0 |
| GitHub Theme („GitHub Dark Default") | `GitHub.github-vscode-theme` | 6.3.5 (universal) | VS Code ≥ 1.43.0 |

- Alle drei auf **Open VSX** (= code-server-Standardgallery) verifiziert; code-server 4.141.0 (VS Code 1.141) erfüllt alle Engine-Anforderungen.
- **Zwei Installationswege**: (a) Build-Zeit über die Feature-Option `extensions` → in neuen Codespaces sofort vorhanden (1–3 min extra Build-Zeit). (b) **Container-Start** über einen gepatchten Entrypoint → deckt **wiederverwendete Codespaces** ab (Container-Dateisystem bleibt erhalten) und Rebuilds. Die Installation läuft **synchron vor** dem code-server-Start → der Port öffnet erst, wenn die Extensions fertig sind („direkt nutzbar").
- **Fallback ohne Gallery**: Die App löst URLs per Open-VSX-API auf und schreibt sie nach `.devcontainer/extensions.lock.json` (ID → Version + `.vsix`-URL), z. B. `https://open-vsx.org/api/saoudrizwan/claude-dev/4.1.25/file/saoudrizwan.claude-dev-4.1.25.vsix`, `https://open-vsx.org/api/GitHub/github-vscode-theme/6.3.5/file/GitHub.github-vscode-theme-6.3.5.vsix`, `https://open-vsx.org/api/kilocode/kilo-code/linux-x64/7.8.15/file/kilocode.kilo-code-7.8.15@linux-x64.vsix` → `curl` + `code-server --install-extension <datei>`. Schlägt auch das fehl, startet code-server trotzdem (`|| true`) und die App meldet „Erweiterungen unvollständig".
- **Supply-Chain-Schutz**: Der Ensure-Schritt liest **ausschließlich** die root-owned Liste `/usr/local/share/codespace-manager/extensions.txt` außerhalb des Workspace; `.devcontainer/extensions.txt` im Repo ist rein dokumentarisch → ein fremdes Repo kann keine Extensions einschleusen (der Port ist zeitweise anonym erreichbar).
- **Autorisierung**: Die Extensions sind installiert, aber nicht autorisiert. Beim ersten Öffnen zeigt die App **einmalig** „Kilo Code & Cline sind bereit – trage deinen Provider-Key (Anthropic/OpenRouter/Kilo-Konto) in der Erweiterung ein". Key liegt danach im SecretStorage des Codespaces und überlebt Neustarts; in neuen Codespaces erneut nötig.

**`.devcontainer/setup-code-server.sh`** (läuft als `onCreateCommand` beim Image-Build, also **vor** dem code-server-Start):
```bash
#!/usr/bin/env bash
set -euo pipefail
USER_HOME="${_REMOTE_USER_HOME:-$HOME}"
# 1) Theme + Origin-Check
mkdir -p "$USER_HOME/.local/share/code-server/User" "$USER_HOME/.config/code-server"
cat > "$USER_HOME/.local/share/code-server/User/settings.json" <<'JSON'
{
  "workbench.colorTheme": "GitHub Dark Default",
  "workbench.startupEditor": "none",
  "workbench.list.smoothScrolling": false,
  "editor.minimap.enabled": false,
  "editor.fontSize": 14,
  "terminal.integrated.fontSize": 13,
  "terminal.integrated.defaultProfile.linux": "bash",
  "files.autoSave": "afterDelay",
  "files.autoSaveDelay": 1000,
  "scm.diffDecorations": "gutter",
  "explorer.autoReveal": false
}
JSON
if [ -n "${CODESPACE_NAME:-}" ]; then
  cat > "$USER_HOME/.config/code-server/config.yaml" <<YAML
bind-addr: 0.0.0.0:13337
auth: none
cert: false
trusted-origins:
  - https://${CODESPACE_NAME}-13337.app.github.dev
YAML
fi
# 2) Extension-Liste ausserhalb des Workspace hinterlegen (root-owned)
install -m 0755 /dev/stdin /usr/local/bin/ensure-extensions.sh <<'EOF'
#!/usr/bin/env bash
# Installiert die festgelegten Erweiterungen (Liste liegt ausserhalb des Workspace).
# Reihenfolge: Gallery -> .vsix aus Lock-Datei -> Hinweis (kein Abbruch).
set -uo pipefail
DIR="/usr/local/share/codespace-manager"; LIST="$DIR/extensions.txt"; LOCK="$DIR/extensions.lock.json"
has_ext() { code-server --list-extensions 2>/dev/null | tr '[:upper:]' '[:lower:]' | grep -qx "$1"; }
install_from_lock() {
  local id="$1" url file
  [ -f "$LOCK" ] || return 1
  url="$(jq -r --arg id "$id" '.[$id] // empty' "$LOCK" 2>/dev/null | head -n1)"; [ -n "$url" ] || return 1
  file="/tmp/$(echo "$id" | tr '/' '-').vsix"; curl -fsSL "$url" -o "$file" || return 1
  code-server --install-extension "$file"
}
[ -f "$LIST" ] || { echo "[ensure-extensions] keine Liste" >&2; exit 0; }
while read -r ext; do
  ext="$(echo "$ext" | tr '[:upper:]' '[:lower:]' | tr -d '\r')"; [ -n "$ext" ] || continue
  has_ext "$ext" && continue
  echo "[ensure-extensions] installing $ext"
  code-server --install-extension "$ext" || install_from_lock "$ext" \
    || echo "[ensure-extensions] WARN: $ext nicht installierbar" >&2
done < "$LIST"
exit 0
EOF
mkdir -p /usr/local/share/codespace-manager
cp /workspaces/*/.devcontainer/extensions.txt /usr/local/share/codespace-manager/extensions.txt 2>/dev/null \
  || printf 'kilocode.kilo-code\nsaoudrizwan.claude-dev\nGitHub.github-vscode-theme\n' > /usr/local/share/codespace-manager/extensions.txt
cp /workspaces/*/.devcontainer/extensions.lock.json /usr/local/share/codespace-manager/extensions.lock.json 2>/dev/null || true
chmod 0644 /usr/local/share/codespace-manager/extensions.txt
# 3) Entrypoint wrappen: Ensure läuft vor dem code-server-Start, als $_REMOTE_USER
if [ -f /usr/local/bin/code-server-entrypoint ] && [ ! -f /usr/local/bin/code-server-entrypoint.real ]; then
  mv /usr/local/bin/code-server-entrypoint /usr/local/bin/code-server-entrypoint.real
  cat > /usr/local/bin/code-server-entrypoint <<'EOF'
#!/usr/bin/env bash
set -uo pipefail
if [ "$(id -u)" = "0" ] && [ -n "${_REMOTE_USER:-}" ] && [ "$(id -un)" != "$_REMOTE_USER" ]; then
  exec su "$_REMOTE_USER" -c /usr/local/bin/code-server-entrypoint
fi
/usr/local/bin/ensure-extensions.sh || echo "[codespace-manager] Erweiterungs-Installation unvollständig" >&2
exec /usr/local/bin/code-server-entrypoint.real
EOF
  chmod +x /usr/local/bin/code-server-entrypoint
fi
```

## 6. Aufgabenliste

**Phase 0 – Spike (entscheidet Port-Strategie, Pfadvariante, Extension-IDs)**
1. OAuth App anlegen (User): Callback `com.codespacemanager.app://oauth/callback`; ID/Secret als CI-Secrets `GITHUB_CLIENT_ID`/`GITHUB_CLIENT_SECRET`.
2. Per `curl` belegen: PKCE-Token → `POST /user/repos` → devcontainer.json via Contents-API → `POST /user/codespaces` → pollen → `GET /user/codespaces/{name}?internal=true&refresh=true` → `PUT {serviceUri}[/api/v1]/tunnels/{id}/ports/13337` (anonymous ACE) → `GET https://<name>-13337.app.github.dev/` → **200 + code-server-HTML?** → danach `PUT` mit deny-org-ACE → URL wieder **nicht** öffentlich.
3. Im Codespace prüfen: Datei öffnen/bearbeiten, **Terminal-Befehl ausführen** (WebSocket + Origin-Check!), `git status`/`git push` (Credential-Helper der Codespaces-Umgebung).
4. Erweiterungen prüfen: neu erstellter Codespace → Kilo Code, Cline, Theme installiert, Theme „GitHub Dark Default" aktiv; danach Codespace **stoppen und neu starten** (Wiederverwendungsfall) → Ensure installiert nach, Port öffnet erst danach. Ergebnis in `docs/spike-codespace-port.md` festhalten (Pfadvariante, ACE, trusted-origins, Extension-ID-Schreibweise, Installationsdauer).

**Phase 1 – Projekt & CI**
5. Gradle-Projekt (`settings.gradle.kts`, Root-`build.gradle.kts`, `app/`, `gradle/libs.versions.toml`, `gradle.properties`, `gradlew` + `gradle/wrapper/*`), Manifest (Intent-Filter `com.codespacemanager.app://oauth/callback`, `launchMode="singleTask"`), `res/` (adaptives Icon inkl. `monochrome`-Layer, Theme, deutsche Strings), **Design-System** `ui/theme/{Color,Theme,Type,Shape}.kt` mit zentralem `AppTheme`.
6. `keystore/debug.keystore` erzeugen (`keytool -genkeypair … -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -dname "CN=Android Debug,O=Android,C=US"`) → in `buildTypes.debug.signingConfig` verdrahten (identische Signatur über alle Builds).
7. `.github/workflows/android.yml`: Trigger `push`, `pull_request`, `workflow_dispatch`; `checkout` → `setup-java` (17) → `setup-gradle` (Cache, `gradle-version: 8.11.1`) → optional `gradle wrapper` falls `gradlew` fehlt → `./gradlew --no-daemon test lintDebug assembleDebug` → `actions/upload-artifact` (`codespace-manager-<sha>`, 14 Tage). `GITHUB_CLIENT_ID`/`GITHUB_CLIENT_SECRET` → Gradle-Properties → `BuildConfig`.
8. `README.md`: Setup (OAuth App, Secrets, Sideload aufs Moto G84, „Unbekannte Apps"), Nutzung, Sicherheitshinweis „Port öffentlich", bekannte Grenzen.

**Phase 2 – App-Kern**
9. `TokenStore` (EncryptedSharedPreferences + Keystore), `AccountRepository`, Room-Schema + Migrationen, Settings-DataStore.
10. Retrofit-Services `AuthApi`, `RepoApi`, `ContentsApi`, `GitApi`, `CodespaceApi`; eigener `DevTunnelClient` (OkHttp, `Authorization: Tunnel …`, `api-version`, Pfadvarianten-Fallback).
11. PKCE-Helfer (Verifier via `SecureRandom`, Challenge = Base64URL(SHA-256) ohne Padding), Custom-Tabs-OAuth-Flow (`prompt=select_account`, optionales `login=<user>`), State-/Code-Prüfung, Token-Tausch, `GET /user` → Login/Avatar.
12. Multi-Account-UI inkl. Entfernen des Tokens, Re-Auth bei 401.

**Phase 3 – Repos & Codespaces**
13. Repo-Liste + Suche + Leer-/Fehlerzustände; „Neues Repo"-Dialog (Namensvalidierung, Sichtbarkeit, **Owner: Konto/Org**, Template) + Startdateien: `.devcontainer/devcontainer.json`, `.devcontainer/setup-code-server.sh`, `.devcontainer/extensions.txt`, `.devcontainer/extensions.lock.json` (per Open-VSX-API aufgelöst), `README.md`, `.gitignore`.
14. Repo-Liste paginiert laden (`Link`-Header).
15. `PrepareRepoUseCase`: devcontainer-Liste prüfen → ohne devcontainer: Standard-Template; **mit** devcontainer: JSON-Merge (§5.2) → Branch `codespace/editor` anlegen + Datei schreiben (Bestätigungsdialog mit Diff).
16. Start-Dialog: Maschine (`machines`-Endpoint), Region, Idle-Timeout, Ref; Defaults in Settings.
17. `StartCodespaceUseCase` (§4 State-Machine) inkl. Polling, Abbruch, deutschen Fehlertexten, Kontingent-Hinweis, Wiederverwendung, Edge Case „fremdes devcontainer" + Read-only-Hinweis.
18. `PortManager`: Port anlegen/ACE setzen (public/private), Status lesen, Rücksetzen, 409/412-Toleranz, Pfadvarianten-Fallback.
19. Kontoweite Codespaces-Liste mit Status/Maschine/„zuletzt genutzt"/Git-Status, Aktionen öffnen/stoppen/löschen/umbenennen, **Kontingent-Schätzung** (Σ Kerne × Stunden seit `last_used_at`, als Schätzung markiert + Link auf github.com/settings/codespaces).
20. Fehlerbehandlung nach Tabelle in §7 (403/401/422/502) inkl. Custom-Tab-Freigabe und „Neu anlegen"-Angebot.

**Phase 4 – Editor**
21. `EditorScreen`: WebView + Topbar + Allowlist/Header-Injektion, Zurück → WebView-History, `onPause`/Timer → Port privat (Timer durch Interaktionen zurücksetzen), **Recovery-Overlay** („Port wieder öffnen" / „Codespace neu starten"), `onResume`-Statusprüfung, Tastatur-Toggle.
22. Custom-Tab-Fallback (`https://github.com/codespaces/<name>`) überall sichtbar (auch während des Starts).
23. Einmaliger Bottom-Sheet beim ersten Öffnen: „Kilo Code & Cline sind bereit – trage deinen Provider-Key in der Erweiterung ein" (die App speichert keine Keys).

**Phase 5 – Feinschliff, Tests, Doku**
24. Theme (dunkel), Edge-to-Edge, 20:9-Listenlayouts, Bottom-Sheets, 48dp-Touch-Ziele, Akku-schonendes Polling (kein Hintergrund-Polling, Backoff), Fehler-/Leerzustände, Hilfeseite.
25. Einstellungen: editierbare Erweiterungsliste (Default: Kilo Code, Cline, GitHub-Theme) → `.devcontainer/extensions.txt`; Open-VSX-Abfrage (ID → Version + `.vsix`-URL) → `extensions.lock.json`. Änderungen wirken bei neuen Codespaces bzw. nach Rebuild.
26. Unit-Tests: PKCE, Namensvalidierung, Template-Rendering (Snapshot), devcontainer-Merge, Browse-URL-Bau, State-Machine (MockWebServer), ACE-Payloads (public/private), Token-Verschlüsselung, Ensure-Skript-Rendering.
27. `docs/`: Spike-Ergebnis, API-Protokoll-Referenz, Setup-Anleitung.

## 7. Fehlerbehandlung

| Situation | Signal | Reaktion |
|---|---|---|
| Codespaces nicht freigeschaltet / kein Kontingent | 403 auf `POST /user/codespaces` bzw. `/codespaces/new` | Meldung „Für dieses Konto/Repo sind keine Codespaces verfügbar" + Link auf github.com/settings/codespaces |
| devcontainer verlangt extra Repo-Berechtigungen | 401 + `allow_permissions_url` | Custom Tab mit Freigabeseite, danach „Erneut versuchen" |
| Repo-Name vergeben | 422 auf `POST /user/repos` | Feldfehler am Namen |
| Port liefert 502/503 | HTTP-Status der Browse-URL | „code-server startet noch" → weiter pollen (max. 3 min) |
| Port antwortet nicht | Timeout | Fehler + „Devcontainer prüfen" + Browser-Fallback + „Neuen Codespace erstellen" |
| Tunnel-API nicht erreichbar / 401 | HTTP-Status | Port-Schritt als fehlgeschlagen melden, Browser-Fallback (Codespace selbst bleibt nutzbar) |
| WebView verliert Verbindung (Port privat/gestoppt) | `onReceivedError` | Recovery-Overlay: „Port wieder öffnen" / „Codespace neu starten" |
| Extension-Install schlägt fehl | Log in `ensure-extensions.sh` | Editor startet ohne Extension; App meldet „Erweiterungen unvollständig" |

## 8. Risiken

| # | Risiko | Gegenmaßnahme | Status |
|---|---|---|---|
| R1 | Öffentlicher Port per Org-Policy verboten (`allowed_port_privacy_settings` ohne `public`) | Vorab prüfen, Meldung „Port bleibt privat – im Browser arbeiten", Custom Tab als vollwertiger Ersatz | offen (Spike) |
| R2 | Dev-Tunnels-Pfadvariante (`/tunnels` vs `/api/v1/tunnels`) | Beide probieren, funktionierende Variante cachen | offen (Spike) |
| R3 | code-server liefert HTTPS | Ohne `cert` läuft code-server als reines HTTP → `protocol:"http"` | erledigt |
| R4 | Origin-Check blockiert WebSockets/Terminal | `trustedOrigins` mit `${containerEnv:CODESPACE_NAME}` + Fallback-Script; GitHub-Browse-URL statt Relay-URL | offen (Spike) |
| R5 | WebView-Performance auf Snapdragon 695 (Editor + 2 Agenten) | 2-Core-Maschine, Minimap aus, `startupEditor:none`, Autoscroll, kein Hintergrund-Polling, Browser-Fallback | offen |
| R6 | OAuth App nicht vom User angelegt | Setup-Screen in der App + CI-Secrets + Eingabefelder in Einstellungen | Abhängigkeit User |
| R7 | Kontingent (120 Core-Std./Monat) | Idle-Timeout 30 min, Stoppen anbieten, Verbrauchsschätzung anzeigen | umgesetzt |
| R8 | Öffentlicher Port = **anonyme Codeausführung** (code-server `auth:none`) | Port nur während der Nutzung öffentlich, Auto-Privatisieren nach 15 min, Status sichtbar, Hinweis „Nicht teilen" in Topbar/README | entschieden |
| R9 | `POST /user/codespaces` lehnt `geo` ab | Fallback `location` | offen (Spike) |
| R10 | code-server startet nicht (Feature-/Image-Build fehlgeschlagen) | Port antwortet nicht → Timeout mit Hinweis + Browser-Fallback + „Neu anlegen" | umgesetzt |
| R11 | System-WebView zu alt | min. WebView 90+, README-Hinweis „Android System WebView aktualisieren", Versionsprüfung beim Start | umgesetzt |
| R12 | Große Repos → lange Codespace-Starts | Fortschritts-Screen, Abbruch, Wiederverwendung statt Neuanlage | umgesetzt |
| R13 | Theme-Extension nicht installierbar | Degradationsfall: Default-Dark-Theme; App-UI unabhängig; `.vsix`-Fallback | umgesetzt |
| R14 | code-server zu alt für Engine-Anforderungen (Kilo ≥ 1.105.1, Cline ≥ 1.101.0) | code-server 4.141.0 (VS Code 1.141) pinnen, Spike prüft Aktivierung | umgesetzt |
| R15 | Open VSX im Container nicht erreichbar | `.vsix`-Fallback aus `extensions.lock.json`; Ensure endet mit `|| true`; App-Hinweis | umgesetzt |
| R16 | Extension-Installation verzögert den Editor (1–3 min, nur beim ersten Start) | Statuszeile „Erweiterungen werden vorbereitet"; Port öffnet erst danach; Ensure idempotent | umgesetzt |
| R17 | JSON-Merge erzeugt kaputte devcontainer-Datei | Merge nur über bekannte Felder, Validierung vor dem Schreiben, Diff im Dialog, Original im Default-Branch, Fallback „Original verwenden" | umgesetzt |
| R18 | Supply-Chain: fremdes Repo schleust Extensions ein | Ensure liest ausschließlich die root-owned Liste außerhalb des Workspace | umgesetzt |

## 9. Validierung

- **CI**: `./gradlew test lintDebug assembleDebug` grün, Lint ohne Error, Artifact vorhanden; Unit-Tests mit MockWebServer (Auth/Repo/Codespaces/Tunnel).
- **Abnahme Moto G84**: Sideload → Login mit 2 Konten → neues Repo (Standard + Android) → Codespace starten → Editor lädt → **Kilo Code und Cline in der Activity-Bar vorhanden und startklar (Key einmalig im Erweiterungs-UI eintragen)** → Datei bearbeiten → Terminal-Befehl (`ls`, `git status`) → Commit & Push (im GitHub-Repo prüfen) → Port-Status öffentlich/privat prüfen → App-Update per Sideload über bestehende Installation (Konten bleiben erhalten) → Codespace stoppen → Port wieder privat.
- **Wiederverwendung**: Codespace stoppen → App erneut öffnen → Editor startet ohne Neuinstallation (Ensure prüft nur) → Extensions weiterhin vorhanden.
- **Visuell**: nur Graustufen + #1F6FEB, keine Fremdfarben, Akzent nur für Primäraktionen/aktive Zustände, „GitHub Dark Default" im Editor aktiv, Performance, Navigationsleiste `#010409`, edge-to-edge ohne störenden Scrim.
- **Edge Cases**: Repo-Name doppelt (422), Codespace mit fremdem devcontainer (→ „Neu anlegen"), Repo mit eigenem devcontainer (Merge-Diff prüfen, Toolchain erhalten, Editor funktioniert), Kontenwechsel im Betrieb, App > 15 min im Hintergrund (Port privat), Codespace während der Sitzung gestoppt (Recovery-Overlay), Erweiterungsliste geändert.

## 10. Abhängigkeiten vom User

- **OAuth App einmalig selbst anlegen** (Settings → Developer settings → OAuth Apps → Callback `com.codespacemanager.app://oauth/callback`); ID/Secret in GitHub Secrets **und** optional in den App-Einstellungen.
- GitHub-Konto braucht Codespaces-Kontingent (Free-Privatkonto: 120 Core-Stunden/Monat inklusive; nach Erschöpfung blockiert GitHub ohne Zahlungsmethode).
- Erster Start dauert je nach Template 2–5 min (Image-Build) + 1–3 min Extensions → Fortschritts-Screen mit Statuszeilen und Abbruch.
- **Einmalig pro Codespace**: Provider-Key für Kilo Code/Cline im Erweiterungs-UI eintragen (Anthropic/OpenRouter/Kilo-Konto).

## 11. Out of Scope

Copilot im Editor, GitHub Enterprise Server (nur github.com), Multi-Repo-Editor, lokales Klonen/Pushen vom Gerät, Play-Store-Veröffentlichung, Hintergrund-Start von Codespaces, App-eigene Quota-Abrechnung, Light-Modus/Theme-Umschalter (dark-only), Farbtöne jenseits des GitHub-Blau-Akzents, automatisches Einspielen von Provider-Keys.
