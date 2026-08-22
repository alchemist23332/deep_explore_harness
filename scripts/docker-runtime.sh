#!/usr/bin/env bash

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$PROJECT_ROOT/scripts/docker-env.sh"

COLIMA=(env
  "HOME=$DEEP_EXPLORE_RUNTIME_HOME"
  "TMPDIR=$DEEP_EXPLORE_RUNTIME_TMP"
  colima
)
BRIDGE_HTTP_URL="${SANDBOX_DOCKER_HOST/tcp:/http:}"

start_bridge() {
  local pid_file="$DEEP_EXPLORE_RUNTIME_HOME/docker-bridge.pid"
  if [[ -f "$pid_file" ]] && kill -0 "$(cat "$pid_file")" 2>/dev/null; then
    return
  fi
  nohup socat \
    TCP-LISTEN:23750,bind=127.0.0.1,reuseaddr,fork \
    "UNIX-CONNECT:${DOCKER_HOST#unix://}" \
    > "$DEEP_EXPLORE_RUNTIME_HOME/docker-bridge.log" 2>&1 &
  echo "$!" > "$pid_file"
}

stop_bridge() {
  local pid_file="$DEEP_EXPLORE_RUNTIME_HOME/docker-bridge.pid"
  if [[ -f "$pid_file" ]]; then
    kill "$(cat "$pid_file")" 2>/dev/null || true
    rm -f "$pid_file"
  fi
}

start_runtime() {
  "${COLIMA[@]}" start \
    --runtime docker \
    --vm-type vz \
    --arch aarch64 \
    --cpus 4 \
    --memory 8 \
    --disk 60 \
    --mount "$PROJECT_ROOT:w" \
    --ssh-config=false \
    --vz-rosetta=false

  start_bridge
  docker version
  docker compose version
  curl --fail --silent "${BRIDGE_HTTP_URL}/_ping"
  printf '\n'
}

case "${1:-start}" in
  start)
    start_runtime
    ;;
  stop)
    stop_bridge
    "${COLIMA[@]}" stop
    ;;
  status)
    "${COLIMA[@]}" status
    start_bridge
    docker version
    curl --fail --silent "${BRIDGE_HTTP_URL}/_ping"
    printf '\n'
    ;;
  reset)
    stop_bridge
    "${COLIMA[@]}" delete --force || true
    start_runtime
    ;;
  *)
    echo "Usage: $0 {start|stop|status|reset}" >&2
    exit 2
    ;;
esac
