---
name: add-tech
description: Add a non-language concern, a lane entry, a spine task, or a hook
disable-model-invocation: true
---

# add-tech

Role: the tech meta-skill. Adds a non-language concern the descriptor
does not cover: a lane entry, a spine task, or a hook policy. Generates
the matching artifact from its schema and wires it into the descriptor.

## Prerequisites

`.claude/project.edn` exists (run bootstrap-project first). A real
project need the curated config does not cover: a new build target, a
deterministic clerical task the model does by hand, or a policy that
belongs in a hook rather than a prompt.

## Procedure

Follow the shared add-* procedure in `docs/design.md` section 8.3:
detect the gap, interview for the skeleton's variable slot, generate
against the skeleton, wire into the descriptor, validate. The artifact
kind depends on the gap.

1. **Detect the gap and classify it.** One of three kinds:
   - **Lane entry:** a new command in `:lanes :cheap`, `:wave`, or
     `:pre-land` (a formatter, a linter, a release gate, a deep test
     tier).
   - **Spine task:** a new deterministic clerical task that joins the
     interface in `skills/shared/references/spine.md`. The model does
     it identically every time, so it earns promotion to the spine.
   - **Hook policy:** a new template in `:hooks` that enforces a policy
     at the tool boundary, the way `format-on-write`, `deny-secrets`,
     `require-tests-before-land`, and `mcp-first` do.
2. **Interview for the schema.** Ask one batch of at most three
   questions for what the artifact's schema needs:
   - For a lane entry: the command, the tier (cheap, wave, pre-land),
     and what failure stops.
   - For a spine task: the invocation name and arguments, what it reads,
     what it writes, the invariant it owns, and the exit contract (one
     line on stdout, exit code). It must be pure and deterministic on
     the unambiguous cases and refuse with a structured escalation on
     the ambiguous ones.
   - For a hook policy: the trigger (PreToolUse or PostToolUse, on
     which tools), the check, and the deny or warn action.
3. **Generate the artifact.**
   - For a lane entry: no new file; the command lands in the descriptor.
   - For a spine task: author the task entry against the interface in
     `spine.md`. It joins the five stable names (triage, integrate,
     run-status, compile-rules, lint) or extends the catalog with a new
     name that answers the same contract shape.
   - For a hook policy: author the hook template into the toolkit's hook
     templates, parameterized by the descriptor.
4. **Wire the descriptor.** Add the lane command to `:lanes`, the spine
   task name to the spine adapter, or the hook key to `:hooks`. The
   descriptor is the single tuning valve; the artifact is dead config
   without a descriptor entry.
5. **Validate.** Run the artifact once dry: the lane command green on a
   clean tree; the spine task run on a sample input with the expected
   exit contract; the hook policy firing on a sample trigger and
   passing on a clean one. Tune from what the validation surfaced.
6. **Land.** One commit, category `Skills:` (or `Build:` for a pure
   lane addition).

## Boundaries

Produces one project-local artifact (a spine task, a hook template) or
one descriptor amendment (a lane entry). The artifact is a candidate
for promotion to a toolkit master via incorporate-feedback; promotion
is deliberate, never automatic. It does not add a language (that is
add-language) or a review dimension (that is add-dimension). It amends
only the descriptor and adds only the artifact file. Atoms referenced:
`skills/shared/references/spine.md` for the spine task interface,
`docs/design.md` section 9 for the hook templates, the project
descriptor.

## Returns

One line: the artifact kind and path (or the descriptor field for a
lane entry), the descriptor field amended, and the validation verdict.
