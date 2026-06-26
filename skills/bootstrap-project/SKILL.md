---
name: bootstrap-project
description: Detect the stack, write the descriptor, materialize recipes and scaffolding
disable-model-invocation: true
---

# bootstrap-project

Role: the one-time setup. Detects the stack, writes
`.agentic-sdk/project.edn`, snaps the masters (skills, agents, hooks,
spine, templates) into `.agentic-sdk/`, symlinks the `.claude/` adapter
over it, generates the `.opencode/` projection, drops the root
`CLAUDE.md` and project `.gitignore`, and wires the spine adapter and
the runtime projections.

## Prerequisites

Run in the project repository root, once per project (or after a stack
change). No descriptor exists yet, or the existing one is being
regenerated.

## Procedure

Follow `docs/design.md` section 8.2. The descriptor schema is
`skills/shared/references/project-descriptor.md`; the spine interface is
`skills/shared/references/spine.md`; the hook templates and their
runtime mapping are documented in `hooks/README.md`.

1. **Detect the stack.** Scan for language markers: `deps.edn` or
   `project.clj` (Clojure), `build.zig` or `.zig-version` (Zig),
   `mix.exs` (Elixir), `CMakeLists.txt` or `Makefile` or `*.h`
   alongside `*.c` (C). Detect the VCS (`.jj` resolves to `:jj`, else
   `.git` to `:git`); jj-first is the default. Detect the UI surface
   (frontend markers). Detect the spine level from `bb` presence on
   PATH: `:babashka` when present, else `:thin` (the level for C, Zig,
   or Elixir without bb), else `:none` when the project opts out.
   Derive the working dir as `.agentic-sdk/state/`.
2. **Elicit the gaps.** Ask one batch of at most three questions for
   what the detector cannot decide: the architecture pattern (confirm
   Functional Core / Imperative Shell or name a divergence), the native
   edge (is there a boundary between the languages), and the module
   roots (the detector proposes, the author confirms or corrects). Ask
   in the same batch which `:hooks` to arm and whether to confirm both
   `:runtimes`. Apply the descriptor defaults when the author gives no
   answer.
3. **Write the descriptor.** Write `.agentic-sdk/project.edn`
   (committed, not gitignored) with the detected and elicited fields.
   Every field has a default; a minimal descriptor is `{}`.
4. **Snap masters into `.agentic-sdk/`.** Copy the toolkit's `skills/`
   (the shared doctrine verbatim; the write-<lang> recipes filtered to
   the `:languages` subset), `agents/`, `hooks/` (filtered to `:hooks`),
   the spine (`bb.edn` plus `src/spine/`), and `templates/` into
   `.agentic-sdk/`. All re-installable by re-running this step.
5. **Scaffold the artifact and run-state directories.** Create
   `.agentic-sdk/artifacts/` (planning, decisions, project, ops, and
   adr per the descriptor's `:adr :store`) and `.agentic-sdk/runs/`
   (ephemeral, gitignored). The spine working dir
   (`:spine :working-dir`, default `.agentic-sdk/state/`) is created
   on first spine task run.
6. **Symlink the Claude Code adapter.** Create `.claude/skills`,
   `.claude/agents`, and `.claude/hooks` as symlinks pointing to
   `../.agentic-sdk/{skills,agents,hooks}` so Claude Code resolves the
   masters under its expected paths.
7. **Drop the root CLAUDE.md.** Copy `templates/CLAUDE.md` (now snapped
   at `.agentic-sdk/templates/CLAUDE.md`) into the project root as
   `CLAUDE.md`. Fill the `{{placeholders}}` from the descriptor: project
   name, primary and secondary languages, the domain guidelines path,
   the cheap, wave, and pre-land lane commands, the eval command, the
   spine working dir, and the operational gotchas. The project owns
   this file after bootstrap.
8. **Write the project `.gitignore`.** Copy `templates/gitignore` (now
   snapped at `.agentic-sdk/templates/gitignore`) to the project root
   as `.gitignore`. It commits only `.agentic-sdk/project.edn`,
   `.agentic-sdk/artifacts/`, the root `CLAUDE.md`,
   `.claude/settings.json`, and `.opencode/opencode.json`; everything
   else under `.agentic-sdk/`, the `.claude/` symlinks, and generated
   `.opencode/` is gitignored.
9. **Wire the Claude Code hook block.** Write the `.claude/settings.json`
   hooks block that maps each armed hook to its matcher event per
   `hooks/README.md`. Scripts resolve under
   `$CLAUDE_PROJECT_DIR/.claude/hooks/` through the symlink into
   `.agentic-sdk/hooks/`.
10. **Generate the OpenCode adapter and wire the spine.** For every
    runtime in `:runtimes`: run the `opencode-sync` spine task to project
    the masters into `.opencode/agent/`; write the OpenCode permission
    rules and formatter into `.opencode/opencode.json` (the deny list
    from `deny-secrets`, the formatter config from `format-on-write`);
    drop the `require-tests-before-land` plugin into
    `.opencode/plugins/` (auto-loaded, enforces via `tool.execute.before`,
    no permission rule needed); run `opencode-check` to confirm the
    derived form is green against the masters. For the detected spine
    level (`:babashka`, `:thin`, `:none`), wire the spine adapter so the
    task names in `spine.md` answer.

## Boundaries

Run once per project (or after a stack change). It writes only the
descriptor at `.agentic-sdk/project.edn`, the snapped masters and
scaffolded directories under `.agentic-sdk/`, the `.claude/` symlinks
and the `.claude/settings.json` hook block, the root `CLAUDE.md`, the
project `.gitignore`, the spine wiring, and the runtime projections
under `.opencode/` and `.opencode/opencode.json`. It does not write
project code, does not pick a feature, does not plan. It is the one
setup valve; every later retune goes through the add-* meta-skills,
which amend the descriptor. Atoms dispatched: the `opencode-sync` and
`opencode-check` spine tasks for the runtime projections. The hook
templates in `hooks/`, the CLAUDE.md skeleton in `templates/`, the
`templates/gitignore` skeleton, and the write-<lang> masters are
sources this skill copies from.

## Return

One line: the descriptor path under `.agentic-sdk/`, the snapped master
count, the scaffolded paths, the `.claude/` symlinks created, the root
`CLAUDE.md` and `.gitignore` written, the armed hooks, the detected
spine level, and the runtime projections written under `.opencode/`.
