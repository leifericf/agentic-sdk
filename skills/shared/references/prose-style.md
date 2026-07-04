# Prose style: the writing standard

Applies to everything written for humans: design docs, ADRs, skill
bodies, docstrings, code comments, commit and changelog messages.
Match the terse, plain voice this file sets.

**Read first for the general craft:** `style-foundations.md`. It
distills Strunk and White's *Elements of Style* and Zinsser's *On
Writing Well* into the eight load-bearing rules, plus the full
AI-tells catalog. This file narrows those rules to the project's
specific surfaces (commit format, ADR format, code-comment idioms, the
no-process-ID rule).

## Voice

- Plain, direct, technical. No metaphors, no cleverness in names or
  headings. A reader six months from now gets the plain meaning on
  first read.
- Active voice; present tense for current behavior ("the analysis
  module extracts the feature"), past tense for history ("the spike
  showed").
- State constraints and effects, not narrative. "Native library
  unloading is awkward, so baked artifacts are content-addressed"
  beats "we ran into an interesting problem with unloading".
- No marketing adjectives (powerful, robust, simple, seamless,
  blazing, modern). If a property matters, state the measurement or
  the mechanism.
- No em dashes, no arrows (`->` in prose), no other AI tells. The
  full catalog is in `style-foundations.md`. Never "hand-written" or
  "hand-rolled" in any public-facing text.

## No process IDs in source prose

Prose written for humans carries no execution scaffolding. No phase,
task, or slice IDs; no `plan.edn` or `decisions.edn` references; no
change, campaign, or run identifiers; no internal ticket or shard
names. A reader who is not running the agent fleet must understand
the text. The plan and the decisions log are working state for the
run, not material for the documentation. If a piece of prose only
makes sense alongside a plan entry, it belongs in the plan, not in a
doc, a docstring, or a commit message.

Names for deliverables and bookmarks are plain words for what shipped
(`catalog-core`, `diffing-renderer`), never numbered or coded
identifiers.

## Succinctness (Strunk and White, Zinsser)

The eight load-bearing rules from the foundations:

1. Cut. The first draft is too long.
2. Active voice. Name the agent of the action.
3. Concrete over general. Name the file, the type, the number.
4. Positive form. Say what IS, not what IS NOT.
5. Related words together. Modifier next to modified, subject next
   to verb.
6. End on the point. The end of the sentence is the stress position.
7. One tense per scope. Present for current behavior, past for
   history.
8. No decoration. No adverb that repeats the verb; no adjective
   that softens the claim.

The test: would deleting this sentence cost a future reader a wrong
decision or a re-derivation? If not, delete it. One idea per
sentence; one topic per paragraph. Concrete beats general: a named
type carries more than "a handle type". Do not restate what an
adjacent artifact already says; cite it by path and add only what is
new here.

## Decision records (ADRs)

Architecture decisions are ADRs, written when the decision happens.
`record-decision` writes them. The design principles they serve remain
in the design docs and are not duplicated per ADR.

- Fields: Title, Date, Context, Decision, Consequences,
  Alternatives. No status field; a decision stands until a later
  record supersedes it by name.
- Title is the decision itself, readable in the index without opening
  the file ("Catalog entities are plain maps, not records", not
  "Catalog modeling investigation").
- Context is neutral: the facts and constraints as they stood, no
  foreshadowing of the answer.
- Decision is one paragraph: what and how, present tense.
- Consequences include the costs; a record listing only upsides is
  advertising.
- Alternatives get their real strengths before the rejection; a
  strawman documents nothing.
- One screenful (about 40 to 60 lines). If it cannot fit, it is
  probably several decisions.

## Code comments

The north star is `clojure.core`: inline comments are rare one-liners
(roughly one per fifty to one hundred twenty code lines), comment walls
are absent, and documentation lives in docstrings.

- Comment only what the code cannot say: an ownership or lifetime
  constraint at a language boundary, why a branch is unreachable, a
  non-obvious algorithmic or numeric decision. Comment the why, never
  the what; clear names carry the meaning.
- A comment block is at most three lines; one line is preferred. Four
  lines is the essay threshold and a finding. File inline comment
  density above roughly one line per fifty code lines is a finding.
- Delete or move. Delete a comment that restates the next line,
  describes mechanism, or narrates history ("used to", "previously").
  Move a design rationale to an ADR and cite the ADR by path.
- No banners. No `---` rules, no ASCII art, no decorated separators.
  A section marker, when used, is a single `;;;; label` line (or `;;`
  then `;`), label only, no prose underneath.
- No commented-out code; the VCS holds history. A `(comment ...)`
  form is REPL scratch under a `;;;; Scratch` marker at the foot of the
  file, not inline narration.

## Docstrings

The docstring is the documentation; inline comments are the exception.
Every public var and every fn gets one.

- One to three lines for most fns: what it returns and the shape of
  inputs that require it. Never the mechanism.
- A long docstring (ten or more lines) is earned by argument surface
  area: an options map, a spec or DSL, a public API primitive. Length
  tracks the interface, not the implementation. A long docstring that
  explains a clever implementation is a finding.
- A docstring that narrates mechanism, history, or rationale is a
  finding. Rationale belongs in an ADR; cite it by path. Deprecation
  lives in `:deprecated` metadata or a one-line note.

## Commit messages

The canonical commit-message style lives in `write-commit`. In short:
single line, category first (`Category: Imperative subject`),
imperative mood, no trailing period, within 70 characters, effect not
diff, no body, no `Co-Authored-By` or any other AI or tool attribution
trailer, no version numbers, no em dashes. See `write-commit` for the
category list, good-versus-bad examples, word-choice rules, and the
pre-flight check.
