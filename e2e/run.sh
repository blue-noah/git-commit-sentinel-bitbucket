#!/usr/bin/env bash
# End-to-end tests against a real Bitbucket Data Center, in a pod (Atlassian's official image):
# start it, install the plugin like an admin would (REST), run e2e.sh, tear it down.
#
#   e2e/run.sh                          # Bitbucket 9.4.13, the version the plugin is built against
#   BITBUCKET_VERSION=9.4.26 e2e/run.sh # any other release
#   KEEP=1 e2e/run.sh                   # leave Bitbucket running: http://localhost:7990, admin/admin
#
# Needs podman, curl, git, and the plugin jar (mvn package).
set -euo pipefail
cd "$(dirname "$0")/.."

export BITBUCKET_VERSION=${BITBUCKET_VERSION:-9.4.13}
export BB_URL=http://localhost:7990
PLUGIN_KEY=com.github.bluenoah.git-commit-sentinel-bitbucket
JAR=$(find bitbucket-dc-9/target -maxdepth 1 -name 'git-commit-sentinel-bitbucket-dc-9-*.jar' | head -1)
POD=bitbucket-dc-9/target/e2e-pod.yaml
[ -n "$JAR" ] || { echo "plugin jar not found: run mvn package first" >&2; exit 1; }

sed "s|\${BITBUCKET_VERSION}|$BITBUCKET_VERSION|" e2e/bitbucket-pod.yaml > "$POD"
down() { podman kube down "$POD" >/dev/null 2>&1 || true; }
# On failure, show Bitbucket's log before the pod (and its log) goes away.
finish() {
    local rc=$?
    [ "$rc" -eq 0 ] || podman logs --tail 100 git-commit-sentinel-bitbucket-e2e-bitbucket 2>&1 || true
    [ -n "${KEEP:-}" ] || down
    return "$rc"
}
down
trap finish EXIT

echo "== starting Bitbucket $BITBUCKET_VERSION"
podman kube play --configmap e2e/timebomb-license.yaml "$POD" >/dev/null
for _ in $(seq 1 120); do
    status=$(curl -s "$BB_URL/status" || true)
    [[ $status == *RUNNING* ]] && break
    if [[ $status == *ERROR* ]]; then podman logs --tail 50 git-commit-sentinel-bitbucket-e2e-bitbucket; exit 1; fi
    sleep 5
done
[[ $status == *RUNNING* ]] || { echo "Bitbucket did not start: $status" >&2; exit 1; }
echo "git on the server: $(podman exec git-commit-sentinel-bitbucket-e2e-bitbucket git --version)"

echo "== installing $JAR"
token=$(curl -s -u admin:admin -D - -o /dev/null "$BB_URL/rest/plugins/1.0/?os_authType=basic" \
    | awk 'tolower($1) == "upm-token:" { print $2 }' | tr -d '\r')
curl -s -u admin:admin -o /dev/null -F "plugin=@$JAR" "$BB_URL/rest/plugins/1.0/?token=$token"
for _ in $(seq 1 60); do
    curl -s -u admin:admin "$BB_URL/rest/plugins/1.0/$PLUGIN_KEY-key" | grep -q '"enabled":true' && break
    sleep 2
done
curl -s -u admin:admin "$BB_URL/rest/plugins/1.0/$PLUGIN_KEY-key" | grep -q '"enabled":true' \
    || { echo "plugin not enabled" >&2; exit 1; }

echo "== end-to-end tests (client $(git --version))"
e2e/e2e.sh
