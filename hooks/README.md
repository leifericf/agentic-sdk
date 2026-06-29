# Policy-as-hook templates

Policy that lives in hooks, not in prompts, is far more reliable. Three
hook templates, all mino scripts, are the artifacts the descriptor's
`:hooks` list selects: `format-on-write.clj`, `deny-secrets.clj`, and
`require-tests-before-land.clj`. `agentic setup` copies the named
subset from `$AGENTIC_SDK_SRC/hooks/` into the project home at
`~/.agentic-sdk/<project>/.claude/hooks/` and wires them into the host
runtime via the `.claude/hooks` symlink in the project root.

The master scripts live at `$AGENTIC_SDK_SRC/hooks/` and are authored
against the Claude Code hook protocol. In an installed project, Claude
Code runs them through a `.claude/hooks` symlink that resolves into the
project home, wired by a `.claude/settings.json` hooks block. The land
gate is Claude Code only; OpenCode gets the deny-secrets and
format-on-write policies through its native mechanisms and no land gate.

## Runtime contract

Every hook is a mino script invoked as
`mino $CLAUDE_PROJECT_DIR/.claude/hooks/<name>.clj`. It reads hook JSON
from stdin via `host/slurp-stdin`, writes JSON to stdout, and never
hangs. mino must be on PATH; a project without mino leaves its hooks
unarmed (omit them from `:hooks`). Each hook fails soft on a malformed
payload, allowing the call rather than blocking. A denial emits:

```json
{"hookSpecificOutput":{"permissionDecision":"deny","permissionDecisionReason":"..."}}
```

on stdout and exits `0`. An allow exits `0` with no output.

### Finding the SDK source

Each hook bootstraps its classpath with `add-load-path!` against
`$AGENTIC_SDK_SRC`, falling back to `~/Code/agentic-sdk` when the env
var is unset. This puts `spine.host` on the load path. Hooks never use
`babashka.classpath`; mino provides `add-load-path!` directly.

### Reading stdin and parsing JSON

Hooks read all of stdin through `host/slurp-stdin`, which loops
`read-line` so it works under mino (no `*in*` as a Reader) and
Babashka alike. JSON parsing goes through `host/json-parse`, which uses
cheshire when available and falls back to the built-in `spine.json`.
The same pair handles emission via `host/json-encode`.

## The hooks

### `format-on-write.clj`

Runs the project formatter on the file an edit just touched, so
`check-format` costs nothing at review time.

- **Trigger:** PostToolUse on `Write|Edit`.
- **Action:** reads the formatter hint from
  `~/.agentic-sdk/<project>/project.edn` `:lanes` when present, else
  detects by extension (`clang-format` for C, `zig fmt` for Zig,
  `cljfmt` or `zprint` for Clojure, `mix format` for Elixir). Runs the
  fix form on the file.
- **Deny condition:** none. Fail soft: a missing formatter warns on
  stderr and the edit stands.

### `deny-secrets.clj`

Blocks secret-bearing files from the editor boundary.

- **Trigger:** PreToolUse on `Read|Edit|Write`.
- **Action:** reduces the target to its basename.
- **Deny condition:** the basename matches `.env` (and `.env.*`), any
  `*.pem` or `*.key`, `id_rsa` (and `id_rsa.*`), or `credentials` and
  `secrets` (with suffixes). Everything else allows.

### `require-tests-before-land.clj`

Gates land on a green lane run recorded this session.

- **Trigger:** PreToolUse on `Bash`, only when the command is
  land-shaped: `git push`, `jj git push`, `jj bookmark move main` or
  `jj bookmark set main`, and `git merge`.
- **Action:** looks for a green marker.
- **Deny condition:** no marker found. A marker is the `lanes-green`
  file in the spine working dir (`$SPINE_WORK_DIR/lanes-green`,
  default `~/.agentic-sdk/<project>/state/lanes-green`), or a recorded
  `VERDICT: PASS` line in the session transcript. The verifier writes
  the marker after a green pre-land run.

## Claude Code wiring

`agentic setup` writes a `.claude/settings.json` hooks block that maps
matcher events to these scripts. The block below is the full set; only
the entries for the hooks in `:hooks` land.

```json
{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Write|Edit",
        "hooks": [
          { "type": "command",
            "command": "mino $CLAUDE_PROJECT_DIR/.claude/hooks/format-on-write.clj" }
        ]
      }
    ],
    "PreToolUse": [
      {
        "matcher": "Read|Edit|Write",
        "hooks": [
          { "type": "command",
            "command": "mino $CLAUDE_PROJECT_DIR/.claude/hooks/deny-secrets.clj" }
        ]
      },
      {
        "matcher": "Bash",
        "hooks": [
          { "type": "command",
            "command": "mino $CLAUDE_PROJECT_DIR/.claude/hooks/require-tests-before-land.clj" }
        ]
      }
    ]
  }
}
```

The `$CLAUDE_PROJECT_DIR` variable resolves to the project root at hook
fire time. The `.claude/hooks/` path in each command resolves through
the project-root symlink into the project home, so the same copied
script serves both the Claude Code wiring and any direct invocation.
Hooks run under `mino`; the executable bit is not required.

## OpenCode equivalent

OpenCode has no PreToolUse or PostToolUse hooks. Two of the three
policies take a different shape there; the third has no OpenCode
equivalent and is dropped.

- **`deny-secrets`** becomes a deny list of file path patterns in the
  OpenCode permission rules.
- **`format-on-write`** is served by OpenCode's native formatter config
  (a `formatter` field in `opencode.json` or the language's default); no
  hook is needed.
- **`require-tests-before-land`** has no OpenCode equivalent. The land
  gate is Claude Code only.

The OpenCode projection is generated and gitignored or regenerated; the
master form at `$AGENTIC_SDK_SRC/hooks/` is the source of truth. The
`opencode-sync` spine task keeps the projection green against the
masters, and `opencode-check` fails the pre-land lane when it drifts.

## Adding a hook policy

A new policy that belongs at the tool boundary is authored here as a
mino template, then armed by the descriptor. The path is the `add-tech`
meta-skill: detect the gap, interview for the trigger and the deny or
warn action, author the template into this directory, and add its key
to the descriptor's `:hooks`. See `skills/add-tech/SKILL.md`.
