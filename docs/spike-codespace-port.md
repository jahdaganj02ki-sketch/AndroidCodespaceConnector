# Spike: Codespace-Port, Dev-Tunnel-Pfad, Erweiterungen

Dieses Dokument hält das Ergebnis des Phase-0-Spikes fest. Der Spike braucht ein **echtes
GitHub-Konto mit Codespaces-Kontingent** sowie die OAuth-Zugangsdaten – er kann daher nicht
automatisiert im CI laufen.

## Status

| # | Offene Frage | Stand | Ergebnis |
|---|---|---|---|
| S1 | Dev-Tunnel-Pfadvariante (`/tunnels` vs `/api/v1/tunnels`) | erledigt (Code) | Die App probiert beide Varianten, merkt sich die funktionierende in `tunnelApiPathVariant` (DataStore). |
| S2 | `geo` vs `location` bei `POST /user/codespaces` | erledigt (Code) | Die App sendet `geo` (`EuropeWest`, `UsEast`, `UsWest`, `SoutheastAsia`) und wiederholt bei HTTP 400 mit `location`. |
| S3 | Öffentlicher Port durch Org-Policy verboten? | offen (benötigt Nutzerkonto) | Die App liest `runtime_constraints.allowed_port_privacy_settings`; ohne `public` bleibt der Port privat, der Editor wird dann über das Codespaces-UI genutzt. |
| S4 | Origin-Check / WebSocket-Terminal über die Browse-URL | offen (benötigt Nutzerkonto) | Editor-URL ist `https://<name>-13337.app.github.dev`; `trusted-origins` wird zur Laufzeit in `~/.config/code-server/config.yaml` geschrieben. |
| S5 | Installationsdauer der Erweiterungen | offen (benötigt Nutzerkonto) | Wird im Start-Fortschritt als „Erweiterungen werden vorbereitet“ angezeigt (1–3 Min. beim ersten Start). |

## Manuelle Durchführung

Voraussetzungen: OAuth-App (Callback `com.codespacemanager.app://oauth/callback`), PAT mit den
Scopes `repo codespace read:org`, ein Konto mit Codespaces-Kontingent.

```bash
export GH_TOKEN=<personal access token>
export CLIENT_ID=<oauth client id>
export CLIENT_SECRET=<oauth client secret>
```

### 1. Token + Repo + devcontainer

```bash
# Token per PKCE (Device-Flow funktioniert nicht, der Redirect ist custom)
# Der einfachste Weg für den Spike: die App selbst nutzen oder
# https://github.com/login/oauth/authorize?client_id=$CLIENT_ID&
#   redirect_uri=com.codespacemanager.app://oauth/callback&scope=repo%20codespace%20read:org
#   &state=x&code_challenge=<sha256(base64url(verifier))>&code_challenge_method=S256

curl -s -X POST https://github.com/login/oauth/access_token \
  -H "Accept: application/json" \
  -d client_id="$CLIENT_ID" -d client_secret="$CLIENT_SECRET" \
  -d code="$CODE" -d redirect_uri=com.codespacemanager.app://oauth/callback \
  -d code_verifier="$VERIFIER"
```

```bash
curl -s -H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json" \
  -H "X-GitHub-Api-Version: 2026-03-10" https://api.github.com/user
```

Repo anlegen und devcontainer schreiben (Base64-kodiert über die Contents-API):

```bash
curl -s -X POST -H "Authorization: Bearer $TOKEN" https://api.github.com/user/repos \
  -d '{"name":"codespace-spike","private":true,"auto_init":true}'
```

### 2. Codespace erzeugen und pollen

```bash
curl -s -X POST -H "Authorization: Bearer $TOKEN" https://api.github.com/user/codespaces \
  -d '{"repository_id":'"$REPO_ID"',"ref":"main","machine":"basicLinux32gb","geo":"EuropeWest","idle_timeout_minutes":30}'

curl -s -H "Authorization: Bearer $TOKEN" \
  "https://api.github.com/user/codespaces/$NAME?internal=true&refresh=true"
```

Aus `connection.tunnelProperties` kommen `serviceUri` und `tunnelId`.

### 3. Port öffentlich schalten

```bash
TOKEN_TUNNEL=$MANAGE_PORTS_ACCESS_TOKEN
TUNNEL_ID=$TUNNEL_ID
BASE=$SERVICE_URI            # z. B. https://use.rel.tunnels.api.visualstudio.com/

curl -s -X PUT \
  -H "Authorization: Tunnel $TOKEN_TUNNEL" \
  -H "Content-Type: application/json;charset=UTF-8" \
  -H "If-None-Match: *" \
  "$BASE/tunnels/$TUNNEL_ID/ports/13337?api-version=2023-09-27-preview" \
  -d '{"portNumber":13337,"protocol":"http","labels":["UserForwardedPort"],"accessControl":{"entries":[{"type":"anonymous","subjects":[],"scopes":["connect"]}]}}'
```

Falls die Antwort 404 liefert, dieselbe Anfrage mit `/api/v1` zwischen `BASE` und `tunnels`
wiederholen → das ist die Antwort auf **S1**.

Danach prüfen:

```bash
curl -s -o /dev/null -w "%{http_code}\n" "https://$NAME-13337.app.github.dev/"
```

Erwartung: **200** mit code-server-HTML.

### 4. Port wieder privat schalten

```bash
curl -s -X PUT \
  -H "Authorization: Tunnel $TOKEN_TUNNEL" -H "Content-Type: application/json;charset=UTF-8" \
  -H "If-Match: *" \
  "$BASE/tunnels/$TUNNEL_ID/ports/13337?api-version=2023-09-27-preview" \
  -d '{"portNumber":13337,"protocol":"http","labels":["UserForwardedPort"],"accessControl":{"entries":[{"type":"organization","subjects":["1"],"scopes":["connect"],"provider":"github","isDeny":true}]}}'

curl -s -o /dev/null -w "%{http_code}\n" "https://$NAME-13337.app.github.dev/"
```

Erwartung: **kein** code-server mehr (404/502/403).

### 5. Im Codespace prüfen

* Datei öffnen und bearbeiten.
* **Terminal**: `ls`, `git status`, `git push` (Credential-Helper der Codespaces-Umgebung).
  → beantwortet **S4** (Origin-Check + WebSocket).
* Devcontainer prüfen: `cat ~/.config/code-server/config.yaml` (trusted-origins),
  `cat ~/.local/share/code-server/User/settings.json` (Theme).
* Erweiterungen: `code-server --list-extensions` → Kilo Code, Cline, GitHub-Theme.
* Wiederverwendungsfall: Codespace stoppen und neu starten → der Ensure-Schritt darf nichts
  neu installieren (`ensure-extensions.sh` ist idempotent).

### 6. Ergebnis eintragen

Die Beobachtungen bitte in die Tabelle oben und in `docs/api-reference.md` eintragen, damit die
App-Konfiguration (z. B. `tunnelApiPathVariant`) auf dem Gerät nicht mehr erraten werden muss.
