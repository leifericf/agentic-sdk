---
name: check-clarity
description: Review dimension for reader experience, jargon, and pacing over prose and docs. Invoked by reviewer agents over a prose shard when the descriptor activates :clarity.
user-invocable: false
---

# check-clarity

Role: review the assigned prose shard for the reader's experience.

Failure model: a reader in the intended audience cannot follow the text
on the first read, because the structure buries the point, the jargon
excludes, or the pacing stalls.

This dimension covers prose written for humans: design docs, ADRs,
reference material, docstrings surfaced as docs, READMEs, guides. Code
is out of scope. When the shard is code, return `NO FINDINGS`. The prose
standard is `skills/shared/references/prose-style.md`, backed by
`skills/shared/references/style-foundations.md`; the deterministic
`lint` spine task already catches the mechanical tells (the em dash, the
prose arrow, the process ID, the banner line). This pass catches the
clarity judgment a regex cannot.

## Look for

1. **Audience fit.** The text assumes knowledge the intended audience
   does not have, or over-explains what they already know. Name the
   audience the design docs declare; judge against that audience, not an
   idealized expert.
2. **Buried point.** The load-bearing claim sits in the middle of a
   paragraph or after the evidence, when the reader needs it first. Lead
   with the point; end on the stress.
3. **Jargon and undefined terms.** A term of art used before its
   definition, or a term the project's vocabulary defines differently
   from common usage. The first use earns a definition; later uses
   inherit it.
4. **Pacing and paragraph focus.** A paragraph that carries more than
   one idea; a section that circles its point; a sequence of loose
   sentences where subordination or contraction would carry the same
   meaning in half the words.
5. **Signposting and structure.** A heading that does not match the
   section's topic; a cross-reference to a section that does not exist
   or does not say what is claimed; a list whose items are not parallel
   in form.
6. **Missing concrete grounding.** A claim stated in the abstract when a
   named file, type, number, or call site would carry it. General terms
   where concrete ones serve.
7. **Unexplained dependency.** The text assumes an earlier section the
   reader has not read, or builds on a decision recorded elsewhere
   without a pointer. A reader landing mid-document must find the link.
8. **Passive voice and hidden agency.** A sentence that hides who or
   what acts, when naming the agent would clarify.

## Ignore here

Mechanical prose tells the `lint` spine task gates (em dashes, prose
arrows, process IDs, banners). Line-level voice drift against the
standard (check-style owns voice; clarity owns comprehension).
Correctness of code claims (check-correctness, check-conformance).
Factoring of the codebase the docs describe (check-factoring).

## Severity

- `:high`. A reader in the audience cannot reach the point at all, or is
  led to a wrong understanding.
- `:medium`. A reader reaches the point but stumbles: a buried lead,
  undefined jargon, a circling paragraph.
- `:low`. A polish note that would tighten the read.

## Level

`:style`. Clarity findings land in the style wave, alongside the other
prose-judgment findings.

## Boundaries

Owns: the reader's comprehension, pacing, and structural clarity.
Siblings: check-style owns voice and the line-level craft against the
prose standard; check-conformance owns whether the prose's claims match
the code; the `lint` spine task owns the mechanical tells; `write-prose`
owns the standard this dimension enforces.

## Return

An EDN vector of finding maps (shape in
`skills/shared/references/review-model.md`), one per defect, each with
the location and the comprehension failure. When the shard has none,
return exactly:

```
NO FINDINGS
```
