#!/usr/bin/env bash
# JitPack builds the library only; no demo AAR or research model is needed.
set -euo pipefail
: "${ANDROID_HOME:?JitPack must provide the Android SDK}"
manager=$(command -v sdkmanager || true)
if [[ -z "$manager" ]]; then
  for candidate in "$ANDROID_HOME"/cmdline-tools/*/bin/sdkmanager "$ANDROID_HOME/tools/bin/sdkmanager"; do
    if [[ -x "$candidate" ]]; then
      manager="$candidate"
      break
    fi
  done
fi
if [[ -z "$manager" ]]; then
  echo 'Android SDK manager was not found.' >&2
  exit 1
fi
# sdkmanager can exit before yes, so inspect its status rather than yes's SIGPIPE.
set +o pipefail
yes | "$manager" --licenses >/dev/null
set -o pipefail
"$manager" 'platforms;android-37.0' 'build-tools;36.0.0'
