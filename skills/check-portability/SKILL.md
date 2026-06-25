---
name: check-portability
description: Review dimension for platform branches, endianness, filesystem semantics, and integer-width assumptions. Invoked by reviewer agents over native or platform shards.
user-invocable: false
---

# check-portability

Role: review the assigned shard for behavior that works on one platform
but breaks on another.

Failure model: the code runs on the development platform and silently
misbehaves on another platform the project commits to, because a
platform difference was not gated or a host-order assumption leaked.

Read the design docs for the project's platform commitments before
sweeping. A platform the project does not commit to is out of scope; a
finding that an unsupported platform does not work is not a finding.

## Look for

1. **Platform branches not compile-time gated.** A platform check that
   runs at runtime when it could fold away at compile time; a platform
   path that ships in a release build for a platform it does not target;
   a check that tests the wrong platform predicate.
2. **Filesystem semantics that differ.** Case sensitivity, path
   separators, max path length, reserved names, mandatory versus
   advisory file locking, atomic-rename guarantees, permission models.
   Use the platform library's path handling; do not hand-roll. A path
   built with a hardcoded separator or a hardcoded home directory is a
   finding.
3. **Watcher and event-source divergence.** When the project watches the
   filesystem or subscribes to OS events, the shape of the event differs
   across platforms (rename versus modify coalescing, latency
   guarantees, recursive defaults). A body that assumes one platform's
   semantics and breaks on the other is a finding.
4. **Surface and windowing creation.** Graphics or window surface
   creation is platform-specific. The surface code must gate the right
   path per platform. A call to the wrong platform's surface creator is
   a build failure, not a finding; an unused but compiled surface path
   that pulls the wrong system framework is.
5. **System-library availability.** A decoder or integration that uses a
   system library on one platform and a bundled library on another. The
   dispatch must be platform-aware; a path that assumes the system
   library will not work where it is absent.
6. **Endianness and integer widths.** External formats (file headers,
   wire protocols, serialized state) have specific byte orders; a
   host-order reinterpret is a finding. Pointer widths and the C
   fixed-width types (`c_int`, `c_long`, `long`) differ across
   platforms; a type whose width varies, used in a persistence or wire
   shape, is a finding. Use fixed-width types at every external
   boundary.
7. **Baked artifact portability.** A build that produces a platform-
   specific artifact (a dylib, a shared object, an executable) must
   target every platform the project commits to, and the runtime must
   select the right one for the host. A bake that produces one platform
   only and tries to load it on another is a build-config finding.

## Ignore here

Pure style (check-style). Factoring (check-factoring). Correctness on a
single platform (check-correctness). Performance (check-performance).

A finding needs the specific platform where the code breaks, the
specific call site, and the system difference that causes the divergence.

## Severity

- `:high`. A runtime crash on a platform the project commits to.
- `:medium`. A degraded behavior that works but is wrong (events
  coalesced, paths mis-resolved) on a committed platform.
- `:low`. A future-proofing concern for a platform the project treats as
  best-effort.

## Level

`:factoring` for structural issues (a path that needs gating);
`:correctness` for platform-specific wrong behavior on a committed
platform.

## Boundaries

Owns: cross-platform divergence. Siblings: check-correctness owns
single-platform logic bugs; check-factoring owns the structure;
check-conformance owns whether the platform commitment matches the spec.

## Return contract

An EDN vector of finding maps (shape in
`skills/shared/references/review-model.md`), one per defect, each
naming the platform and the system difference. When the shard has none,
return exactly:

```
NO FINDINGS
```
