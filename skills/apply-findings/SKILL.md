---
name: apply-findings
description: Fix the punch-list findings assigned to you, one module at one level, smallest sufficient edit, verify then land. Run by editor agents.
user-invocable: false
---

# apply-findings

Fix the punch-list items assigned to you (one module, one fix level),
one commit per finding or tightly-related group. You are the sole
mutator in a fix loop.

## Procedure

Per finding:

1. **Confirm it first.** Read the cited code; reproduce if a repro is
   suggested (a failing test, an eval form, a malformed fixture, a lane
   run with the offending input). A finding you cannot confirm goes
   back as `FAILED <id>: not reproducible, <why>` rather than a
   speculative edit.
2. **Smallest sufficient edit.** Fix exactly the defect. At the
   `:correctness` level do not also rename or refactor; that is the
   factoring wave's job and it makes the diff unreviewable. At
   `:factoring` or `:style` the finding defines the scope.
3. **Stay in bounds.** Your module directory plus its own test
   namespace. If the real fix lives in another module or the public
   surface (a public API, the native boundary contract in
   `references/architecture.md`), return `needs-cross-module <id>`; do
   not write the tempting local workaround. That is exactly the fix the
   no-workarounds rule bans.
4. **Test the fix.** A correctness fix gets a regression test in the
   right surface (`write-tests`) unless one already covers it; "the
   reviewer found it" means the suite did not. For a persistence fix,
   the regression exercises the store's transaction and history
   behavior. For a native fix, the round-trip integration test that
   compiles and calls real native code.
5. **Verify.** The owning tests always; the full suite if the fix could
   touch a caller; the changed language's format and lint lanes; if the
   fix touched the native edge, the round-trip integration test.
6. **Land.** Commit `Category: Imperative summary` with jj (`jj
   describe`, then `jj new`) and fold it into the stack by change id
   (`jj squash` or `jj rebase`). Never edit `CHANGELOG.md` or version
   fields. RETURN your changelog lines; the round runner collects them.
   Changelog lines follow `write-changelog` (user-visible effects only;
   no line is normal for a style or factoring fix). If the project is
   alpha or beta, write-changelog refuses and returns no line; return
   none yourself.

If two of your findings conflict (one wants the code the other
removes), fix in punch-list order and note the second as overtaken:
`FAILED <id>: overtaken by <first-id>`.

## Boundaries

Owns applying one module's findings at one level. The level order and
wave batching are `run-review-round`; scope that crosses modules is the
round runner's call. Reviewers are read-only; the verifier is
bash-heavy with no judgment; the editor is the sole source mutator.

## Return

`LANDED <id> <n> commit(s)` / `needs-cross-module <id>` / `FAILED <id>: <first error>`.
