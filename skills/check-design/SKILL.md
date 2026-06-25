---
name: check-design
description: Review dimension for the design language, view-spec data cleanliness, and UI experience targets. Invoked by reviewer agents over a UI shard when the descriptor has :ui? true.
user-invocable: false
---

# check-design

Role: review the assigned shard against the project's design language and
the data-oriented UI contract.

Failure model: the UI ships a stance violation, an uncleansable
view-spec, a frame-budget miss, or a state the design language requires
but the view omits.

This dimension is active only when the descriptor has `:ui? true`. The
authorities are the project's design docs (the design language, the
design system, the spatial or interaction layer, the view-spec, the
performance-UX targets, the token catalog); read them first. This skill
covers UI surfaces and the view-spec a feature produces. Code with no UI
surface is out of scope; return `NO FINDINGS`.

What follows is the sweep pattern, not the spec.

## Look for

1. **Maximalism over opinion.** A new option, toggle, or configuration
   knob where a good default would serve. Flag exposed settings that
   exist because the author would not commit to a choice. The inverse is
   also a finding: a hardcoded value that genuinely needs to be
   configurable (a theme, a device, a shortcut set) but was buried.
2. **Weak defaults.** A default that lands the user in a poor first
   experience: an empty state with no guidance, a sort or filter that
   hides the obvious result, an elevated widget shown with no path to
   reach it. The default is the product.
3. **Clutter, or wasted space.** A view-spec that piles widgets the
   workflow does not need, or one that wastes real estate where density
   would serve. Whitespace must buy focus; density must serve the
   workflow. Cite the widget that does not earn its place.
4. **Effects that do not justify themselves.** A visual or motion effect
   that decorates without improving understanding, feedback, or spatial
   context. A purely aesthetic touch is allowed only if it is subtle and
   rare; a gratuitous one is a finding.
5. **View-spec not data-clean.** A function, closure, or object in a
   widget map instead of plain data; an event handler that is a callback
   instead of an action value; a late-binding value inlined instead of a
   placeholder keyword. The view-spec must be serializable data that
   round-trips through the project's reader.
6. **Look encoded in the view-spec, or a raw value where a token
   exists.** Pixel colors, font sizes, spacing, or timings carried in
   the view-spec instead of semantic state the renderer interprets; a
   raw literal where a design token names the value. The view-spec names
   the type and references a token; the renderer owns the look.
7. **A new widget type that should not be.** A fresh widget type where
   an existing one parameterized would do, or a new type with no
   recorded decision when it is a real choice between alternatives. Cite
   the existing widget it duplicates.
8. **Frame-budget or latency risk on the UI data path.** A prepare or
   layout path too expensive to run per frame: a non-virtualized large
   grid, a linear pass over the whole set on every keystroke, a sort or
   projection recomputed when the input did not change. This overlaps
   check-performance; file it here only when the cost is born by the UI
   data path specifically.
9. **Diff-defeating churn.** A view-spec rebuilt so unchanged subtrees
   become fresh equal-but-distinct objects, or missing stable keys, so
   the renderer cannot match old to new. This makes the per-frame diff
   do work the design commits to avoiding.
10. **Platform leakage into the UI data.** A host-OS branch in the
    prepare, layout, or hit-test path, so the view-spec or bounds differ
    across platforms when the design commits to pixel identity.
11. **Missing states.** A view that renders only the happy path, omitting
    the empty, loading, error, or permission-denied state the design
    language requires.
12. **Focus or accessibility gap.** A focusable widget with no focus
    state, an unpredictable focus order, or focus lost across a
    transition; a treatment with no reduced-motion fallback, text that
    cannot scale, or contrast too low to read.
13. **Motion or depth that does not communicate.** A motion that answers
    no "what changed", a depth treatment that reveals no structure, or a
    use that contradicts the grammar (slide for navigation, zoom for
    focus, fade for appearance).
14. **Experience target missed.** A treatment whose feedback or
    interaction response would exceed the experience targets (immediate
    feedback, interaction response, consistent frame pacing). Overlaps
    check-performance; file it here when the UI experience target is
    what breaks.

## Ignore here

Pure style, naming, comments (check-style). Module boundary or leakage
(check-factoring), though IO inside a UI unit is worth a cross-reference
since it also breaks the data-clean contract. Back-end performance off
the UI data path (check-performance). A treatment an ADR records as
deliberate is not a finding; cite the ADR when one applies. External
design guidelines (the platform HIGs, Material) are loose inspiration,
not a visual checklist; never flag a divergence from their treatments.

## Severity

- `:high`. A break of a load-bearing contract: a view-spec that is not
  data-clean, a diff-defeating rebuild, platform leakage that breaks
  pixel identity, a frame-budget miss against the documented target.
- `:medium`. A stance violation that degrades the experience without
  breaking a contract: maximalism, weak defaults, clutter, an
  unjustified effect, look encoded in the view-spec, an avoidable new
  widget type.
- `:low`. A minor polish or consistency note.

## Level

`:correctness` for load-bearing contract breaks (they ship as a
user-visible glitch, a stale frame, or a cross-platform difference);
`:factoring` for stance violations; `:style` for polish. Most design
findings land in the factoring wave.

## Boundaries

Owns: the design language, the view-spec cleanliness, the UI experience
targets. Siblings: check-style owns naming and comments; check-factoring
owns module boundaries; check-performance owns back-end cost;
check-clarity owns the prose a UI surfaces.

## Return

An EDN vector of finding maps (shape in
`skills/shared/references/review-model.md`), one per defect, citing the
design principle or doc section it breaks. One finding per defect; do
not bundle. When the shard has none, return exactly:

```
NO FINDINGS
```
