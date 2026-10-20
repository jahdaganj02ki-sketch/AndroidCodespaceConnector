#!/usr/bin/env bash
# Codespace Manager – Android-Template: Android SDK installieren.
#
# Laeuft als postCreateCommand (nach dem Container-Start).
set -euo pipefail

export ANDROID_HOME=/opt/android-sdk
export ANDROID_SDK_ROOT=/opt/android-sdk
export PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}"

mkdir -p "${ANDROID_HOME}/cmdline-tools"

if [ ! -x "${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager" ]; then
  tmp="$(mktemp -d)"
  curl -fsSL -o "${tmp}/cmdline-tools.zip" \
    "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  unzip -q -o "${tmp}/cmdline-tools.zip" -d "${tmp}"
  rm -rf "${ANDROID_HOME}/cmdline-tools/latest"
  mv "${tmp}/cmdline-tools" "${ANDROID_HOME}/cmdline-tools/latest"
  rm -rf "${tmp}"
fi

yes | "${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager" --sdk_root="${ANDROID_HOME}" --licenses > /dev/null 2>&1 || true

"${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager" --sdk_root="${ANDROID_HOME}" \
  "platform-tools" \
  "platforms;android-35" \
  "build-tools;35.0.0"

# PATH dauerhaft fuer die Remote-User-Shell setzen
for rc in "${_REMOTE_USER_HOME:-$HOME}/.bashrc" "${_REMOTE_USER_HOME:-$HOME}/.profile"; do
  if [ -f "$rc" ] || [ "${rc##*/}" = ".bashrc" ]; then
    if ! grep -q "ANDROID_HOME" "$rc" 2>/dev/null; then
      cat >> "$rc" <<'ENV_EOF'

export ANDROID_HOME=/opt/android-sdk
export ANDROID_SDK_ROOT=/opt/android-sdk
export PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/emulator:${PATH}"
ENV_EOF
    fi
  fi
done

echo "[codespace-manager] Android SDK bereit"
