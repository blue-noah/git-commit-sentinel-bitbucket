#!/usr/bin/env bash
# End-to-end checks against a running Bitbucket (started by e2e/run.sh). Pushes with whatever `git` is on PATH.
set -u
BB=${BB_URL:-http://localhost:7990}
SCM=${BB/:\/\//://admin:admin@}/scm
AUTH=admin:admin
PLUGIN=com.github.bluenoah.git-commit-sentinel-bitbucket
HOOK=$PLUGIN:commit-sentinel-hook
WORK=$(mktemp -d)
PASS=0; FAIL=0
RUN=$(date +%H%M%S); P=T$RUN; P2=U$RUN   # fresh projects every run

ok()   { echo "PASS: $1"; PASS=$((PASS+1)); }
ko()   { echo "FAIL: $1"; FAIL=$((FAIL+1)); }
api()  { curl -s -u $AUTH -H 'Content-Type: application/json' "$@"; }

echo "== plugin state"
api "$BB/rest/plugins/1.0/$PLUGIN-key" | python3 -c 'import json,sys; d=json.load(sys.stdin); print("enabled:", d.get("enabled"), [(m["key"], m["enabled"]) for m in d.get("modules",[])])'

for p in $P $P2; do api -X POST "$BB/rest/api/1.0/projects" -d "{\"key\":\"$p\",\"name\":\"$p\"}" >/dev/null; done
for r in $P/demo $P/other $P2/free; do api -X POST "$BB/rest/api/1.0/projects/${r%/*}/repos" -d "{\"name\":\"${r#*/}\"}" >/dev/null; done

clone() {
  local dir=$WORK/${1//\//_}
  git clone -q "$SCM/$(echo $1 | tr A-Z a-z).git" "$dir" 2>/dev/null
  git -C "$dir" config user.email t@example.com; git -C "$dir" config user.name Tester
  git -C "$dir" commit -q --allow-empty -m "chore: init"
  git -C "$dir" push -q origin HEAD:main 2>/dev/null
  git -C "$dir" fetch -q origin
  echo "$dir"
}
push_msg() {
  git -C "$1" checkout -q -B "$2" origin/main
  # Unique body: two identical empty commits made in the same second would share a SHA, and the
  # second push would carry nothing new to the repository (so, correctly, nothing to check).
  git -C "$1" commit -q --allow-empty -m "$3" -m "e2e $2 $RANDOM$RANDOM"
  git -C "$1" push -f origin "HEAD:refs/heads/$2" 2>&1
}
expect() { # NAME WANT_EXIT(0|1) GREP_PATTERN OUTPUT EXIT
  local name=$1 want=$2 pat=$3 out=$4 code=$5
  [ "$code" -ne 0 ] && code=1
  if [ "$code" = "$want" ] && { [ -z "$pat" ] || grep -q -- "$pat" <<<"$out"; }; then ok "$name"; else ko "$name (exit=$code)"; echo "$out" | sed 's/^/    /'; fi
}

D=$(clone $P/demo); O=$(clone $P/other); F=$(clone $P2/free)

echo "== hook not enabled"
out=$(push_msg "$D" feature/a "wip: stuff"); expect "not enabled: anything goes" 0 "" "$out" $?

echo "== enabled on the project (built-in defaults), inherited by every repository"
api -X PUT "$BB/rest/api/1.0/projects/$P/settings/hooks/$HOOK/enabled" -d '{}' >/dev/null
out=$(push_msg "$D" feature/b "wip: stuff");       expect "error rejects push" 1 'error: \[type\]' "$out" $?
out=$(push_msg "$O" feature/b "wip: stuff");       expect "second repo inherits project hook" 1 'error: \[type\]' "$out" $?
out=$(push_msg "$D" feature/c "feat: add thing");  expect "valid message accepted" 0 "" "$out" $?
out=$(push_msg "$D" feature/d "fix: the bug.");    expect "warning shown, push accepted" 0 'warning: \[description-period\]' "$out" $?
out=$(push_msg "$D" bugfix/e "wip: stuff");        expect "non-feature branch ignored" 0 "" "$out" $?
out=$(push_msg "$F" feature/x "wip: stuff");       expect "other project unaffected" 0 "" "$out" $?
n=$(grep -c 'git-commit-sentinel: ' <<<"$(push_msg "$D" feature/once "fix: trailing.")")
[ "$n" = 2 ] && ok "each commit checked once" || ko "expected 2 sentinel lines, got $n"

echo "== merge commits are skipped"
git -C "$D" checkout -q -B side origin/main; git -C "$D" commit -q --allow-empty -m "chore: side"; git -C "$D" push -q origin side 2>/dev/null
git -C "$D" checkout -q -B feature/m origin/main; git -C "$D" commit -q --allow-empty -m "feat: m"
git -C "$D" merge -q --no-ff --no-edit side -m "Merge branch 'side' into feature/m"
out=$(git -C "$D" push -f origin HEAD:refs/heads/feature/m 2>&1); expect "merge commit skipped" 0 "" "$out" $?

echo "== project settings"
api -X PUT "$BB/rest/api/1.0/projects/$P/settings/hooks/$HOOK/settings" -d '{"types":"feat,task","rule-description-period":"error"}' >/dev/null
out=$(push_msg "$O" feature/f "task: do it");      expect "project custom types" 0 "" "$out" $?
out=$(push_msg "$O" feature/g "fix: x");           expect "project custom types reject others" 1 'error: \[type\]' "$out" $?
out=$(push_msg "$O" feature/h "feat: x.");         expect "project level escalated to error" 1 'error: \[description-period\]' "$out" $?

echo "== repository override"
api -X PUT "$BB/rest/api/1.0/projects/$P/repos/demo/settings/hooks/$HOOK/enabled" -d '{"branchPattern":"(feature|story)/.+","rule-type":"off"}' >/dev/null
out=$(push_msg "$D" feature/i "wip: stuff");       expect "repo override: type off" 0 "" "$out" $?
out=$(push_msg "$D" story/j "nope");               expect "repo override: custom pattern" 1 'error: \[format\]' "$out" $?
out=$(push_msg "$O" feature/k "wip: stuff");       expect "sibling repo keeps project settings" 1 'error: \[type\]' "$out" $?
api -X PUT "$BB/rest/api/1.0/projects/$P/repos/demo/settings/hooks/$HOOK/settings" -d '{"rule-header-length":"off"}' >/dev/null
out=$(push_msg "$D" feature/q "fix: allowed by built-in types"); expect "repo override does not inherit project fields (fix allowed)" 0 "" "$out" $?
api -X DELETE "$BB/rest/api/1.0/projects/$P/repos/demo/settings/hooks/$HOOK/enabled" >/dev/null
out=$(push_msg "$D" feature/l "wip: stuff");       expect "repo explicitly disabled" 0 "" "$out" $?
api -X DELETE "$BB/rest/api/1.0/projects/$P/repos/demo/settings/hooks/$HOOK" >/dev/null
out=$(push_msg "$D" feature/n "wip: stuff");       expect "repo back to inheriting the project" 1 'error: \[type\]' "$out" $?

echo "== bypass"
api -X PUT "$BB/rest/api/1.0/projects/$P/settings/hooks/$HOOK/settings" -d '{"bypassUsers":"ADMIN"}' >/dev/null
out=$(push_msg "$D" feature/o "nope");             expect "bypass user not checked" 0 'bypass' "$out" $?

echo "== settings validation (REST)"
body=$(api -w '\n%{http_code}' -X PUT "$BB/rest/api/1.0/projects/$P/settings/hooks/$HOOK/settings" -d '{"branchPattern":"feature/(","headerMaxLength":"x","rule-type":"fatal"}')
code=$(tail -1 <<<"$body")
[ "$code" = 400 ] && grep -q 'branchPattern' <<<"$body" && grep -q 'headerMaxLength' <<<"$body" && grep -q 'rule-type' <<<"$body" \
  && ok "invalid settings rejected (400, every field reported)" || { ko "invalid settings ($code)"; echo "$body" | head -5; }

echo
echo "PASSED $PASS, FAILED $FAIL"
[ "$FAIL" -eq 0 ]
