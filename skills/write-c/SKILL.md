---
name: write-c
description: Recipe for writing C in the runtime, module placement and the ownership and control-flow discipline. Invoked by writer agents when a C unit is dispatched.
user-invocable: false
---

# write-c

Write new C for the runtime. The standard is
`skills/write-c/references/c-style.md` (read it first). The architecture
it implements is Functional Core / Imperative Shell; the C expression of
that split, and the native boundary contract, live in
`skills/shared/references/architecture.md`. Placement comes from the
project's module map (the `:architecture :modules` entry in the
descriptor). The why behind the constraints is the ADR log: scan it
before designing anything that feels constrained by an unexplained rule.

## Procedure

1. **Place it.** Find the owning translation unit in the module map;
   read that TU end to end before adding to it. Start a new TU only when
   the responsibility is genuinely new, name it after the
   responsibility, and add it to the map and the build. Respect the
   dependency direction the map records; a new cross-module include is a
   cross-module escalation, not a local convenience.
2. **C discipline.** The load-bearing rules, in order:
   - **Ownership decision up front.** Decide ownership of every
     allocation before writing: runtime-managed (allocated through the
     runtime's owner, with temporaries pinned across allocation points)
     or caller-owned (malloc, freed on every error path). Decide before
     writing, never retrofit. When a helper allocates twice, write the
     pin or ownership guard for the first before allocating the second.
     Never free a runtime-managed value through `free`; never let a
     caller-owned struct hold the only reference to a runtime-managed
     value across an allocation point.
   - **Explicit control flow.** Validate early, return early. Invalid
     input and failure cases exit at the top; the normal path runs at
     low indentation, not nested inside success conditionals. One
     important action per line: no assignment inside conditionals, no
     nested ternaries, no multi-effect loop conditions, no short-circuit
     chains that hide real work. When resources accumulate, use a single
     cleanup path (one `goto cleanup` label) that is safe under partial
     initialization, not mirrored cleanup blocks in every error branch.
   - **Deterministic cleanup.** Every caller-owned allocation has a free
     on every path, including the error path. Buffers travel with
     lengths; bounds checks sit adjacent to the access they protect.
     Guard size and index arithmetic before allocation and pointer math
     (a `SIZE_MAX` overflow check precedes the multiply it protects), so
     a hostile length degrades as data instead of overflowing before the
     guard fires.
   - **Error classes.** Every failure path picks its class consciously:
     recoverable for anything a caller can catch and handle; a host or
     limit error for IO and capacity; abort only with a comment naming
     why recovery is impossible. User input must never reach an abort.
     Diagnostics name the failing operation and the reason, never a bare
     error or failed.
3. **Failure model.** The core computes a plan and returns it; the
   caller acts. Untrusted input (a file from disk, bytes that cross the
   native edge, a length field a payload controls) is expected, not
   exceptional: validate it, return an error class when it is malformed,
   let the shell surface the diagnostic. A decision in C is an enum, a
   small struct, or a filled out-parameter; make the caller's switch
   explicit rather than burying the choice inside the effectful loop.
   No strict-aliasing violations, no pointer punning, no reliance on
   signed overflow.
4. **Verify like the lanes.** Build with the project's cheap lanes first
   (the compile lane with warnings as errors is the floor), then the
   module's tests, then the full cheap lane set from the descriptor. If
   you wrote pointer-heavy or ownership-sensitive code, run the
   sanitizer and leak lanes yourself before the review wave; a
   use-after-free or a leak found in the wave is one the writer should
   have caught.

## Tests-first

A failing test against the intended behavior goes in before the
implementation; write it, or coordinate with the write-tests dispatch.
Never land implementation without the test that pins it. Edge cases are
the point: empty, single, boundary sizes, values near the type limits,
and malformed input that must degrade as data rather than crash. See
write-tests for surface selection and the teeth every assertion needs.

## Boundaries

Owns: the C translation unit, its ownership and cleanup discipline, and
the error classes it emits. Cites: the architecture contract in
`skills/shared/references/architecture.md` for the Functional Core /
Imperative Shell split and the native boundary contract; the module map
for placement; the ADR log for the why. Siblings: write-tests owns the
test surface selection; write-zig owns the Zig side of a C ABI edge;
write-prose owns the prose standard the comments ride on. Does not
invent ownership rules that contradict the style standard or an ADR, and
does not carry an unexplained constraint forward without scanning the
decision log first.

## Comments and public text

Terse and sparse; comment the why, never the what. Comment only what the
code cannot say: an ownership or lifetime constraint at a language
boundary, why a branch is unreachable, a non-obvious algorithmic
decision. No decorative banners, no commented-out code, no change
narrative (the VCS holds history). A comment block longer than a few
lines, or comments outweighing the code they sit in, is itself a
finding.

Public-facing text rule: never describe code as hand-written or
hand-rolled in docstrings, docs, or changelog lines, and never carry an
internal process identifier (a phase, task, slice, or run label) into a
comment or commit. See write-prose.
