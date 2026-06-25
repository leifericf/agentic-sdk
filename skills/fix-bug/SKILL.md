---
name: fix-bug
description: Fix one bug end to end, from reproducer to one commit
disable-model-invocation: true
---

# fix-bug

Role: the single-bug fix. Reproduces the bug, writes the failing test,
fixes at the source, verifies, lands one commit.

## Prerequisites

`.agentic-sdk/project.edn` exists with `:lanes`. A bug report: a wrong
behavior, a crash on a specific input, a failing assertion, or a
description.

## Procedure

The discipline, in order. Do not skip steps.

1. **Reproduce.** Reduce to the smallest form that shows the wrong
   behavior: a single function call, a one-input test, a minimal fixture
   through the failing path. No fix lands without a confirmed
   reproducer. "Cannot reproduce" goes back to the reporter with what
   you tried. Keep reproducer fixtures under the project's test fixtures
   path and cite the path in the regression test.
2. **Failing test first.** Write the regression test in the right
   surface via write-tests: pure core to the core test namespace, a
   native edge to a round-trip integration test, persistence to a
   scratch store. Run it; watch it fail for the expected reason. Commit
   it first (`Tests: ...`) so history proves fail then pass.
3. **Find the cause, not the symptom.** First check the decision index
   (the ADR store from the descriptor) and the design docs. Behavior
   that matches an existing ADR or a documented design choice is not a
   bug; report that back instead of fixing it. Then fix at the source:
   the pure core function, the native body, the persistence transaction,
   or the shell wiring. Never a caller-side special case, never a test
   adjustment. Classify the gap: a real defect (fix here), an upstream
   platform difference (document at the site), or harness debt (fix the
   harness).
4. **Fix smallest-sufficient.** If the cause is in another module than
   expected, follow it and say so. A factoring finding is a valid fix-bug
   outcome: open it as a forward task, do not silently refactor here.
5. **Verify.** Dispatch one verifier running verify-lanes against the
   project's lanes (from the descriptor): the cheap tier always. When
   the fix touched a native edge (any native body or the boundary
   itself), also a round-trip test that compiles and calls the real
   native code, and the native formatter on any changed source. When the
   fix touched persistence, a transaction-history test. When the fix
   touched a computation with tolerance, an assertion that the returned
   values are within tolerance on a known-good fixture.
6. **Commit.** One commit via write-commit: `Category: Imperative
   subject`, single line. The regression test and the fix land as the
   next commit on the tip, not a branch.

## Boundaries

Solos by default. Fan out only when the bug is actually several bugs,
or the reproducer hunt needs parallel hypotheses (dispatch reviewers
with explicit hypotheses). Reads only the one-line returns of any
verifier or reviewer it dispatches. It does not advance main; the
commit lands on the tip and the maintainer advances main at land time.
Atoms dispatched: write-tests (the regression test), write-<lang> (the
source fix), verify-lanes, optionally reviewer dispatches for a
hypothesis hunt, write-commit.

## Return

One line: the commit id, the category, and the verify verdict, with the
regression test that failed before and passes after.
