# Agent guide

The standard project router for an agentic-sdk project. Identical across
projects; it carries no per-project content. The project's own config is the
single source of truth at `~/.agentic-sdk/<project>/project.edn`; the skill
catalog and shared doctrine live under `$AGENTIC_SDK_SRC/skills/`; project
design, ADRs, and operational notes live in `docs/` and
`~/.agentic-sdk/<project>/artifacts/`.

## Hard rules (read first)

- **VCS.** Describe and commit per the descriptor's `:vcs`; the spine's VCS
  adapter detects jj or git. Commits are single line, category first
  (`Category: Imperative subject`), imperative, no trailing period, within 70
  characters, effect not diff, no body, no attribution, no version numbers, no
  em dash characters. See `write-commit`.
- **Tests before implementations.** Write the failing test, then the code.
  Every assertion must be able to fail.
- **Functional Core / Imperative Shell.** Pure data and functions in the
  core; effects and state in the shell; native edges at the boundary. See
  `$AGENTIC_SDK_SRC/skills/shared/references/architecture.md`.
- **Policy lives in hooks, not prompts.** The armed hooks in
  `~/.agentic-sdk/<project>/.claude/hooks/` enforce format-on-write, secret
  denial, and the green-lane gate before land. Do not work around a hook
  denial; fix the condition.
- **One source of truth for the run.** The orchestrator reads `run` after
  each phase, not the transcript. Workers return one contracted line.

## Skills and entry points

The catalog is under `$AGENTIC_SDK_SRC/skills/` (shared doctrine in
`$AGENTIC_SDK_SRC/skills/shared/references/`). Human-invoked entry points:
`plan-system`, `advance-plan`, `implement-change`, `audit-code`,
`investigate`, `fix-bug`, `ship`. Authoring: `write-<lang>`, `write-tests`,
`write-ui`, `write-prose`, `write-commit`, `write-changelog`. Reviewing: the
`check-*` dimensions. A mid-task correction: `capture-guidance`, then
`incorporate-feedback`. A real choice between alternatives:
`record-decision`, which writes an ADR.

## Lanes, modules, and permissions

All in `~/.agentic-sdk/<project>/project.edn`: `:lanes` (the cheap, wave, and
pre-land commands), `:architecture :modules`, and `:permissions`. The verifier
records `VERDICT: PASS` and writes a `lanes-green` marker into the spine
working dir after a green pre-land run; the `require-tests-before-land` hook
arms on it.

## Project specifics

This file is standard. For what is unique to this project, read:
- `~/.agentic-sdk/<project>/project.edn` for the stack, lanes, modules,
  permissions, and the ADR store path;
- `docs/` and `~/.agentic-sdk/<project>/artifacts/` for the design, the ADRs,
  and the operational notes. When a project doc and a generic recipe disagree,
  the project doc wins; record the divergence via `record-decision`.

## Safety denylist

Do not read, write, or commit `.env` (and `.env.*`), private key material
(`*.pem`, `*.key`, `id_rsa` and `id_rsa.*`), or credential stores
(`credentials`, `credentials.*`, `secrets`, `secrets.*`). The `deny-secrets`
hook enforces this at the editor boundary.
