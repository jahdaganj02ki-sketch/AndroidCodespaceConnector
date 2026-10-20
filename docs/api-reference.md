# API-Protokoll-Referenz

Alle Angaben sind gegen die GitHub-Dokumentation, den `gh`-CLI-Quellcode und die
Dev-Tunnels-SDKs geprüft (Stand 2026-10). Quellen:

* `cli/cli` → `internal/codespaces/connection/connection.go` (Tunnel-Credentials)
* `cli/cli` → `internal/codespaces/portforwarder/port_forwarder.go` (ACE-Semantik)
* `microsoft/dev-tunnels` → `tunnels/manager.go`, `tunnel_port.go`, `tunnel_access_control*.go`
* `coder/devcontainer-features` → `src/code-server/install.sh` (Feature-Verhalten)
* `coder/code-server` → `src/node/routes/index.ts` (keine `/api/v1`-REST-API)

## 1. OAuth

```
GET https://github.com/login/oauth/authorize
    ?client_id=…
    &redirect_uri=com.codespacemanager.app://oauth/callback
    &scope=repo%20codespace%20read:org
    &state=…
    &code_challenge=…&code_challenge_method=S256
    &prompt=select_account
```

```
POST https://github.com/login/oauth/access_token
Accept: application/json
Body: client_id, client_secret, code, redirect_uri, code_verifier
```

* PKCE (S256) wird von GitHub seit 07/2025 empfohlen und für native Apps unterstützt.
* Custom-Schemen-Redirects sind erlaubt → GitHub blendet automatisch den Account-Picker ein.
* Scopes: `repo` (Repos anlegen), `codespace` (Pflicht), `read:org` (Org-Repos).
* OAuth-Tokens laufen nicht ab.

**Header für alle REST-Calls**

```
Authorization: Bearer <token>
Accept: application/vnd.github+json
X-GitHub-Api-Version: 2026-03-10
```

Paginierung über den `Link`-Header (`per_page=100`), z. B.

```
Link: <https://api.github.com/user/repos?page=2>; rel="next", <…page=9>; rel="last"
```

## 2. Codespaces (`https://api.github.com`)

| Zweck | Aufruf |
|---|---|
| Anlegen | `POST /user/codespaces` |
| Lesen | `GET /user/codespaces/{name}` |
| Tunnel-Credentials | `GET /user/codespaces/{name}?internal=true&refresh=true` |
| Start / Stop | `POST /user/codespaces/{name}/start` \| `/stop` |
| Umbenennen | `PATCH /user/codespaces/{name}` (`display_name`) |
| Löschen | `DELETE /user/codespaces/{name}` |
| Liste (kontoweit) | `GET /user/codespaces` |
| Liste (Repo) | `GET /repos/{owner}/{repo}/codespaces` |
| Devcontainer-Varianten | `GET /repositories/{repoId}/codespaces/devcontainers?ref=…` |
| Maschinen | `GET /repositories/{repoId}/codespaces/machines?location=…&ref=…&devcontainer_path=…` |
| Berechtigungen | `GET /repositories/{repoId}/codespaces/permissions_check?ref=…&devcontainer_path=…` |

**Anlegen**

```json
{
  "repository_id": 123,
  "ref": "codespace/editor",
  "machine": "basicLinux32gb",
  "geo": "EuropeWest",
  "idle_timeout_minutes": 30,
  "devcontainer_path": ".devcontainer/devcontainer.json",
  "display_name": "demo (App)",
  "multi_repo_permissions_opt_out": false
}
```

* `location` wird von GitHub abgeschaltet, `geo` ist der aktuelle Parameter. Die App sendet `geo`
  und wiederholt den Aufruf bei HTTP 400 mit `location`.
* HTTP 201/202 = ok, 401 + `allow_permissions_url` = devcontainer-Freigabe nötig,
  403 = kein Codespace-Kontingent.

**Antwortfelder (Codespace)**

```
state, machine, location, web_url, devcontainer_path, git_status{ref,ahead,behind,…},
billable_owner, last_used_at, created_at, runtime_constraints.allowed_port_privacy_settings
```

**Tunnel-Credentials** – nur mit `internal=true&refresh=true`:

```json
{
  "connection": {
    "tunnelProperties": {
      "serviceUri": "https://use.rel.tunnels.api.visualstudio.com/",
      "tunnelId": "…",
      "clusterId": "…",
      "domain": "…",
      "connectAccessToken": "…",
      "managePortsAccessToken": "…"
    }
  }
}
```

## 3. Dev Tunnels (nicht `api.github.com`)

Basis: `serviceUri` aus `tunnelProperties`. Die API liegt je nach SDK-Version unter

```
{serviceUri}/tunnels/{tunnelId}/ports/{port}
{serviceUri}/api/v1/tunnels/{tunnelId}/ports/{port}
```

Die App probiert beide Varianten und merkt sich die funktionierende in den Einstellungen
(`tunnelApiPathVariant`).

