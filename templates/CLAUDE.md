# {{project_name}}

The project router. Read on every session start, so it leads with the hard
rules, then the surface map, then operational detail. `bootstrap-project`
drops it from the toolkit template and fills the `{{placeholders}}`. Edit in
place; the project owns it after bootstrap.

## Hard rules (read first)

- **jj-first VCS.** Describe, commit, and move bookmarks with `jj`. The
  `{{vcs_adapter}}` adapter detects git-only checkouts and falls back,
  but the canonical flow is jj.
- **Tests before implementations.** Write the failing test, then the
  code. Every assertion must be able to fail.
- **Functional Core / Imperative Shell.** New code follows the house
  pattern in `.agentic-sdk/skills/shared/references/architecture.md`. Pure
  data and pure functions in the core; effects and state in the shell;
  native edges stay at the boundary.
- **Policy lives in hooks, not in prompts.** The armed hooks in
  `.agentic-sdk/hooks/` enforce format-on-write, secret denial, the green
  lane gate before land. Claude Code
  resolves them through the `.claude/hooks` symlink. Do not work
  around a hook denial; fix the underlying condition.
- **One source of truth for the run.** The orchestrator reads
  `run` after each phase, not the transcript. Workers return one
  contracted line.

## Tools and MCP

| Surface | When to use | Notes |
|---|---|---|
| {{primary_language}} | default for new code | see `.agentic-sdk/skills/write-{{primary_language}}/SKILL.md` |
| {{secondary_language}} | {{secondary_language_when}} | native edge at {{native_edge_path}} |

## Domain guidelines

The normative guidelines for this project live in
`{{domain_guidelines_path}}`. Read them before designing against any
module listed there. When a guideline and a generic recipe disagree, the
project guideline wins; record the divergence as a decision via
`record-decision`.

## Lanes and eval

Run these from the project root. The cheap tier fits the tight edit
loop; the wave tier runs once per review round; the pre-land tier gates
a land.

```bash
# Cheap tier (every edit):
{{lanes_cheap}}

# Wave tier (per review round):
{{lanes_wave}}

# Pre-land tier (before push or merge):
{{lanes_pre_land}}

# Eval set (regression check before land):
{{eval_command}}
```

The verifier records `VERDICT: PASS` and writes a `lanes-green` marker
into the spine working dir (`{{spine_working_dir}}`) after a green
pre-land run. The `require-tests-before-land` hook arms on that marker.

## Operational gotchas

- {{gotcha_one}}
- {{gotcha_two}}
- {{gotcha_three}}

## Safety denylist

Do not read, write, or commit any of the following. The `deny-secrets`
hook enforces this at the editor boundary; the list here is the human
reference.

- `.env` and any `.env.*` variant (use a secrets manager).
- Private key material: `*.pem`, `*.key`, `id_rsa` and `id_rsa.*`.
- Credential stores: `credentials`, `credentials.*`, `secrets`,
  `secrets.*`.
- Anything pulled from a secrets backend into a tracked file.

If a secret has already been committed, rotate it and sweep history; do
not treat the denylist as recoverable after the fact.
