---
name: maintain-toolchain
description: Toolchain maintenance. The pin, the doctor check, the bump ritual, and the investigated-ceiling cache. Invoked by the verifier agent and maintainer dispatches.
user-invocable: false
---

# maintain-toolchain

Role: keep the toolchain pinned, diagnosed, and bumped, and keep the
investigated ceiling from being re-spiked.

One law frames everything: **the pin is the pin.** Every lane runs the
pinned toolchain and only the pinned toolchain. The pin lives in the
descriptor (the `:lanes` commands assume it) and in the language-native
pin files (`.zig-version` plus `build.zig.zon`'s `minimum_zig_version`
for Zig; `.tool-versions` or per-language version files for other
stacks; lockfiles where the language uses them). Never substitute a host
toolchain of a different version to make a lane pass. That is an
infrastructure failure to report, not to work around.

## The doctor check

A doctor lane diagnoses the toolchain: it reads the pin, runs the host's
version command, and compares. It reports the resolved path, the running
version, the pinned version, and whether they match. Run it first when a
lane reports a toolchain problem. The doctor command itself is a lane in
the descriptor's table (commonly under `:pre-land` or a `:doctor` slot
the verifier runs on demand); it is not hardcoded here.

## How lanes report the pin

A lane whose pinned toolchain is absent or mismatched hard-fails; it
never silently passes on a different version. The line is exactly:

```
FAIL <lane>: pinned <tool> missing (repro: <doctor command>)
```

This applies to every lane in the table. The verdict for the tier is
then `VERDICT: FAIL`. Diagnose with the doctor check before touching
anything else; a missing pin is fixed by installing the pinned
toolchain, not by loosening the pin.

## The bump ritual

A pin bump is deliberate and lands as its own commit.

1. Update every pin site in lockstep: the descriptor, the language-
   native version files, the CI workflow's setup action, and any
   lockfile. Identical version in every site, one change.
2. Regenerate whatever the toolchain produces (stencils, bindings,
   generated headers, format baselines) and commit the regeneration in
   the same change.
3. The doctor check must report a match; the full lane table (all three
   tiers) must pass on the new pin before the bump commits.
4. Commit alone, category-first subject (for example
   `Build: Bump pinned toolchain`), no version number in the message.

Never widen the pin to absorb a host that happens to be installed. The
pin moves only by an intentional bump that the whole lane table has been
re-run against.

## Do not re-spike the ceiling

Some toolchain features are structurally unavailable on a given pin
(sanitizers that need LLVM runtime libs the pin does not ship,
profiling data tooling the pin lacks, fuzzing harnesses that require a
different compiler frontend). When a lane's absence is investigated and
found to be structural, record the finding where the maintainer reads it
(the maintainer toolchain notes the descriptor points at, an ADR, or a
decisions entry): what was investigated, why it is unavailable on this
pin, and where the substitute lives (a host compiler, a separate bench
harness). A later bump that lifts the ceiling re-opens the feature; until
then, do not re-spike it. Re-running the same investigation every
campaign is the cost this cache exists to prevent.

Never regenerate a static-analysis baseline (or any expected-failures
cache) to silence a finding you have not read. The baseline is the record
of what the gate accepts; regenerating it without understanding the diff
erases the gate.

## Boundaries

Owns: the pin, the doctor check, the bump ritual, the ceiling cache.
Siblings: `verify-lanes` owns running the descriptor's lane table and
reporting the verdict; the descriptor owns the lane commands and the
toolchain pin sites; `record-decision` owns the ADR a ceiling
investigation promotes to when it proves architectural.

## Return contract

When run as a lane (the doctor check), the same contract as
`verify-lanes`: `PASS|SKIP|FAIL <lane>` lines plus
`VERDICT: PASS|FAIL`. When run as a bump ritual, return the one-line
result (`bumped <tool> to <version>, all tiers green`) plus the commit
id.
