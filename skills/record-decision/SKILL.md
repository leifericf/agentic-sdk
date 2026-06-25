---
name: record-decision
description: Write an ADR when an architecture decision is made, in conversation or when work settles a real choice between alternatives.
user-invocable: false
---

# record-decision

Architecture decisions are ADRs, written when the decision happens, not
reconstructed later. The store and format come from the descriptor
(`:adr`; default `docs/adr/`, nygard). The fields and voice are fixed
in the "Decision records" section of `references/prose-style.md`.

## Procedure

1. **Decide whether to record.** Record when a discussion ends in "do
   X, not Y" and X constrains future work, or when your work settles a
   real choice where the rejected option was reasonable, or when you
   reject a review finding as deliberate and no record covers it. Do
   not record choices with one reasonable option, reversible
   implementation details, or anything an existing ADR covers
   (supersede it instead if the answer changed).
2. **Next number.** Read `docs/adr/README.md` (create it as `[]` if
   missing), take the highest number, add one, two digits. The first
   ADR is `01`.
3. **Write `docs/adr/NN-slug.md`:**

   ```markdown
   # ADR NN: <the decision, readable in the index>

   Date: YYYY-MM-DD

   ## Context
   ## Decision
   ## Consequences
   ## Alternatives
   ```

   One screenful (about 40 to 60 lines). Context is neutral, no
   foreshadowing. Decision is one paragraph, present tense.
   Consequences include the costs. Alternatives get their real
   strengths before the rejection. Superseding: say "Supersedes ADR
   NN" in the Decision; never edit the old record.
4. **Add the one-line row** to the `docs/adr/README.md` index.
5. **Route the rule too.** If the decision changes how code is written
   (a banned idiom, a new constraint), add the rule to the right
   reference or skill. The ADR holds the why; the reference holds the
   how. Cross-cite.
6. **Commit.** `Docs: Record ADR NN, <short title>` (or fold into the
   change's commit series when recorded mid-change).

## Boundaries

Owns the why of an architecture decision. The enforced how lives in a
reference or lint rule, routed in step 5. Bulk promotion of captured
guidance is `incorporate-feedback`. Does not record reversible
implementation details or one-option choices.

## Return

`docs/adr/NN-slug.md`.
