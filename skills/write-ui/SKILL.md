---
name: write-ui
description: Recipe for writing UI as data, the pure layout and hit-test functions, the widget vocabulary, the renderer diff contract, and the design language as a working discipline. Invoked when writing or changing any UI surface.
user-invocable: false
---

# write-ui

Write the UI for the project. The UI core is pure data transformation:
it takes the application state and returns a view-spec, lays it out, and
resolves hits, all as data. The renderer sits at the edge and consumes
the positioned view-spec. Read first:

- the project's view-spec reference (the widget vocabulary, data shapes,
  event format, layout model, hit-testing). This is the concrete spec;
  this skill is the recipe for authoring against it.
- the project's design-language reference (the principles, the token
  system, the component template, the state design every view must
  cover, and the design-review checklist).
- the project's token catalog (spacing, type, color, depth, motion). The
  vocabulary the view-spec references; never inline a raw value.
- `skills/shared/references/architecture.md` for the Functional Core /
  Imperative Shell split. The whole UI core lives in its pure core.
- design-ui (sibling), the design-first recipe that produces the
  surface's design spec before this recipe implements it. Implement
  against the design spec; do not re-decide the design here.

## Where UI code lives

The whole UI core is pure functions under the UI module. None do IO,
hold an atom, read a clock, call native, or transact. Input arrives as
data from the shell; the view-spec leaves as data for the shell to
render. If you reach for a side effect in this module, the design is
wrong: move the effect to the shell and keep the decision here.

## The contract: view-spec is description, not instruction

The view-spec is the UI's intermediate representation. It describes what
the UI is right now; it never tells the renderer how to draw. Hold to
this:

- **Every widget is a plain map** with a type and type-specific keys. No
  functions, no closures, no objects. A view-spec is serializable data:
  printable, diffable, testable, scriptable.
- **Events are data, not callbacks.** Handlers are vectors of action
  data under an `:on` key. Late-binding values the UI cannot know yet
  use placeholder keys; the shell substitutes them when the event fires.
- **Re-derive the whole tree from the root** each frame. No
  component-local state, no subtree subscriptions. All UI logic lives in
  one entry point; given the same state it returns the same view-spec.
- **Persistent structures, deliberately.** Build the new view-spec by
  transforming the old state's data, so unchanged subtrees stay
  identical objects. The renderer's diff exploits this structural
  sharing; a rebuild that copies unchanged subtrees defeats it.

The entry point is the heart. It is a pure function of the application
state (the domain view, the interaction state, the feature flags)
returning a view-spec. Selection, hover, scroll position, and
drag-in-progress are interaction state the shell owns and passes in.
Test it by passing state and asserting on the returned map: no renderer,
no window, no automation framework.

## The layout engine (pure)

Layout takes a view-spec and returns a positioned view-spec: every
widget gains bounds. It is a pure function in the core, not a renderer
concern; the renderer draws at the bounds layout computed.

- The model is flexbox-inspired: direction, gap, padding, align.
  Containers place children; leaves measure themselves.
- Layout is deterministic and resolution-independent. The same
  view-spec and the same viewport produce the same bounds on every
  platform. Never branch layout on the host OS.
- Virtualize large content in the data, not the renderer. A large list
  carries a visible range; the entry point and layout materialize only
  the visible window so a very large collection lays out in budget.

## Hit-testing (pure)

Hit-test takes a positioned spec and a point and returns the topmost
widget there with its handlers. The shell calls it after draining a
pointer event from native, then dispatches the returned actions. Keep it
pure and total: a miss returns nil, not an exception. Respect z-order:
modals, menus, tooltips, and toasts sit above the content plane and must
win the hit.

## The widget vocabulary

The widget types in the view-spec reference are the alphabet: layout
containers, content widgets, controls, feedback. The vocabulary is the
design language: each widget type encodes a design decision made once in
the renderer and applied everywhere.

- **Reach for an existing widget before inventing one.** A new type is a
  design commitment and a new renderer implementation. If the need is a
  variant, parameterize the existing widget; if it is genuinely new, it
  is a design decision worth recording (record-decision) before it
  spreads.
- **Build widgets through constructors,** not as inline map literals
  scattered across the entry point. One constructor per type keeps the
  data shape uniform and the design consistent.
- **The view-spec names the type; the renderer owns the look.**
  Typography, spacing, and easing live in the renderer's widget
  implementation, defined once. Do not encode pixel colors, font sizes,
  or timings in the view-spec; pass semantic state (selected, playing,
  enabled) and let the renderer apply the language. Changing the look is
  changing the renderer, not every view-spec.
- **Tokens are the design values; reference them, never inline them.**
  Spacing, type, color, depth, and motion all live in the token catalog.
  A view-spec or a renderer treatment names a token; it never writes a
  raw hex, a pixel size, or a millisecond literal. A change to the look
  is a change to one token map, not a sweep across screens. A raw value
  where a token exists is a finding (check-design).

## The diff contract the renderer consumes

