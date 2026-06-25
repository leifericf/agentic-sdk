#!/bin/sh
# PreToolUse hook: deny reads and writes of secret-bearing files.
# Matcher: Read|Edit|Write. Denies when the target basename matches a
# secret pattern; allows otherwise. Fails soft: malformed input or a
# missing jq lets the call through with a stderr warning.
set -eu

INPUT=$(cat)

if ! command -v jq >/dev/null 2>&1; then
    echo "deny-secrets: jq not found on PATH; allowing the call" >&2
    exit 0
fi

FILE_PATH=$(printf '%s' "$INPUT" | jq -r '.tool_input.file_path // empty' 2>/dev/null) || true
[ -z "$FILE_PATH" ] && exit 0

BASENAME=$(basename "$FILE_PATH" 2>/dev/null) || BASENAME="$FILE_PATH"

is_secret () {
    case "$1" in
        .env|.env.*) return 0 ;;
        *.pem|*.key) return 0 ;;
        id_rsa|id_rsa.*) return 0 ;;
        credentials|credentials.*) return 0 ;;
        secrets|secrets.*) return 0 ;;
    esac
    return 1
}

if is_secret "$BASENAME"; then
    REASON="deny-secrets: $BASENAME matches a secret-bearing path. Use a secrets manager, not a tracked file."
    printf '{"hookSpecificOutput":{"permissionDecision":"deny","permissionDecisionReason":%s}}\n' \
        "$(printf '%s' "$REASON" | jq -Rs .)"
    exit 0
fi

exit 0
