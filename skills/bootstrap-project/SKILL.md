---
name: bootstrap-project
description: Detect the stack, write the descriptor, materialize recipes and scaffolding
disable-model-invocation: true
---

# bootstrap-project

Role: the one-time setup. Detects the stack, writes
`.claude/project.edn`, materializes the active write-<lang> recipes,
scaffolds the artifact and run-state directories, the hooks, and the
project CLAUDE.md, and wires the spine adapter.

## Prerequisites

Run in the project repository root, once per project (or after a stack
change). No descriptor exists yet, or the existing one is being
regenerated.

## Procedure

Follow `docs/design.md` section 8.2. The descriptor schema is
`skills/shared/references/project-descriptor.md`; the spine interface is
`skills/shared/references/spine.md`.

1. **Detect the stack.** Scan for language markers: `deps.edn` or
   `project.clj` (Clojure), `build.zig` or `.zig-version` (Zig),
   `mix.exs` (Elixir), `CMakeLists.txt` or `Makefile` or `*.h`
   alongside `*.c` (C). Detect the VCS (`.jj` resolves to `:jj`, else
   `.git` to `:git`); jj-first is the default. Detect the UI surface
   (frontend markers). Detect the spine level from `bb` presence on
   PATH: `:babashka` when present, else `:thin` (the level for C, Zig,
   or Elixir without bb), else `:none` when the project opts out.
   Derive the working dir from the repo name.
2. **Elicit the gaps.** Ask one batch of at most three questions for
   what the detector cannot decide: the architecture pattern (confirm
   Functional Core / Imperative Shell or name a divergence), the native
   edge (is there a boundary between the languages), and the module
   roots (the detector proposes, the author confirms or corrects). Apply
   the descriptor defaults when the author gives no answer.
3. **Write the descriptor.** Write `.claude/project.edn` (committed, not
   gitignored) with the detected and elicited fields. Every field has a
   default; a minimal descriptor is `{}`.
4. **Materialize the active recipes.** Copy the toolkit masters for the
   write-<lang> recipes named in `:languages` into the project's
   `.claude/skills/`. Only the active subset lands. The four curated
   masters are write-c, write-zig, write-clj, write-elixir. A language
   outside the bounded stack lands nothing here; it is added later via
   add-language.
5. **Scaffold the artifact and run-state directories.** Create
   `.claude/artifacts/` (planning, decisions, project, ops, adr per the
   descriptor's `:adr :store`), `.claude/runs/` (ephemeral, gitignored),
   and the spine working dir (`:spine :working-dir`, gitignored).
6. **Scaffold the hooks and the CLAUDE.md.** Materialize the hook
   templates named in `:hooks` (`format-on-write`, `deny-secrets`,
   `require-tests-before-land`, `mcp-first`) into `.claude/hooks/`. Drop
   the CLAUDE.md skeleton into the project root: a hard-rule-first
   router, a tool and MCP table, the normative domain guidelines, the
   concrete lane and eval commands from the descriptor, the operational
   gotchas, and the safety denylist.
7. **Wire the spine and the runtime projections.** For the detected
   level (`:babashka`, `:thin`, `:none`), wire the spine adapter so the
   task names in `spine.md` answer. For every runtime in `:runtimes`
   (default both `:claude-code` and `:opencode`), run the `opencode-sync`
   spine task to project the masters into `.opencode/`. Run
   `opencode-check` to confirm the derived form is green against the
   masters.

## Boundaries

Run once per project (or after a stack change). It writes only the
descriptor, the materialized recipe subset, the scaffolded directories,
the hooks, the CLAUDE.md, and the spine and runtime projections. It
does not write project code, does not pick a feature, does not plan. It
is the one setup valve; every later retune goes through the add-*
meta-skills, which amend the descriptor. Atoms dispatched: the
`opencode-sync` and `opencode-check` spine tasks for the runtime
projections. The hook templates, the CLAUDE.md skeleton, and the
write-<lang> masters are sources this skill copies from.

## Returns

One line: the descriptor path, the materialized recipe count, the
scaffolded paths, the detected spine level, and the runtime projections
written.
