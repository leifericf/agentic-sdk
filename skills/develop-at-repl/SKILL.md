---
name: develop-at-repl
description: The project's interactive eval loop. Connect once, evaluate small forms, read the value, adjust. The discipline the write-time recipes run inside.
user-invocable: false
---

# develop-at-repl

The interactive eval loop is the primary development rhythm, not a
debugging aside. You write a form, evaluate it, read the value it
returns, and adjust: data in, data out, no setup ceremony. Batch test
runs are the exception, reached for at landing boundaries (see "When to
leave the loop"), not the rhythm of the work.

The loop's host depends on the language: nREPL for Clojure, IEx for
Elixir. C and Zig have no eval loop; their fast inner cycle is a small
throwaway harness built and run in seconds (see `run-spike` for the
discipline). The standard for code produced here is the relevant
`write-<lang>` recipe; the boundary contract is
`references/architecture.md`; the module layout and dev loop live in
the descriptor's module map. This skill is the loop those recipes run
inside.

## Connect once

Start the loop's host (nREPL, IEx) and attach the editor's client. One
long-lived session; you do not restart it per change. The running image
is the unit of work. Require the namespace you are shaping; reload it
(`:reload`, or IEx `recompile`, or a fresh harness run) after edits to a
dependency.

If the project has no live host yet (a C or Zig unit, or a Clojure or
Elixir project before the dev alias is wired), say so and fall back to
reasoning over the source plus a fast harness. Do not fabricate a
running image.

## Procedure

1. **Evaluate one small form.** Send the smallest form that answers the
   next question: a constructor call, a query against a literal input,
   a pure transform over a hand-built value. Read the returned value.
2. **Inspect the data, do not re-run a suite.** The pure core takes
   data and returns data; the answer is the value at the prompt. Drill
   with the host's tools (pretty-print on a deep structure, an
   inspector tap, `keys` and `get-in`, or a harness printf). Do not
   boot the whole application to see one function's output.
3. **Adjust and re-evaluate.** Edit the form or the source, re-eval,
   read again. The cycle is seconds, not a test-suite wait. Let the
   shape of the data drive the next form.

Hold the loop to pure data wherever the work allows. The pure core is
built for this: no clock, no atom, no IO, so a literal input fully
determines the output and the value at the prompt is the whole truth.

## The scratch surface

Keep the exploration in the source, not in a transient buffer. A
comment block at the foot of the namespace (Clojure `(comment ...)`,
Elixir a `## dev` section, C or Zig a guarded `SELF` main) holds the
forms you evaluated to shape the code. The block does not load at
compile time, so it is free to carry half-formed forms; it is a living
record of how the module is meant to be driven. Keep it honest: prune
dead forms before landing, leave the ones that document intended use. A
block that has rotted into noise is a style finding (`check-style`).

## The native edge, live

Where the runtime supports live redefinition across the native edge (a
foreign-function or NIF wrapper that recompiles the native body on
demand and caches the artifact by content), the loop extends to native
functions: tweak the body, re-evaluate the form, re-call, no separate
build step. The gotchas all follow from one rule: a handle refers into
the native image that produced it.

- **Never hold a handle across a recompile.** Re-evaluating the native
  form, or anything that reloads the library, invalidates every handle
  it minted. Re-acquire: call the constructor again for a fresh handle,
  do not reuse the one bound before the recompile.
- **Re-acquire after any body change.** Treat a bound handle as dead
  the moment you touch the body that minted it. Bind it fresh, use it,
  free it within the same uninterrupted stretch.
- **A failed compile keeps the last good definition.** If the edited
  body does not compile, the host surfaces a structured diagnostic and
  the previously loaded definition stays live. The loop does not break;
  read the diagnostic, fix the body, re-evaluate. Do not assume a green
  call means the latest edit compiled; confirm the eval reported
  success.
- **Owned returns are safe to keep; handles are not.** A value copied
  into managed memory survives a recompile. A handle does not.

Bracket each native run in the scratch block: acquire, call, free, in
forms you evaluate together, so a recompile never strands a live
handle.

## How the loop and TDD compose

The loop is where red-green happens; the commit is where it lands.

1. Write the failing assertion as a form and evaluate it. It returns
   false, or the call throws. That is red, seen in a second.
2. Shape the implementation form by form until the assertion returns
   true. That is green, seen the same way.
3. Move the proven form into the test suite and the implementation into
   the module. The scratch block was the draft; the test file is the
   record.

Surface selection, fixture discipline, and the spec-first rule are
`write-tests`. The commit choreography is `write-commit`. The loop
proves the behavior; it does not replace the landed test.

## When to leave the loop for verify-lanes

The loop answers "does this form return what I expect." It does not
answer "does the whole suite still pass, is the format clean, does the
native bake stay byte-identical." Run `verify-lanes` when:

- you are about to land a change (the cheap set: owning tests, format,
  lint, build);
- you changed a native body and need the bake lane to confirm the
  content-addressed artifact regenerates as expected;
- you touched a public API, the persistence schema, a native routine,
  or the renderer, each of which has a conditional lane the loop cannot
  stand in for;
- you are landing on `main` (the pre-land set).

Iterate in the loop, verify with the lanes. A green form is necessary,
not sufficient.

## Boundaries

Owns the interactive eval discipline only. The code standard is
`write-<lang>`; the boundary contract is `references/architecture.md`;
landing the proven behavior is `write-tests` plus `write-commit`. Does
not replace the lanes or the landed test.

## Return

`eval: <form or behavior>, green | red` (or `eval: no live host, reasoned over source` when the host is absent).
