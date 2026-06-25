#!/bin/sh
# PostToolUse hook: run the project formatter on the file just written.
# Matcher: Write|Edit. Reads the formatter hint from .claude/project.edn
# :lanes when present, else detects by extension. Fail soft: a missing
# formatter or a bad parse never blocks; the hook warns on stderr and
# exits 0.
set -eu

INPUT=$(cat)

if ! command -v jq >/dev/null 2>&1; then
    echo "format-on-write: jq not found on PATH; skipping" >&2
    exit 0
fi

FILE_PATH=$(printf '%s' "$INPUT" | jq -r '.tool_input.file_path // empty' 2>/dev/null) || true
[ -z "$FILE_PATH" ] && exit 0
[ -f "$FILE_PATH" ] || exit 0

ext () {
    case "$1" in
        *.c|*.h|*.cc|*.cpp|*.hpp) echo c ;;
        *.zig) echo zig ;;
        *.clj|*.cljs|*.cljc) echo clj ;;
        *.ex|*.exs) echo ex ;;
        *) echo "" ;;
    esac
}

EXT=$(ext "$FILE_PATH")
[ -z "$EXT" ] && exit 0

# Scan the descriptor's :lanes for a formatter name; descriptor wins.
DESC=".claude/project.edn"
LANES_FMT=""
if [ -f "$DESC" ]; then
    if   grep -qE 'clang-format' "$DESC" 2>/dev/null; then LANES_FMT=clang-format
    elif grep -qE 'zig fmt'      "$DESC" 2>/dev/null; then LANES_FMT=zig
    elif grep -qE 'cljfmt'       "$DESC" 2>/dev/null; then LANES_FMT=cljfmt
    elif grep -qE 'zprint'       "$DESC" 2>/dev/null; then LANES_FMT=zprint
    elif grep -qE 'mix format'   "$DESC" 2>/dev/null; then LANES_FMT=mix
    fi
fi

pick () {
    if [ -n "$LANES_FMT" ]; then echo "$LANES_FMT"; return; fi
    case "$1" in
        c)   echo clang-format ;;
        zig) echo zig ;;
        clj) echo cljfmt ;;
        ex)  echo mix ;;
    esac
}

FMT=$(pick "$EXT")
[ -z "$FMT" ] && exit 0

run_fmt () {
    case "$1" in
        clang-format) command -v clang-format >/dev/null 2>&1 && clang-format -i "$FILE_PATH" ;;
        zig)          command -v zig          >/dev/null 2>&1 && zig fmt "$FILE_PATH" ;;
        cljfmt)       command -v cljfmt       >/dev/null 2>&1 && cljfmt fix "$FILE_PATH" ;;
        zprint)       command -v zprint       >/dev/null 2>&1 && zprint -w "$FILE_PATH" ;;
        mix)          command -v mix          >/dev/null 2>&1 && mix format "$FILE_PATH" ;;
    esac
}

if ! run_fmt "$FMT" >/dev/null 2>&1; then
    echo "format-on-write: $FMT not runnable on $FILE_PATH; skipping" >&2
fi

exit 0
