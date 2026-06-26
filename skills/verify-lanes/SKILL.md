---
name: verify-lanes
description: The verification lane table. Runs the descriptor's three lane tiers in order and reports pass or fail per lane. Invoked by the verifier agent and the review-round-runner.
user-invocable: false
---

# verify-lanes

Role: run the project's deterministic lanes and report a verdict.

The lane table is descriptor data, never hardcoded commands. Read
`.agentic-sdk/project.edn`'s `:lanes` map (see
`skills/shared/references/project-descriptor.md`): three tiers, each a
vector of shell command strings run in vector order, stopping on the
first failure.

- `:cheap` runs on every unit and after every edit. The tight loop; it
  carries only what must gate each landing.
- `:wave` runs once per review round, once per integrated wave. The
  slower tier that justifies a wave, not a tight loop.
- `:pre-land` runs before a land and before a tag. The gate the
  `require-tests-before-land` hook arms on.

A tier absent or empty in the descriptor reports
`SKIP <tier>: not configured`. A command not yet wired (no build file,
no test runner) reports `SKIP <lane>: not yet wired` so the verifier is
honest about coverage gaps rather than silently passing.

## How to run

Run each tier's commands in order, in the project root. The dispatch
names the tier (`:cheap`, `:wave`, or `:pre-land`); run only that tier
unless told to run all. On failure, capture the first error line and the
repro command, then stop the tier (unless dispatched with "run all").

## Keep the cheap tier fast

The cheap tier runs many times per slice; its wall-clock is paid
repeatedly, so it carries only what must gate each landing, never the
expensive checks a wave or pre-land boundary can carry once.

- **Owned tests, not the full suite, in the cheap tier.** Scope the test
  command to the touched module when the descriptor's lane allows it;
  the full suite belongs to the wave and pre-land.
- **Reduced property case counts in the cheap tier; full count at the
  wave.** The property is unchanged; only how many cases the inner loop
  samples.
- **Coverage and cross-build canaries are per-wave or pre-land, never
  per-unit.** They instrument or re-run the whole suite; a per-unit run
  is wall-clock the boundary should have absorbed.
- **Amortize startup where a persistent process is available** (a
  long-lived test runner, an nREPL, a REPL). A cold process per lane
  pays fixed startup every time and buys no rigor.

## Conditional additions

A tier may carry conditional lanes the descriptor flags. The general
rule: when a change touches a seam (the native edge, a public API, a
schema, a serialization shape), the lane for that seam runs. Which seams
a project has, and which lane covers each, live in the descriptor, not
here. Do not invent a conditional lane; run only what the descriptor
names.

## Reporting

One line per lane, exactly:

```
PASS <lane>
SKIP <lane>: <short reason>
FAIL <lane>: <first error line> (repro: <command>)
```

Then a final line:

```
VERDICT: PASS|FAIL
```

When the run includes the `:pre-land` tier and the verdict is `PASS`, write
the green marker the `require-tests-before-land` hook arms on: an empty file
named `lanes-green` in the spine working dir (the descriptor's
`:spine :working-dir`, or the default `.agentic-sdk/state`). A `FAIL` on the
`:pre-land` tier removes any existing marker; a run that does not include
`:pre-land` leaves it untouched.

Never re-run a failed lane more than once. Never downgrade a failure to
acceptable; that judgment belongs upstream. A `PASS` with `SKIP` lines is
honest but tells the caller what coverage is missing. A lane whose
pinned toolchain is absent hard-fails (see `maintain-toolchain`), never a
silent pass.

## Boundaries

Owns: running the descriptor's lanes and reporting a verdict. Siblings:
`maintain-toolchain` owns the pin, the doctor check, and the bump ritual
the lanes depend on; the `lint` spine task owns the zero-token mechanical
pre-pass whose findings join the same pool; the reviewer dimensions own
judgment over what the lanes cannot catch.

## Return

The `PASS|SKIP|FAIL <lane>` lines plus `VERDICT: PASS|FAIL`, nothing
else.
