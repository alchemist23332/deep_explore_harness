#!/usr/bin/env bash

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export DEEP_EXPLORE_RUNTIME_HOME="$PROJECT_ROOT/.rt"
export DOCKER_CONFIG="$DEEP_EXPLORE_RUNTIME_HOME/.docker"
export DOCKER_HOST="unix://$DEEP_EXPLORE_RUNTIME_HOME/.colima/default/docker.sock"
export SANDBOX_DOCKER_HOST="tcp://127.0.0.1:23750"
export DEEP_EXPLORE_RUNTIME_TMP="$DEEP_EXPLORE_RUNTIME_HOME/tmp"

mkdir -p "$DOCKER_CONFIG" "$DEEP_EXPLORE_RUNTIME_TMP"

if [[ ! -f "$DOCKER_CONFIG/config.json" ]]; then
  cat > "$DOCKER_CONFIG/config.json" <<'JSON'
{
  "cliPluginsExtraDirs": [
    "/opt/homebrew/lib/docker/cli-plugins"
  ]
}
JSON
fi