The renderer rebuilds nothing it can reuse. Per frame it diffs the new
positioned spec against the previous and updates only the changed
resources, never a clear and full redraw. The UI core's job is to make
that diff cheap and correct:

- **Stable identity.** A widget that represents the same thing across
  frames must be diffable as the same node. Carry stable keys so the
  renderer matches old to new instead of treating every frame as new.
- **Minimal change.** Change only what changed. If hover moved from one
  item to another, the two items' state differs and nothing else does;
  structural sharing keeps the rest of the tree identical.
- **No hidden churn.** Do not regenerate stable data into fresh
  collections each frame; reuse the value from state so the diff sees
  identity, not a new equal-but-distinct object.

## The design language as a working discipline

The design language is not decoration applied later. It is a constraint
on the data you produce. Hold each principle as you author a view-spec:

- **Functional, then beautiful.** The master rule. The UI works first;
  aesthetics serve function and never override it. A slow or confusing
  screen is a failure even if it looks good.
- **Opinionated, not maximalist.** Commit to good defaults over exposed
  options. The product has taste; the user should not configure their
  way to a good experience. When tempted to add a toggle, pick the right
  default instead.
- **Space-efficient, not cluttered.** Whitespace is a tool for focus;
  density serves the workflow in grids and lists. Every widget you add
  to a view-spec earns its place; if it does not, cut it.
- **Effects must serve function.** A visual effect must improve
  understanding of the data, clarify feedback, or maintain context. If
  an effect only decorates, it does not ship.
- **Frame budget.** The view-spec you produce must lay out and diff
  inside one frame. A view-spec that is correct but too expensive to
  derive is a defect. Keep the entry point and layout cheap; virtualize;
  reuse stable values.
- **Cross-platform pixel-identical.** Every pixel is custom-drawn, so the
  UI is identical across platforms. Never let a platform fact leak into
  the view-spec or layout. Identity is a feature, not an accident.
- **Design every state, not the happy path.** Each view produces its
  empty, loading, success, error, and permission-denied states: a fresh
  start, a load in progress, a decode that failed, a resource the OS
  will not read. A view-spec that only renders the populated state is
  incomplete.
- **Motion and depth communicate, or they do not ship.** Motion answers
  what changed and depth reveals structure. Pass the semantic intent (a
  toast at the transient depth layer, a detail opening with a focus
  motion); the renderer applies the token timing and curve. Decorative
  motion or depth that harms readability is a finding.
- **Focus is data the view-spec carries.** Focus is always visible,
  predictable, and survives transitions. The shell owns focus as
  interaction state and passes it in; the widget carries the focus flag
  and the renderer draws the indicator.

Apple's HIG and Material Design 3 are loose inspiration for the level
of polish and discipline, not a visual template. Borrow the discipline;
express it as the project's own language.

## Always

- Pure core only. No IO, no atom, no clock, no native call, no transact
  in the UI module. Interaction state and feature flags arrive as
  arguments; the view-spec leaves as a return value.
- Description, not instruction. Widgets and events are data; one global
  handler in the shell interprets the action vectors.
- Honor the dependency direction. The UI module may read the domain data
  shapes but must not require the shell, persistence, or platform. The
  UI consumes data; it does not drive the shell.
- Test the pure functions directly: state to entry point to view-spec to
  layout to positioned spec to hit-test. See write-tests for surface
  selection.
- Place, flatten, and hit-test read positions from one source, never
  re-derive them. A container's vertical cursor, its band reservation,
  and its empty-state short-circuit live in one private helper all three
  passes consume; mirror comments are not lockstep. If a pass recomputes
  a value another already derived, they drift.
- **Lay text-bearing layout out against the renderer's real baked font
  metrics, never design line-heights.** The type tokens size a text
  run's semantic role; they are not what the renderer paints. Reserve
  vertical bands and clip or wrap horizontal runs against the layout's
  real-metric constants, and prove it headless (see write-tests). A
  substitute font in an offscreen gallery measures differently and hides
  the bug; only the real bake metrics expose it.
- A schema cap is not enforcement. Layout, flatten, and hit-test never
  call validate, so a directly-built or cached view-spec arrives
  unvalidated. Bound every collection you iterate and every string you
  emit on the per-frame path at the read site. The schema declares the
  cap; the layout enforces it.
- A new widget type, a new effect, or a default the user cannot override
  is a design decision. If it is a real choice between alternatives,
  record-decision before it spreads.

## Boundaries

Owns: the UI core (the entry point, widget constructors, layout,
hit-test, the event resolver), the view-spec data shape, and the diff
contract the renderer consumes. Cites: the architecture contract in
`skills/shared/references/architecture.md`; the project's view-spec
reference, design-language reference, and token catalog; the ADR log.
Siblings: design-ui owns the design spec this implements; check-design
owns the review dimension the UI is held to; write-tests owns the test
surface; write-prose owns the prose standard.

## Comments and public text

Comments and public text follow write-prose: comment the why, never the
what; no banners, no commented-out code, no change narration; never
"hand-written"; no process id in a comment or commit.
