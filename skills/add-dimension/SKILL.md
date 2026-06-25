---
name: add-dimension
description: Generate a check-<dimension> recipe for a bug class the active set misses
disable-model-invocation: true
---

# add-dimension

Role: the dimension meta-skill. Generates a draft check-<dimension>
recipe from the dimension shape for a bug class the active dimensions
miss, and wires it into the descriptor.

## Prerequisites

`.agentic-sdk/project.edn` exists (run bootstrap-project first). A real bug
class the active `:dimensions-active` set missed, the way a
memory-unsafe language needs check-memory. The bug class is general,
not a one-off.

## Procedure

Follow the shared add-* procedure in `docs/design.md` section 8.3:
detect the gap, interview for the skeleton's variable slot, generate
against the skeleton, wire into the descriptor, validate.

1. **Detect the gap.** Confirm the bug class is not already covered by
   an active dimension. The catalog in `docs/design.md` section 11 is
   the reference: check-correctness, check-factoring, check-style,
   check-conformance, check-security, check-performance,
   check-portability, check-memory, check-design (UI only),
   check-clarity. A one-off bug is a fix-bug item, not a dimension.
2. **Interview for the shape.** Ask one batch of at most three questions
   for what the dimension shape needs: the one-sentence failure model
   (what goes wrong), the ordered look-fors (where to look, in priority
   order), the ignore-rules (what not to flag), the default severity,
   and the editing level (correctness and factoring at level 1,
   conformance and security at 2, style at 3, lint and render at 4).
3. **Generate the recipe.** Author `skills/check-<dimension>/SKILL.md`
   from the dimension shape: frontmatter (`name: check-<dimension>`,
   model-invoked, read-only reviewer), the one-sentence failure model,
   the ordered look-fors, the ignore-rules, the severity, the level,
   and the return contract (`NO FINDINGS` or the EDN finding shape from
   `skills/shared/references/review-model.md`). The reviewer agent
   loads this recipe per shard.
4. **Wire the descriptor.** Add the dimension keyword to
   `:dimensions-active`. If the dimension requires a condition (the way
   check-design requires `:ui? true`, or check-memory requires a
   memory-unsafe language), state the condition in the recipe and
   confirm the descriptor satisfies it.
5. **Validate.** Run one review round using the new dimension: a
   reviewer runs it over a shard that exhibits the bug class. The
   dimension earns its place when it catches the class the active set
   missed and does not flood the punch list with false positives. Tune
   the look-fors and ignore-rules from what the validation surfaced.
6. **Land.** One commit, category `Skills:`.

## Boundaries

Produces one project-local recipe under `.agentic-sdk/skills/`, snapshotted
and owned by the project. The recipe is a candidate for promotion to a
toolkit master via incorporate-feedback; promotion is deliberate, never
automatic. It does not modify the curated catalog. It amends only the
descriptor and adds only the one recipe file. `check-` is reserved for
review dimensions only (model judgment, read-only reviewer);
deterministic gates are verify-lanes, never check-. Atoms referenced:
the dimension shape (in `docs/design.md` section 11),
`skills/shared/references/review-model.md` for the finding shape, the
project descriptor. Validation dispatches a reviewer (loading the new
recipe).

## Returns

One line: the new recipe path, the descriptor field amended, and the
validation verdict.
