#!/usr/bin/env bash
set -euo pipefail

adjust_buffer() {
  local key="$1"
  local value="$2"
  if [[ -z "$value" ]]; then
    return
  fi
  local sys_path="/proc/sys/${key//./\/}"
  if [[ ! -e "$sys_path" ]]; then
    echo "[entrypoint] Warning: $sys_path not found; skipping $key adjustment." >&2
    return
  fi
  echo "[entrypoint] Setting $key to $value"
  if ! sysctl -w "$key=$value"; then
    echo "[entrypoint] Warning: unable to set $key. This may require elevated privileges." >&2
  fi
}

adjust_buffer "net.core.rmem_max" "${LINUX_RMEM_MAX:-}"
adjust_buffer "net.core.wmem_max" "${LINUX_WMEM_MAX:-}"

CMD=("java")
if [[ -n "${JAVA_OPTS:-}" ]]; then
  # shellcheck disable=SC2206
  CMD+=( ${JAVA_OPTS} )
fi
CMD+=("-jar" "/app/app.jar")

echo "[entrypoint] Launching: ${CMD[*]}"
exec "${CMD[@]}"
