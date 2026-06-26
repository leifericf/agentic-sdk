---
name: check-style
description: Review dimension for naming, idiom, comment debt, and AI tells against the codified per-language and prose standards. Invoked by reviewer agents over one module shard.
user-invocable: false
---

# check-style

Role: review the shard against the codified standards.

Failure model: the code reads differently from its surroundings in a way
that costs a future reader, or carries an AI tell or process scaffolding
that should never reach committed prose.

The standards are cited, not duplicated:

- Prose (docs, ADRs, docstrings, comments, commit and changelog
  messages): `skills/shared/references/prose-style.md`, backed by
  `skills/shared/references/style-foundations.md`.
- C: `skills/write-c/references/c-style.md`.
- Zig: `skills/write-zig/references/zig-style.md`.
- Clojure: `skills/write-clj/references/clj-style.md`.
- Elixir: `skills/write-elixir/references/elixir-style.md`.

Those files are the checklist, but apply them for judgment, not for
what a lane already gates. An em dash, a prose arrow, a decorative
banner, or a process ID is the `lint` spine task's job; a format nit is
the formatter's; an unused binding is the language linter's. This is the
nit tax the dimension rejects: flag only the style judgment no lane can
make.

## Look for

1. **Inconsistency with the surrounding file.** A function that names,
   comments, or structures differently from its unit without reason.
2. **Pure, shell, or native leakage as a style signal.** The
   highest-value style finding, because the three-way split is the
   load-bearing structure. IO, a native call, a persistence write, or
   state mutation inside a core unit; pure decision logic buried in the
   shell; domain logic inside a native wrapper. Factoring owns the
   structural fix; style flags it when the seam is readable as a style
   break.
3. **Boundary contract drift.** A native call whose signature disagrees
   with the edge contract, or that reaches into a handle's internals
   from the calling language.
4. **Comment debt.** An ownership or lifetime constraint that is true
   but unstated where it matters (an allocator ownership transfer, a
   borrow duration, a transaction invariant); comments that narrate the
   next line instead of stating a constraint; stale comments
   contradicting the code.
5. **Leaked process identifiers.** A phase, task, slice, run, or plan
   identifier, a `plan.edn` or `decisions.edn` reference, or an internal
   ticket label in a comment, docstring, test description, or commit
   message. The rule and the allowed durable refs (ADR, the docs tree)
   live in `write-prose`. Flag the leak; suggest the code-state
   rephrasing.
6. **Naming that misleads.** A name that promises a behavior the code
   does not deliver, or a name whose meaning has drifted from the domain
   the surrounding code uses.
7. **Macro and metaprogramming overreach.** A macro or a compile-time
   construct doing work that belongs in a data function, so the result
   is unreachable without it.

When the shard includes prose, the standard is `prose-style.md`. Apply
it the same way: the AI-tells catalog, the no-em-dash and no-arrow
rules, the no-process-ID rule, and the succinctness canons. The `lint`
pre-pass catches the mechanical tells; this pass catches the judgment
calls a regex cannot.

## Ignore here

Logic correctness (check-correctness). Module boundaries and
duplication (check-factoring). Conformance to the spec or an ADR
(check-conformance). Performance (check-performance). Portability
(check-portability). Memory safety (check-memory). Generated artifacts
under a generated-output directory, anything an ADR records as
deliberate, and committed test fixtures.

## Severity

- `:low`. Most style findings.
- `:medium`. A misleading comment or name that could cause a future
  correctness mistake, or a pure, shell, or native leakage that will
  make a branch untestable.

## Level

`:style`. Style findings land last, after correctness and factoring.
Never block on them alone; low and style-only findings become forward
tasks, not a reason to extend a round.

## Boundaries

Owns: how the code and prose read against the codified standards.
Siblings: check-factoring owns where the code lives; check-correctness
owns whether it is right; check-conformance owns whether it matches the
spec; the `lint` spine task owns the deterministic mechanical pre-pass;
`write-prose` owns the prose standard this dimension enforces.

## Return

An EDN vector of finding maps (shape in
`skills/shared/references/review-model.md`), one per defect. When the
shard has none, return exactly:

```
NO FINDINGS
```
