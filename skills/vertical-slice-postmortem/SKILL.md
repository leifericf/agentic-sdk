---
name: vertical-slice-postmortem
description: After a major slice lands, root-cause its review findings and its token and verification cost, then commit improvements back into the skill system so the next slice is cheaper and better.
user-invocable: false
---

# vertical-slice-postmortem

A review round fixes the symptom; this skill fixes the cause. After a
landed slice, it asks why each class of defect was written, hardens the
recipe that should have prevented it and the test that should have
caught it, and asks where the slice spent more tokens or wall-clock
than its quality required. Run once per major slice so the system
compounds quality and efficiency instead of relearning the same lesson.

## The four goals in tension

The system pursues four goals at once, and they pull against each
other:

1. Highest code quality: well-factored, correct, conformant.
2. Fewest tokens spent reaching it.
3. Fastest iteration: short build, verify, and review cycles.
4. Thorough review and test: proven well-factored and bug-free.

Goals 2 and 3 pull against 1 and 4. Another review round and more
generated cases buy quality at the cost of tokens and time; fewer buy
economy and speed at the risk of a defect. The balance is
risk-proportionate: spend the extra round, the tokens, and the
wall-clock where the blast radius is large or the input untrusted (the
native edge, a query compiler, a security seam, a cross-module change),
and economize where the unit is small and pure (a mechanical scaffold,
a test-only phase, a single-module refactor). The lean default, the
earned-second-round rule, and security-shifts-left are this principle
at work. This retrospective is where the balance is re-tuned each
slice. A finding that the slice was correctly balanced is a valid
result; do not invent waste or risk to act on.

## Stance: root cause, not symptom; prevention, not patch

- Every fix commit is evidence. A defect found in review is a defect
  the writing recipe let through and the test routine did not catch.
  The slice's fix commits and review findings are the raw material.
- Cluster, do not enumerate. Ten fixes for the same native-lifetime
  bug are one root cause, not ten. The improvement targets the class.
- Improve the source, including the tests. For each class, harden the
  write-time recipe (the relevant `write-<lang>`, `write-ui`), AND the
  test routine that would have caught it (`write-tests`,
  `verify-lanes`), AND, where cheap, the review dimension that found
  it, so the next catch is a lint or a unit test, not a costly round.
- Smallest sufficient change. A checklist line, a lane, a test pattern,
  not a rewrite. An improvement that would not have prevented a real
  defect this slice does not land. Do not bloat the skills.
- Be honest about the uncatchable. Some defects only a runtime or an
  interactive run can surface. Say so; do not pretend a lane covers
  what it cannot.
- Cost is evidence too. Wasted tokens and wall-clock are defects in
  the orchestration, not the code. A verbose return the caller had to
  hold, an invariant repeated in every dispatch, an expensive lane run
  per phase instead of per deliverable, a heavy model on a mechanical
  phase: each is a recurring tax the next slice pays. Cut it at the
  source like any other class, and only where the evidence shows the
  rigor survives the cut.

## When it runs

Once per major slice, automatically, as the campaign's last action
after the deliverable is placed and the session-end report is written.
`advance-plan` and `implement-change` fire it; the maintainer may also
run it on demand. Do not run it mid-slice, or more than once for the
same slice; if a slice lands in pieces, run it once over the whole
landed deliverable.

## Procedure

1. **Gather the evidence.** Read the slice's review findings and
   decisions (the run's decisions log and any round summaries) and the
   slice's fix commits (the commits that landed in review rounds, after
   the initial write). Each fix is a defect that slipped past the
   write-time recipe and the verification lanes.
2. **Cluster by root cause.** Group defects into classes by shared
   cause, not surface. Name each class in terms a future writer will
   recognize before making the mistake (for example "native resource
   lifetime: acquire, use, release ordering across the boundary", not
   "a native bug"). Count each class; note severity.
3. **Trace each class to its skill.** For each class answer three
   questions and record them: which write-time recipe should have
   prevented it? which test or lane should have caught it? which review
   dimension found it, and can that move earlier (a lint rule, a
   checklist, a unit test) so it does not cost a full round next time?
4. **Land the improvement.** For each class make the smallest change to
   the named skill(s) that would have prevented or cheaply caught the
   defect: a checklist in the recipe, a required test pattern, a
   verification lane, a sharper review-dimension prompt. Aim for one
   prevention (a write-time checklist) AND one detection (a test or
   lane) per class, because either alone leaks. Place each via
   `incorporate-feedback`. Commit on the tip, one improvement per
   commit (`Skills: ...`), per `write-commit`.
5. **Evaluate the slice's cost.** Read the campaign's token spend and
   verification wall-clock the same way you read the fix commits.
   Cluster the waste by cause and trace each to its orchestration skill
   (`advance-plan`, `implement-change` and `orchestration.md`, the
   change-runner dispatch, `verify-lanes`):
   - Token cost: returns longer than their contract the caller had to
     hold; invariants repeated verbatim in every dispatch instead of
     read once from a campaign-context note; heavy models on
     mechanical phases a cheaper tier would serve.
   - Verification wall-clock: expensive lanes (coverage, full suite,
     high-case-count property tests) run per phase when per-deliverable
     or a reduced inner-loop count would do; cold runtime startup paid
     per lane instead of amortized over a persistent process; the full
     suite run where the owning namespace was enough.
   Land the cheap, clearly quality-preserving cuts (a lane moved to the
   landing wave, a reduced inner-loop count with the full count kept
   pre-land) as `Skills:` commits; recommend the design-level ones (a
   persistent test runner, model tiering, a return-discipline change)
   in the retrospective for the maintainer. A cut that risks the rigor
   does not land; say why and leave it.
6. **Record the retrospective.** Write a short slice retrospective at
   `docs/retro/<slice>.md`: the defect classes, their root causes, the
   improvements landed, the cost findings and what they saved or
   recommended, and what could not be prevented and why. Link the
   improvement commits and any ADRs. This is the auditable record that
   the loop ran.
7. **Close the loop.** The improvements are a change on the tip and
   land like any other (the maintainer advances `main`). A future slice
   that hits the same class is the signal the improvement was too weak;
   the next retrospective tightens it rather than re-fixing symptoms.

## Boundaries

Owns the post-slice improvement loop for the skill system itself.
Per-finding fixing is `apply-findings`; the round that produced the
findings is `run-review-round`; placing an improvement in its home is
`incorporate-feedback`. Does not edit landed product code; it edits the
recipes, tests, and lanes.

## Return

`retro <slice>: <c> defect classes, <i> improvements landed, <r> cost cuts landed, <r2> recommended`.
