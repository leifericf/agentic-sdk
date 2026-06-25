#!/bin/sh
# PreToolUse hook: gate land operations on a green lane run this session.
# Matcher: Bash. Matches the land-shaped commands: git push, jj git push,
# jj bookmark move/set onto trunk, git merge into trunk. Denies unless a
# green marker is present: the spine working dir's lanes-green file, or a
# recorded VERDICT: PASS in the transcript. Fails soft on missing jq or
# malformed input (allows).
set -eu

INPUT=$(cat)

if ! command -v jq >/dev/null 2>&1; then
    echo "require-tests-before-land: jq not found on PATH; allowing" >&2
    exit 0
fi

CMD=$(printf '%s' "$INPUT" | jq -r '.tool_input.command // empty' 2>/dev/null) || true
[ -z "$CMD" ] && exit 0

is_land () {
    case "$1" in
        *"git push"*) return 0 ;;
        *"jj git push"*) return 0 ;;
        *"jj bookmark move main"*|*"jj bookmark set main"*) return 0 ;;
        *"git merge"*) return 0 ;;
    esac
    return 1
}

is_land "$CMD" || exit 0

WORK_DIR="${SPINE_WORK_DIR:-.spine}"
TRANSCRIPT=$(printf '%s' "$INPUT" | jq -r '.transcript_path // empty' 2>/dev/null) || true

green () {
    [ -f "$WORK_DIR/lanes-green" ] && return 0
    repo_dir=".$(basename "$PWD")"
    [ -f "$repo_dir/lanes-green" ] && return 0
    if [ -n "$TRANSCRIPT" ] && [ -f "$TRANSCRIPT" ]; then
        grep -qE 'VERDICT: PASS' "$TRANSCRIPT" 2>/dev/null && return 0
    fi
    return 1
}

if green; then
    exit 0
fi

REASON="require-tests-before-land: no green lane marker this session. Run the pre-land lanes first; the verifier records VERDICT: PASS and writes $WORK_DIR/lanes-green."
printf '{"hookSpecificOutput":{"permissionDecision":"deny","permissionDecisionReason":%s}}\n' \
    "$(printf '%s' "$REASON" | jq -Rs .)"
exit 0
