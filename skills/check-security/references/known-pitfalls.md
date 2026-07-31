# Known pitfall patterns: untrusted-input risk classes

For a parser or runtime that ingests untrusted documents and binary
assets, these are the highest-prior-probability defect classes.
`check-security` and `check-memory` look for the *pattern*, not a fixed
site. Each entry names the pattern, where it tends to live, and the
check. The catalog grows via `incorporate-feedback`; cite the commit
when an entry is added from a real defect.

## Integer overflow in size arithmetic

**Pattern:** a size, count, or dimension read from an untrusted document
or asset header feeds a multiplication or addition that overflows
`usize`/`i64` and wraps to a small value, producing an undersized
allocation followed by a large write.

- A pixel buffer allocation `width * height * bytes_per_pixel` where the
  dimensions come from an image header field.
- A mesh or vertex buffer `count * @sizeOf(Vertex)` where the count comes
  from an untrusted accessor.
- Any document-driven count: a repeated element count, a gradient-stop
  array size, a growth-loop bound.

**Check:** every `*` and `+` that feeds `alloc`/`@memcpy`/indexing.
Require `std.math.mul`/`std.math.add` (error on overflow) or an explicit
bound check before the operation. `*%`/`+%` are findings unless wrap is
intended and provably bounded.

## Out-of-bounds from header-stated sizes

**Pattern:** a length or dimension stated in a header is trusted without
checking it against the bytes actually present, so a decoder reads or
writes past the real buffer.

- A chunk or record length field larger than the remaining file bytes.
- A stated dimension that implies more data than the decompressed stream
  contains.
- A slice taken as `buf[offset .. offset + stated_len]` without checking
  `offset + stated_len <= buf.len` (and that `offset + stated_len`
  itself did not overflow).

**Check:** every slice bound and index derived from untrusted input is
validated against the real buffer length, after the overflow check.

## Path traversal in asset or path resolution

**Pattern:** joining a user-supplied path to a base directory without
confining the result, so `../`, an absolute path, a Windows drive letter,
or a symlink escapes the intended directory.

- `std.fs.path.join(dir, user_path)` where `user_path` is
  `../../etc/...`.
- An absolute user path ignoring `dir` entirely.
- A symlink inside the base directory pointing outside it.

**Check:** resolve to a canonical or real path and verify it stays under
the base directory the project treats as the root. Reject absolute paths
and traversal components.

## Allocation and decompression bombs

**Pattern:** a small input forces a large allocation or unbounded
recursion.

- A header declaring enormous dimensions, forcing a multi-gigabyte
  buffer before any unit of work is decoded.
- A deeply nested document recursing the reader past the stack, or a
  pathologically large literal or collection.
- A manifest declaring a vast element count.

**Check:** allocations sized from untrusted input need a sanity cap;
recursive parsing needs a depth bound; declared sizes are validated
against the actual input size before allocating.

## Allocator leaks on error paths

**Pattern:** an early `return` or `try`/`error.X` exit leaves a buffer
allocated above it unfreed, because the free is only on the success path.

- Partial construction of a value or buffer where ownership has not yet
  transferred to the caller and no `errdefer` covers the failure.
- A realloc-style grow whose result is dropped on failure, leaking the
  old block.

**Check:** every error path out of a function that allocated. What frees
each allocation at that line? Require `defer` for the success path and
`errdefer` for partial construction. (This is the memory dimension's
core, repeated here because traversal and overflow bugs and leaks
cluster in the same untrusted-input code.)

## Verifying a finding

A memory or security finding is strongest with a repro:

    zig build -Doptimize=ReleaseSafe test   # safety checks catch OOB/overflow
    # crafted fixture under the project's invalid-input test path

Reviewers are read-only and do not run these. Put the candidate fixture
and command in the finding's `:suggestion` so the editor or verifier can
confirm it.
