# Policy-as-hook templates

Policy that lives in hooks, not in prompts, is far more reliable. These
four scripts are the templates the descriptor's `:hooks` list selects.
`bootstrap-project` copies the named subset into a project's
`.claude/hooks/` and wires them into the host runtime.

**Claude Code is the master form.** The scripts are authored against the
Claude Code hook protocol and wired by a `.claude/settings.json` hooks
block. OpenCode has no shell PreToolUse/PostToolUse hooks; the same
policies are expressed there as `opencode.json` permission rules plus an
optional plugin hook. When `:opencode` is in the descriptor's `:runtimes`,
`bootstrap-project` writes the OpenCode permission rules into the
project's `opencode.json` alongside the master `.claude/` wiring. One
source of truth, two runtimes.

## Runtime contract

Every script is POSIX sh, `set -eu`, reads hook JSON from stdin, writes
JSON to stdout, and never hangs. `jq` is required (Claude Code ships it);
each hook fails soft when `jq` is missing or the payload is malformed,
allowing the call with a stderr warning rather than blocking. Denials
emit:

```json
{"hookSpecificOutput":{"permissionDecision":"deny","permissionDecisionReason":"..."}}
```

on stdout and exit `0`. An allow is a bare `exit 0` with no output.

## The hooks

### `format-on-write.sh`

Runs the project formatter on the file an edit just touched, so
`check-format` costs nothing at review time.

- **Trigger:** PostToolUse on `Write|Edit`.
- **Action:** reads the formatter hint from `.claude/project.edn`
  `:lanes` when present, else detects by extension (`clang-format` for
  C, `zig fmt` for Zig, `cljfmt` or `zprint` for Clojure, `mix format`
  for Elixir). Runs the fix form on the file.
- **Deny condition:** none. Fail soft: a missing formatter warns on
  stderr and the edit stands.

### `deny-secrets.sh`

Blocks secret-bearing files from the editor boundary.

- **Trigger:** PreToolUse on `Read|Edit|Write`.
- **Action:** reduces the target to its basename.
- **Deny condition:** the basename matches `.env` (and `.env.*`), any
  `*.pem` or `*.key`, `id_rsa` (and `id_rsa.*`), or `credentials` and
  `secrets` (with suffixes). Everything else allows.

### `require-tests-before-land.sh`

Gates land on a green lane run recorded this session.

- **Trigger:** PreToolUse on `Bash`, only when the command is
  land-shaped: `git push`, `jj git push`, `jj bookmark move main` or
  `jj bookmark set main`, and `git merge`.
- **Action:** looks for a green marker.
- **Deny condition:** no marker found. A marker is
  `$SPINE_WORK_DIR/lanes-green` (default `.spine/lanes-green`), the
  per-repo working dir's `lanes-green` (for example
  `.<repo-name>/lanes-green`), or a recorded `VERDICT: PASS` line in the
  session transcript. The verifier writes the marker after a green
  pre-land run.

## Claude Code wiring

`bootstrap-project` writes a `.claude/settings.json` hooks block that
maps matcher events to these scripts. The block below is the full set;
only the entries for the hooks in `:hooks` land.

```json
{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Write|Edit",
        "hooks": [
          { "type": "command",
            "command": "$CLAUDE_PROJECT_DIR/.claude/hooks/format-on-write.sh" }
        ]
      }
    ],
    "PreToolUse": [
      {
        "matcher": "Read|Edit|Write",
        "hooks": [
          { "type": "command",
            "command": "$CLAUDE_PROJECT_DIR/.claude/hooks/deny-secrets.sh" }
        ]
      },
      {
        "matcher": "Bash",
        "hooks": [
          { "type": "command",
            "command": "$CLAUDE_PROJECT_DIR/.claude/hooks/require-tests-before-land.sh" }
        ]
      }
    ]
  }
}
```

The `$CLAUDE_PROJECT_DIR` variable resolves to the project root at hook
fire time. Scripts must be executable (`chmod +x`); `bootstrap-project`
preserves the executable bit when it copies them.

## OpenCode equivalent

OpenCode has no shell PreToolUse or PostToolUse hooks. The same policies
take a different shape there, and `bootstrap-project` writes them into
the project's `opencode.json` when `:opencode` is in `:runtimes`.

- **`deny-secrets`** becomes a deny list of file path patterns in the
  OpenCode permission rules.
- **`format-on-write`** is served by OpenCode's native formatter config
  (a `formatter` field in `opencode.json` or the language's default); no
  shell hook is needed.
- **`require-tests-before-land`** expresses a prior-state requirement that
  permission rules alone cannot capture. The policy lives in
  `hooks/require-tests-before-land.clj` (Babashka, shared with the Claude Code
  shell hook); an OpenCode adapter at
  `.opencode/plugin/require-tests-before-land.mjs` shells out to it from a
  `permission.ask` hook. `bootstrap-project` arms it by writing a `bash`
  permission rule that turns push and merge into `ask` (the trigger) and
  registering the plugin module under `plugin` in `opencode.json`. The plugin
  is fail-safe: any error allows, so it can never block work.

The OpenCode projection is generated and gitignored or regenerated; the
master form under `.claude/` is the source of truth. The `opencode-sync`
spine task keeps the projection green against the masters, and
`opencode-check` fails the pre-land lane when it drifts.

## Adding a hook policy

A new policy that belongs at the tool boundary is authored here as a
template, then armed by the descriptor. The path is the `add-tech`
meta-skill: detect the gap, interview for the trigger and the deny or
warn action, author the template into this directory, and add its key to
the descriptor's `:hooks`. See `skills/add-tech/SKILL.md`.
