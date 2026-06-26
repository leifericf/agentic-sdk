# Policy-as-hook templates

Policy that lives in hooks, not in prompts, is far more reliable. Three
hook templates, all Babashka, are the artifacts the descriptor's `:hooks`
list selects: `format-on-write.clj`, `deny-secrets.clj`, and
`require-tests-before-land.clj`. `bootstrap-project` snaps the named
subset into a project's `.agentic-sdk/hooks/` and wires them into the
host runtime via the `.claude/hooks` symlink.

The master scripts live at `.agentic-sdk/hooks/` and are authored
against the Claude Code hook protocol. In an installed project, Claude
Code runs them through a `.claude/hooks` symlink into
`.agentic-sdk/hooks`, wired by a `.claude/settings.json` hooks block.
OpenCode has no PreToolUse or PostToolUse hooks; the same policies take
a different shape there (permission rules plus one plugin), written into
`.opencode/opencode.json` when `:opencode` is in `:runtimes`. One
source of truth, two runtimes.

## Runtime contract

Every hook is a Babashka script invoked as `bb <hook>.clj`. It reads
hook JSON from stdin, writes JSON to stdout, and never hangs. Babashka
must be on PATH; a project without bb leaves its hooks unarmed (omit
them from `:hooks`). Each hook fails soft on a malformed payload,
allowing the call rather than blocking. A denial emits:

```json
{"hookSpecificOutput":{"permissionDecision":"deny","permissionDecisionReason":"..."}}
```

on stdout and exits `0`. An allow exits `0` with no output.

## The hooks

### `format-on-write.clj`

Runs the project formatter on the file an edit just touched, so
`check-format` costs nothing at review time.

- **Trigger:** PostToolUse on `Write|Edit`.
- **Action:** reads the formatter hint from `.agentic-sdk/project.edn`
  `:lanes` when present, else detects by extension (`clang-format` for
  C, `zig fmt` for Zig, `cljfmt` or `zprint` for Clojure, `mix format`
  for Elixir). Runs the fix form on the file.
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

Gates land on a green lane run recorded this session. One script serves
both runtimes: it detects the caller from the input shape.

- **Trigger:** PreToolUse on `Bash`, only when the command is
  land-shaped: `git push`, `jj git push`, `jj bookmark move main` or
  `jj bookmark set main`, and `git merge`.
- **Action:** looks for a green marker.
- **Deny condition:** no marker found. A marker is the `lanes-green`
  file in the spine working dir (`$SPINE_WORK_DIR/lanes-green`, default
  `.agentic-sdk/state/lanes-green`), or a recorded `VERDICT: PASS` line
  in the session transcript. The verifier writes the marker after a
  green pre-land run.
- **Two shapes, one policy.** Claude Code sends its native hook JSON
  (`tool_input.command`, `transcript_path`); the script emits the deny
  JSON above on deny, nothing on allow. The OpenCode plugin sends
  `command` and `cwd`; the script emits `{allow true}` or
  `{allow false, reason}`.

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
            "command": "bb $CLAUDE_PROJECT_DIR/.claude/hooks/format-on-write.clj" }
        ]
      }
    ],
    "PreToolUse": [
      {
        "matcher": "Read|Edit|Write",
        "hooks": [
          { "type": "command",
            "command": "bb $CLAUDE_PROJECT_DIR/.claude/hooks/deny-secrets.clj" }
        ]
      },
      {
        "matcher": "Bash",
        "hooks": [
          { "type": "command",
            "command": "bb $CLAUDE_PROJECT_DIR/.claude/hooks/require-tests-before-land.clj" }
        ]
      }
    ]
  }
}
```

The `$CLAUDE_PROJECT_DIR` variable resolves to the project root at hook
fire time. The `.claude/hooks/` path in each command resolves through
the symlink into `.agentic-sdk/hooks/`, so the same script master serves
both the Claude Code wiring and any direct invocation. Hooks run under
`bb`; the executable bit is not required.

## OpenCode equivalent

OpenCode has no PreToolUse or PostToolUse hooks. The same policies take
a different shape there, and `bootstrap-project` writes them into the
project's `opencode.json` when `:opencode` is in `:runtimes`.

- **`deny-secrets`** becomes a deny list of file path patterns in the
  OpenCode permission rules.
- **`format-on-write`** is served by OpenCode's native formatter config
  (a `formatter` field in `opencode.json` or the language's default); no
  hook is needed.
- **`require-tests-before-land`** expresses a prior-state requirement
  that permission rules alone cannot capture. An OpenCode adapter at
  `.opencode/plugins/require-tests-before-land.mjs` calls the same
  `hooks/require-tests-before-land.clj` (normalized shape) from a
  `tool.execute.before` hook and throws to block a land op that lacks a
  green marker. OpenCode has no hook that resolves a permission `ask`
  programmatically (`permission.asked` and `permission.replied` only
  notify), so the plugin watches bash calls directly. `bootstrap-project`
  drops the file in `.opencode/plugins/`, where OpenCode auto-loads it;
  no `opencode.json` entry or `bash` permission rule is needed. The
  plugin is fail-safe: any plumbing error allows, so only an explicit
  deny from the policy ever blocks work.

The OpenCode projection is generated and gitignored or regenerated; the
master form at `.agentic-sdk/hooks/` is the source of truth. The
`opencode-sync` spine task keeps the projection green against the
masters, and `opencode-check` fails the pre-land lane when it drifts.

## Adding a hook policy

A new policy that belongs at the tool boundary is authored here as a
Babashka template, then armed by the descriptor. The path is the
`add-tech` meta-skill: detect the gap, interview for the trigger and the
deny or warn action, author the template into this directory, and add
its key to the descriptor's `:hooks`. See `skills/add-tech/SKILL.md`.
