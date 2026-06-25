---
name: check-security
description: Review dimension for untrusted input reaching unsafety, path and capability bypass, and boundary integrity. Invoked by reviewer agents over one module shard.
user-invocable: false
---

# check-security

Role: review the assigned shard for security defects.

Failure model: untrusted input reaches an unsafe operation, a boundary
that should enforce a capability does not, or a path crosses a trust line
without a check.

The trust boundaries are project-specific, but the shape repeats. Read
the descriptor and the design docs for the project's trust boundaries
before sweeping. The general trust lines:

- **Caller-supplied data at a native edge** is untrusted. A seq, stream,
  iterator, or buffer from the calling language, materialized into a
  fixed-size native array, must be bounded before it is realized (the
  load-bearing rule in `skills/shared/references/architecture.md`). The
  native length guard is the second line of defence, not the first.
- **Files and paths from outside the trust line** are untrusted. A
  malformed header, a symlink loop, a path with crafted unicode, a
  parent directory the process does not own.
- **Caches and persisted state read back at startup** are untrusted at
  read time. A corrupt or attacker-written cache file must fail clean.
- **Credentials, tokens, and entitlements** are untrusted. A forged or
  corrupted entitlement must not grant elevated behavior or crash the
  process.
- **The native edge itself** is a memory-safety boundary. A native body
  that retains a caller-owned pointer, double-frees a handle, or reads
  past a slice's length is a critical finding; native memory unsafety
  reachable from untrusted input is the highest-severity bug class.
- **Extension or scripting entry points** are partially trusted. They
  must not reach the filesystem, the network, or process spawning
  except through an explicit capability gate.

## Look for

1. **Buffer arithmetic.** Every allocation and copy whose size flows
   from a file header, a network read, or caller input. Size-doubling
   growth without an overflow guard. Any user-controlled count flowing
   into indexing.
2. **Filesystem.** `stat` versus `lstat` in walks; path joins with
   user-supplied components; TOCTOU between check and use; symlink
   handling in a recursive walk; paths with control characters or null
   bytes.
3. **Parser surfaces.** Any decoder for an external format (a media
   container, a serialization format, a wire protocol): a header that
   claims a size larger than the payload, a count of zero or absurdly
   large, a field the decoder does not support. Each must error, not
   crash or read past the buffer.
4. **Native handle lifetime.** For every handle type at the edge: is the
   finalizer or destructor registered? Can the handle be used after
   free? Can two threads free the same handle? Is the handle stored in
   a global another module could free from under the owner? The
   ownership discipline itself is check-memory's; the reachability from
   untrusted input is here.
5. **Capability boundary.** Extension or scripting entry points must not
   reach filesystem, network, or process-spawning primitives except
   through explicit gates. An unrestricted eval against untrusted input
   is a finding.
6. **Entitlement validation.** A credential or entitlement parser must
   reject corrupt signatures cleanly; the entitlement path must not be
   writable by an attacker via a path trick; an expired or invalid
   entitlement must downgrade cleanly without leaving elevated behavior
   on.
7. **Crash reachability.** For each abort, panic, unreachable, or hard
   assertion in the shard: can untrusted input steer execution there?
   An untrusted-input-driven crash is the desktop equivalent of a
   server-side denial of service.
8. **Secrets.** A credential, key, or token written to a log, embedded
   in an error message, or committed to the repository.

## Ignore here

Leaks without an unsafety consequence (check-performance for cost,
check-memory for the lifetime). Style, naming (check-style). Factoring
(check-factoring).

## Severity

- `:high`. Reachable native memory unsafety from any untrusted input; a
  reachable crash from untrusted input; an entitlement or capability
  bypass; a secret committed or logged.
- `:medium`. A trust-line weakness that needs a specific shape to
  exploit.
- `:low`. A hardening note with no current path.

## Level

`:correctness` for reachable unsafety, bypass, and crash findings;
`:factoring` for capability gaps that need a structural split.

## Boundaries

Owns: untrusted input reaching unsafety, trust-line and capability
enforcement, secrets. Siblings: check-memory owns the native ownership
and lifetime discipline; check-correctness owns logic bugs not on the
trust line; check-conformance owns the edge contract shape; the
`deny-secrets` hook owns the commit-time block.

## Return contract

An EDN vector of finding maps (shape in
`skills/shared/references/review-model.md`), one per defect, each with
the trust line crossed and a `:suggestion` sketching a repro or a fix.
When the shard has none, return exactly:

```
NO FINDINGS
```
