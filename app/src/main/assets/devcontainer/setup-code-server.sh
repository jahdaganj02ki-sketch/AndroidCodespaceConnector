#!/usr/bin/env bash
# Codespace Manager – Einrichtung des code-server-Editors.
#
# Läuft als onCreateCommand (einmalig beim Erstellen des Containers) und
# installiert alles, was ausserhalb des Workspace liegen muss:
#   * root-owned Erweiterungsliste  -> /usr/local/share/codespace-manager/extensions.txt
#   * root-owned Lock-Datei          -> /usr/local/share/codespace-manager/extensions.lock.json
#   * ensure-extensions.sh           -> /usr/local/bin/ensure-extensions.sh
#   * codespace-manager-config.sh    -> /usr/local/bin/codespace-manager-config.sh
#   * Wrapper um /usr/local/bin/code-server-entrypoint
#
# WICHTIG: Der Ensure-Schritt liest ausschliesslich die root-owned Liste ausserhalb
# des Workspace. Die Datei .devcontainer/extensions.txt im Repo ist rein dokumentarisch
# und fliesst bewusst nicht in die Installation ein.
set -uo pipefail

STATE_DIR="/usr/local/share/codespace-manager"
EXTENSIONS_FILE="$STATE_DIR/extensions.txt"
LOCK_FILE="$STATE_DIR/extensions.lock.json"

mkdir -p "$STATE_DIR"

# 1) Erweiterungsliste (root-owned, ausserhalb des Workspace)
cat > "$EXTENSIONS_FILE" <<'EXTENSIONS_EOF'
@@EXTENSION_LIST@@
EXTENSIONS_EOF
chmod 0644 "$EXTENSIONS_FILE"

# 2) Optionale Lock-Datei (.vsix-Fallback) aus dem Repo uebernehmen
if [ -f ".devcontainer/extensions.lock.json" ]; then
  cp ".devcontainer/extensions.lock.json" "$LOCK_FILE" 2>/dev/null || true
  chmod 0644 "$LOCK_FILE" 2>/dev/null || true
fi

# 3) Konfigurations-Skript: schreibt Settings + code-server-Konfiguration
install -m 0755 /dev/stdin /usr/local/bin/codespace-manager-config.sh <<'CONFIG_EOF'
#!/usr/bin/env bash
# Schreibt code-server-Nutzereinstellungen (Theme) und die Konfiguration
# (bind-addr, auth, trusted-origins) fuer den Remote-User.
set -uo pipefail

USER_HOME="${_REMOTE_USER_HOME:-$HOME}"
mkdir -p "$USER_HOME/.local/share/code-server/User" "$USER_HOME/.config/code-server"

cat > "$USER_HOME/.local/share/code-server/User/settings.json" <<'SETTINGS_EOF'
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
SETTINGS_EOF

ORIGINS=""
if [ -n "${CODESPACE_NAME:-}" ]; then
  ORIGINS="https://${CODESPACE_NAME}-@@EDITOR_PORT@@.app.github.dev"
fi

{
  echo "bind-addr: 0.0.0.0:@@EDITOR_PORT@@"
  echo "auth: none"
  echo "cert: false"
  if [ -n "$ORIGINS" ]; then
    echo "trusted-origins:"
    echo "  - $ORIGINS"
  fi
} > "$USER_HOME/.config/code-server/config.yaml"

exit 0
CONFIG_EOF

# 4) Ensure-Skript: installiert fehlende Erweiterungen, bevor code-server startet
install -m 0755 /dev/stdin /usr/local/bin/ensure-extensions.sh <<'ENSURE_EOF'
#!/usr/bin/env bash
# Installiert die festgelegten Erweiterungen.
# Reihenfolge: Gallery (Open VSX) -> .vsix aus der Lock-Datei -> Hinweis (kein Abbruch).
# Quelle der Wahrheit ist ausschliesslich die root-owned Liste ausserhalb des Workspace.
set -uo pipefail

DIR="/usr/local/share/codespace-manager"
LIST="$DIR/extensions.txt"
LOCK="$DIR/extensions.lock.json"

has_ext() {
  code-server --list-extensions 2>/dev/null | tr '[:upper:]' '[:lower:]' | grep -qx "$1"
}

install_from_lock() {
  local id="$1" url file
  [ -f "$LOCK" ] || return 1
  url="$(jq -r --arg id "$id" '.[$id] // empty' "$LOCK" 2>/dev/null | head -n1)"
  [ -n "$url" ] || return 1
  file="/tmp/$(echo "$id" | tr '/' '-' | tr ':' '-').vsix"
  curl -fsSL "$url" -o "$file" || return 1
  code-server --install-extension "$file"
}

[ -f "$LIST" ] || { echo "[ensure-extensions] keine Liste" >&2; exit 0; }

while read -r ext; do
  ext="$(echo "$ext" | tr '[:upper:]' '[:lower:]' | tr -d '\r' | tr -d ' ')"
  [ -n "$ext" ] || continue
  case "$ext" in \#*) continue ;; esac
  if has_ext "$ext"; then
    continue
  fi
  echo "[ensure-extensions] installing $ext"
  code-server --install-extension "$ext" || install_from_lock "$ext" \
    || echo "[ensure-extensions] WARN: $ext nicht installierbar" >&2
done < "$LIST"

exit 0
ENSURE_EOF

# 5) Entrypoint wrappen: Ensure laeuft synchron vor dem code-server-Start
if [ -f /usr/local/bin/code-server-entrypoint ] && [ ! -f /usr/local/bin/code-server-entrypoint.real ]; then
  mv /usr/local/bin/code-server-entrypoint /usr/local/bin/code-server-entrypoint.real
  cat > /usr/local/bin/code-server-entrypoint <<'WRAPPER_EOF'
#!/usr/bin/env bash
set -uo pipefail

if [ "$(id -u)" = "0" ] && [ -n "${_REMOTE_USER:-}" ] && [ "$(id -un)" != "$_REMOTE_USER" ]; then
  exec su "$_REMOTE_USER" -c /usr/local/bin/code-server-entrypoint
fi

/usr/local/bin/codespace-manager-config.sh || echo "[codespace-manager] Konfiguration unvollstaendig" >&2
/usr/local/bin/ensure-extensions.sh || echo "[codespace-manager] Erweiterungs-Installation unvollstaendig" >&2

exec /usr/local/bin/code-server-entrypoint.real
WRAPPER_EOF
  chmod +x /usr/local/bin/code-server-entrypoint
fi

echo "[codespace-manager] code-server vorbereitet"
exit 0
