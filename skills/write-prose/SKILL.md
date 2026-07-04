---
name: write-prose
description: Recipe for any English the agent produces in this project, commits, comments, docstrings, ADRs, design docs, skill bodies, changelog, error messages, project guides. Optimizes for terse, humanized prose with no em dashes and no AI tells. Invoke for any prose-writing activity.
user-invocable: false
---

# write-prose

Invoke this skill for **any English you produce** in this project:
commits, comments, docstrings, ADRs, design docs, skill bodies,
changelog, error messages, project guides. If you are about to write a
sentence of prose, invoke this first.

**Read this before writing:**
`skills/shared/references/style-foundations.md`, the canonical craft
reference. It distills Strunk and White's *Elements of Style* and
Zinsser's *On Writing Well* into the eight load-bearing rules, and
catalogs the AI tells the project bans.

**Also read for project-specific surfaces:**
`skills/shared/references/prose-style.md`, the project's surface
conventions (commit format, ADR format, code-comment idioms, the
no-process-ID rule).

## The stance: terse, humanized, no tells

Write like a person who respects the reader's time. Not like a machine
hedging its output. The default failure mode for AI prose is
over-production: too many words, decorative em dashes, sympathetic
throat-clearing, false-affinity adjectives (powerful, robust, seamless).
Reject that default at every sentence.

The four non-negotiables:

1. **Terse.** Omit needless words. The first draft is too long; the
   second is shorter. If a sentence can be cut and lose no meaning, cut
   it.
2. **Humanized.** Write like a careful person, not a helpful machine. No
   apologetic throat-clearing, no false brightness. No filler.
3. **No em dashes.** Never the em dash character. Restructure: a comma, a
   colon, a period, or a rewrite. Reaching for the em dash means the
   sentence is doing too much.
4. **No AI tells.** No arrow in prose, no hand-written or hand-rolled,
   no false-affinity adjectives, no hollow hedging, no rhetorical-question
   transitions, no in order to (just to), no utilize or leverage (just
   use), no There is openers, no trendy intensifiers (really, very,
   super). The full catalog is in `style-foundations.md`.

## The voice (every surface)

- Plain, direct, technical. No metaphors, no cleverness in names or
  headings. A reader six months from now gets the plain meaning on first
  read.
- Active voice; present tense for current behavior, past tense for
  history.
- State constraints and effects, not narrative.
- No marketing adjectives. If a property matters, state the measurement
  or the mechanism.

## No internal process identifiers

Code-facing prose describes the code and its behavior, never the process
that produced it. The following must never appear in source comments,
docstrings, test descriptions, or commit messages:

- phase, task, slice, or run identifiers;
- risk-spike or retro labels;
- campaign run artifacts (a plan, checkpoint, or decisions file by
  name);
- any other plan, campaign, or orchestration identifier.

A comment that defers to a later point or marks a test as failing on
purpose describes the state, not the process: write a placeholder until
the capability exists, or committed failing until the capability lands,
without the identifier.

Durable project references are allowed and encouraged: ADR references
and design-doc references by path, and named capabilities and modules.
The line: cite durable design artifacts, never ephemeral process IDs.

Why: comments and commit messages are read by future maintainers and
users. Campaign and plan IDs are internal scaffolding, noise outside the
run that produced them, and leaking them makes the code read as
generated.

## Per-surface rules

### Commit messages

The canonical commit form and category list live in write-commit
(sibling skill). In short: single line, category first, imperative mood,
within 70 characters, effect not diff, no body, no Co-Authored-By or any
other AI or tool attribution trailer, no version numbers, no em dashes.
Invoke write-commit before any commit operation.

### Source-code comments

Terse and sparse; `clojure.core` and the standard library are the
benchmark. The budget is concrete; see `prose-style.md` for the full
rule. In short: comment only the why (an ownership or lifetime
constraint at a boundary, why a branch is unreachable, a non-obvious
numeric decision), never the what; a comment block is at most three
lines; file density above one line per fifty code lines is a finding;
no banners (a section marker is a single `;;;; label` line, label only,
no prose underneath); no commented-out code, no change narration.

