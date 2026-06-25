---
name: add-language
description: Generate a write-<lang> recipe for a language outside the curated four
disable-model-invocation: true
---

# add-language

Role: the language meta-skill. Generates a draft write-<lang> recipe
from the skeleton for a language outside the curated set (C, Zig,
Clojure, Elixir), and wires it into the descriptor.

## Prerequisites

`.claude/project.edn` exists (run bootstrap-project first). The new
language is outside the curated four: the curated masters cover C, Zig,
Clojure, and Elixir. A real project need for the language, not
speculative.

## Procedure

Follow the shared add-* procedure in `docs/design.md` section 8.3:
detect the gap, interview for the skeleton's variable slot, generate
against the skeleton, wire into the descriptor, validate.

1. **Detect the gap.** Confirm the language is outside
   `#{:c :zig :clojure :elixir}` and that the project actually ships
   code in it. A speculative add is a finding, not an action.
2. **Interview for the discipline slot.** Ask one batch of at most three
   questions for what the write-<lang> skeleton's discipline slot needs:
   how the language expresses Functional Core / Imperative Shell, the
   failure model (errors as values, exceptions, result types), and the
   rigor that distinguishes correct code in this language (allocation,
   lifetimes, concurrency, the native edge when applicable). The
   skeleton fills four anchors and a numbered procedure per
   `docs/design.md` section 6; the discipline slot is the only part
   that varies.
3. **Generate the recipe.** Author `skills/write-<lang>/SKILL.md` from
   the skeleton: frontmatter (`name: write-<lang>`, model-invoked), the
   four anchors (the style-standard file, the architecture contract in
   `skills/shared/references/architecture.md`, the placement source from
   the descriptor's module map, the ADR log), and the numbered procedure
   (place it, then language discipline, then failure model, then verify
   like the lanes). Tests-first; terse comments; the public-text rule
   from `skills/shared/references/prose-style.md`. Reference FC/IS and
   the native-edge doctrine in `architecture.md` instead of restating
   them.
4. **Wire the descriptor.** Add the language keyword to `:languages` in
   `.claude/project.edn`. If the language brings a native edge, set
   `:architecture :native-edge?` true and add the native-edge citation.
   Add the language's lane commands to `:lanes :cheap`, `:wave`, and
   `:pre-land` (formatter, build, test, release gate).
5. **Validate.** Run one sample write and one review round using the new
   recipe: a writer writes a small unit, a reviewer runs the active
   dimensions over it. The recipe earns its place when the unit lands
   clean and the review finds nothing the recipe should have caught.
   Adjust the discipline slot from what the validation surfaced.
6. **Land.** One commit, category `Skills:`.

## Boundaries

Produces one project-local recipe under `.claude/skills/`, snapshotted
and owned by the project. The recipe is a candidate for promotion to a
toolkit master via incorporate-feedback; promotion is deliberate, never
automatic. It does not modify the curated four. It amends only the
descriptor and adds only the one recipe file. Atoms referenced: the
write-<lang> skeleton (in `docs/design.md` section 6),
`skills/shared/references/architecture.md`,
`skills/shared/references/prose-style.md`, the project descriptor.
Validation dispatches a writer (loading the new recipe) and a reviewer
(running check-* dimensions).

## Returns

One line: the new recipe path, the descriptor field amended, and the
validation verdict.