```
Authorization: Tunnel <managePortsAccessToken>
Content-Type: application/json;charset=UTF-8
api-version=2023-09-27-preview
```

**Port setzen** – `PUT` (nicht POST!)

```
If-None-Match: *   → anlegen
If-Match: *        → aktualisieren
```

409/412 werden toleriert (Port existiert bereits).

```json
{
  "portNumber": 13337,
  "protocol": "http",
  "labels": ["UserForwardedPort"],
  "accessControl": {
    "entries": [
      { "type": "anonymous", "subjects": [], "scopes": ["connect"] }
    ]
  }
}
```

* **öffentlich** = `{ "type": "anonymous", "subjects": [], "scopes": ["connect"] }`
* **privat** = `{ "type": "organization", "subjects": ["1"], "scopes": ["connect"],
  "provider": "github", "isDeny": true }` (Default des `gh`-CLI)

**Browse-URL**: `https://<codespace-name>-<port>.app.github.dev` – öffentliche Ports brauchen
keine Authentifizierung. Der `gh`-CLI nutzt dieselbe Konstruktion.

**Header im WebView**

| Header | Wirkung |
|---|---|
| `X-Tunnel-Skip-AntiPhishing-Page: true` | unterdrückt die Interstitial-Seite des Tunnels |
| `X-Tunnel-Authorization: tunnel <connectAccessToken>` | Zugriff auf private Ports (nur HTTP!) |

WebSockets (Terminal) können keine Header setzen → für den Terminalbetrieb ist der öffentliche
Port der Hauptweg.

## 4. code-server / Devcontainer

* Feature: `ghcr.io/coder/devcontainer-features/code-server:2`
* Ohne `cert`/`certHost`/`certKey` läuft code-server als **reines HTTP** → Tunnel-`protocol:"http"`.
* `host` muss `0.0.0.0` sein, `auth:"none"` schaltet die Anmeldung ab.
* Das Feature erzeugt `/usr/local/bin/code-server-entrypoint` (Foreground-Prozess des Containers).
  Der Entrypoint wechselt selbst per `su $_REMOTE_USER` in den Remote-User.
* code-server **4.141.0 = VS Code 1.141.0**; die Gallery ist **Open VSX** (nicht der
  MS-Marketplace). Eine `/api/v1`-REST-API existiert in aktuellen Releases **nicht** → Extensions
  werden ausschließlich per CLI/Entrypoint installiert.
* Nutzer-Einstellungen: `~/.local/share/code-server/User/settings.json`
* Server-Config: `~/.config/code-server/config.yaml`
* Extension-Storage: `~/.local/share/code-server/extensions`

**Origin-Check**: code-server lehnt Requests ab, deren `Origin` nicht zum `Host` passt. Dev-Tunnel-
Relays schreiben den HTTP-Host auf `localhost` um → der Editor wird über die GitHub-Browse-URL
geladen, zusätzlich wird `trusted-origins` in `config.yaml` gesetzt (zur Laufzeit, mit dem echten
`CODESPACE_NAME`).

**Versionsanforderungen der Erweiterungen**

| Erweiterung | ID | Version | Engine |
|---|---|---|---|
| Kilo Code | `kilocode.kilo-code` | 7.8.15 | VS Code ≥ 1.105.1 |
| Cline | `saoudrizwan.claude-dev` | 4.1.25 | VS Code ≥ 1.101.0 |
| GitHub Dark Default | `GitHub.github-vscode-theme` | 6.3.5 | VS Code ≥ 1.43.0 |

Alle drei liegen auf **Open VSX**; code-server 4.141.0 erfüllt alle Engine-Anforderungen.
„GitHub Dark Default“ ist **kein** VS-Code-Built-in, sondern kommt aus der Theme-Extension.

**Fallback-URLs (`.vsix`)**

```
https://open-vsx.org/api/saoudrizwan/claude-dev/4.1.25/file/saoudrizwan.claude-dev-4.1.25.vsix
https://open-vsx.org/api/GitHub/github-vscode-theme/6.3.5/file/GitHub.github-vscode-theme-6.3.5.vsix
https://open-vsx.org/api/kilocode/kilo-code/linux-x64/7.8.15/file/kilocode.kilo-code-7.8.15@linux-x64.vsix
```

`code-server --install-extension` akzeptiert nur eine ID oder eine Datei → der Fallback lädt die
`.vsix` per `curl` nach `/tmp` und installiert die Datei.

## 5. Kontingent

* Free-Privatkonto: **120 Core-Stunden/Monat** inklusive, 15 GB-Monat/Monat.
* Eine 2-Core-Maschine verbraucht 2 Core-Stunden pro Stunde.
* Die App schätzt den Verbrauch aus `machine` und `last_used_at` (Σ Kerne × Stunden) und weist ihn
  ausdrücklich als **Schätzung** aus; der Link führt zu
  <https://github.com/settings/codespaces>.
