# Review model

Review doctrine. It owns the HOW we judge code and prose. The WHAT under
review, the architecture shape, lives in `architecture.md`. Fan-out,
escalation, and dispatch mechanics live in `orchestration.md`.

## 1. One dimension per reviewer

A reviewer runs exactly one dimension on one module shard. Dimensions never
mix in one reviewer.

Why:

- **Each reviewer stays narrowly rules-cited.** A `check-security` reviewer
  loads the security rules and the untrusted-input catalogue; it does not
  also load the style guide. Narrow loading is cheaper and more accurate.
- **No anchoring.** A reviewer that finds a bad factoring does not then read
  the rest of the shard primed to find style nits. One lens, one pass, no
  carryover bias.
- **Parallelizable.** Dimensions on the same shard run concurrently; the
  orchestrator fans them out and folds the findings.

The reviewer returns EDN findings, or `NO FINDINGS`. Nothing else.

## 2. The dimension catalog

Each dimension is a `check-<dimension>` primitive with the same shape: a
one-sentence failure model, ordered look-fors, ignore-rules, severity, and
level. A project's `:dimensions-active` selects the subset.

| Dimension | Looks for | Conditional |
|---|---|---|
| `check-correctness` | logic bugs, nil/empty/boundary, arithmetic | always |
| `check-factoring` | module boundaries, dependency direction, duplication | always |
| `check-style` | naming, idiom, comment debt, AI tells | always |
| `check-conformance` | behavior matches dossier, ADRs, spec | always |
| `check-security` | untrusted input to unsafety, traversal, bypass | always |
| `check-performance` | hot-path allocation, budget breaks | always |
| `check-portability` | platform branches, endianness, FS semantics | always |
| `check-memory` | ownership, lifetimes, leaks, GC safety | C and Zig only |
| `check-design` | design language, view-spec cleanliness | UI projects only |
| `check-clarity` | reader experience, jargon, pacing | prose and docs only |

The reviewer agent's dimension allowlist is the floor per module type and
the descriptor tunes it, proven on memory-unsafe language projects that
swap `check-memory` in. A C/Zig project activates `:memory`; a UI project
activates `:design`; a library project may run `:style` alone.

## 3. The level discipline (software)

In a fix loop, levels never mix in one editor wave. The order is fixed:

1. **Correctness** first. Logic, nil, boundary, arithmetic. The code has to
   be right before anything else matters.
2. **Factoring** next. Module boundaries, dependency direction, duplication.
   The code has to be in the right place before polish.
3. **Style** last. Naming, idiom, comment debt.

Why the order is fixed:

- **Polishing code that is about to move is wasted work.** A style fix on a
  function the factoring wave is about to extract or delete is thrown away. A
  factoring fix on a function the correctness wave is about to rewrite is
  thrown away. Work each wave at its level and the editor never throws work
  away.
- **The diff stays legible.** A factoring fix in the same wave as a
  correctness fix obscures the correctness diff. The reviewer, the
  maintainer, and the next reader can read each wave for one thing.

This parallels the prose review levels: developmental, then content, then
line, then copy. Structure before content before sentence before punctuation.
Jump levels and the work redoes itself.

## 4. The finding shape

A finding is an EDN map. The keys are fixed:

```edn
{:dimension   :security
 :severity    :high
 :level       :correctness
 :file        "modules/domain/audio/src/sm/native/decode.clj"
 :evidence    "lines 42-58: float-array called on caller-supplied seq without bounded-count"
 :suggestion  "bounded-count the seq to the native ceiling plus one; reject as data before float-array"
 :rule        "bound-untrusted-seq-before-realizing"}
```

- `:dimension` names the dimension that produced the finding (one of the
  catalog).
- `:severity` describes damage (high, medium, low).
- `:level` names the editor wave that fixes it (correctness, factoring,
  style).
- `:file` is the path to the shard under review.
- `:evidence` is the concrete location and what is wrong, quoted from the
  source. A finding without evidence is unactionable and is dropped at
  triage.
- `:suggestion` is the direction of the fix, not the fix itself. The editor
  writes the fix.
- `:rule` names the rule the finding cites, so the lint projection can
  promote a repeated finding into an enforced rule.

## 5. Severity vs level: orthogonal

Severity and level answer different questions and vary independently.

- **Severity describes damage.** High: the code is wrong or unsafe in a way
  that breaks the build, the user, or the contract. Medium: the code works
  but is fragile or wrong in a corner. Low: the code is fine but ugly or
  stale.
- **Level describes when.** Correctness fixes run in the first editor wave;
  factoring in the second; style in the third.

A high-severity style finding is still fixed in the style wave; severity does
not promote it into correctness. A low-severity correctness finding still
runs in the correctness wave; low severity does not demote it. Triage orders
the punch list by level first, then by severity within level.

## 6. The round cap

At most two review rounds per phase. The first round catches most findings;
the second catches what the first round's fixes disturbed, plus anything
missed. A third round inside a phase churns the code without finding enough
to justify itself.

The exception is `audit-code`, which is uncapped: it runs rounds until a
round finds nothing new. Audit is the mode where the goal is exhaustion, not
delivery.

Low and style-only findings do not buy a third round. They become forward
tasks: a row in the backlog, addressed in a later phase, not a reason to
extend this one.

## 7. The tool split as a review concern

The tool split (reviewers read-only, editor the sole mutator, verifier
bash-heavy) is in `orchestration.md`. Restated here because it makes the
level discipline enforceable.

- **The reviewer is read-only.** No Edit, no Bash, no Write. A reviewer that
  can edit is a reviewer that will edit, and an edit during review is a
  second editor wave running untracked. The reviewer reads, returns
  findings, stops.
- **The editor is the sole mutator.** One editor per module per level wave.
  The editor reads the punch list, applies the fixes, returns the landed
  change id. No other role touches source during a fix loop.
- **The verifier is bash-heavy and has no judgment.** It runs the
  deterministic lanes (format check, lint, build, tests) and reports pass or
  fail with the first error only. It does not triage, does not suggest, does
  not edit. A green verifier is the gate the editor's wave has to pass before
  the next wave runs.

A reviewer that cannot edit cannot sneak a style fix into a correctness wave.
An editor that cannot run the lanes cannot declare its own work green. A
verifier that cannot judge cannot rationalize a failure away. The split is
the enforcement.