Per-language marker idiom:

- **C**: block comment at the top of the translation unit stating its
  single responsibility; inline comments only for constraints the code
  cannot say.
- **Zig**: `//!` file-top (one or two lines naming the file's
  responsibility), `///` doc comments on public declarations whose
  contract is not obvious from the signature, `//` for inline.
- **Clojure**: `;;;;` for section labels, `;;;` for namespace-level,
  `;;` for top-level forms, `;` for inline.
- **Elixir**: `@moduledoc` and `@doc` on public modules and functions,
  terse inline comments only for the why.

### Docstrings

The docstring is the documentation. Every public var and every fn gets
one. One to three lines for most fns: what it returns and the shape of
inputs that require it; never the mechanism. A long docstring (ten or
more lines) is earned by argument surface area (an options map, a spec,
a public primitive), never by a clever implementation. Rationale
belongs in an ADR; cite it by path. Deprecation lives in `:deprecated`
metadata or a one-line note.

Good: returns the vector of records matching the predicate.

Bad: goes through the index and filters; a ten-line docstring on a
one-line helper.

### ADRs

Written when the decision happens (invoke record-decision). Fields:
Title, Date, Context, Decision, Consequences, Alternatives. Title is the
decision itself, readable in the index without opening the file. Context
is neutral. Decision is one paragraph, present tense. Consequences
include the costs. Alternatives get their real strengths before the
rejection. About one screenful (40 to 60 lines).

### Design docs

The existing design docs are the exemplar. Match their terse, plain
voice. Each section has a single topic; cross-reference by path rather
than restating. A design doc is the spec; a substantive edit is usually
an ADR in disguise (use record-decision, then update the doc to cite
it).

### Skill bodies (skills/*/SKILL.md and references)

The skill body is the procedure an agent follows. Imperative voice (run
X, dispatch Y, do not Z). Numbered steps for sequential procedure,
bullets for non-ordered rules. Front-load the contract and the return;
defer the rationale. Skill prose is itself reviewed by check-style
against `prose-style.md`.

### Changelog entries

User-facing voice, written from the user's perspective. Category prefix
so the section groups. Lead with the effect, not the cause. One to five
lines once wrapped. Never reference internal cycle names, round numbers,
finding ids, or run paths. See write-changelog for the full recipe.

### User-facing error messages

An error message names the failing operation, never a bare error; leads
with what the user was doing; states the constraint that was violated;
suggests the next step if there is one.

Good: the file header claims a terabyte of data but the file is 12 KB.
The file is corrupt; the load was refused.

Bad: load failed.

Bad: error: invalid input.

### Project guides (CLAUDE.md, README.md)

Plain, direct, scannable. Headings every few paragraphs. Bullet lists
for enumerations. Code blocks for commands and paths. Update the guide
when a new skill or agent lands; update the README when the project
shape changes.

## Pre-flight before submitting any prose

Run the prose through this check (from `style-foundations.md`):

1. Read it aloud once. The ear catches clutter the eye misses.
2. Cut. If you cannot find words to cut, read again.
3. Search for the em dash character. If any appears, restructure.
4. Search for adverbs ending in ly. Most can go.
5. Search for that. Half of them are filler.
6. Search for the AI tells in `style-foundations.md`. Find one, fix one.
7. Search for internal process IDs (phase, task, slice, run, plan,
   retro labels). Rephrase to describe the code.
8. End each sentence on its point, not its preamble.
9. Check parallelism in lists and headings.

## Boundaries

Owns: the prose craft for every human-readable surface in the project.
Cites: `skills/shared/references/style-foundations.md` (the craft
canon) and `skills/shared/references/prose-style.md` (the project
surfaces). Siblings: write-commit owns the commit-specific form;
write-changelog owns the changelog recipe; record-decision owns the ADR
shape; check-style owns the prose-review dimension that enforces this
standard. Does not decide content (that is the owning skill's job); it
decides how the content reads.

## When in doubt

Read `style-foundations.md` again. If a prose choice is not covered
there, capture the question via capture-guidance for the next
incorporate-feedback pass; do not guess.
