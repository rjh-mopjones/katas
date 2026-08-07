# Hash Map

## Approach
The map is an array of bucket chains — **separate chaining**: `buckets[i]` is the head of a singly
linked list of `Node`s, and two keys that land in the same bucket both survive as distinct nodes,
distinguished by `equals`, never by hash alone. Each `Node` caches its spread hash so a resize never
has to re-derive it.

A key's index is derived in two steps. First `hashCode()` is *spread* — XOR-folding the high bits into
the low bits (`h ^ (h >>> 16)`) — because for many real hash functions (small `Integer`s, short
`String`s) most of the entropy lives in the low bits; without folding, a small capacity would collide
keys a plain modulo against a larger table would have kept apart. Second, the spread hash is masked
down with `hash & (capacity - 1)` — one AND instead of a division — which is only valid because
capacity is kept a power of two on every resize (`tableSizeFor` rounds any requested capacity up to
the next power of two).

Past a load factor of `0.75` (`size > capacity * 0.75`), `put` doubles the table and re-links every
existing node against the new capacity. Nodes are *moved*, not recreated — a node's key/value never
changes across a resize, only which bucket it hangs off of, so the resize reuses the cached hash and
just re-derives the index.

The `null` key is not special-cased away — it's routed deterministically to bucket `0`, because
`null.hashCode()` would throw, so `spread(null)` short-circuits to `0` before ever calling `hashCode()`.

Generic array creation (`new Node<K,V>[n]`) is illegal under erasure, so the backing array is allocated
as a raw `Node[]` and cast to `Node<K,V>[]` — unchecked but safe because the array is private and every
element ever stored is a `Node<K,V>` this class constructed itself.

## The real challenge
- **Separate chaining**: each bucket holds a singly-linked chain of entries; two keys landing in the
  same bucket must both survive, distinguished by `equals`, never by hash alone.
- **Spreading the hash**: `key.hashCode()` alone clusters badly when only its low bits vary. XOR-folding
  the high bits into the low bits (`h ^ (h >>> 16)`) spreads that entropy before masking it down to an
  index.
- **Power-of-two capacity**: keeping capacity a power of two turns `hash % capacity` into
  `hash & (capacity - 1)` — one AND instead of a division — but it is only correct if every resize also
  lands on a power of two.
- **Load factor and resize**: past 0.75 load, chains get long enough that lookups stop being O(1).
  Resizing means allocating a bigger table and re-deriving every entry's bucket index against the new
  capacity — get this wrong and entries silently vanish after a resize.
- **The null key**: `null.hashCode()` throws, so `null` needs a special-cased hash (conventionally
  bucket `0`) rather than falling through the normal `hashCode()` path.

## Common mistakes & senior signal
- Using `key.hashCode() % capacity` directly, without spreading — works on small test inputs but
  clusters badly on real key distributions where entropy lives in the high bits. Knowing *why* the
  spread step exists (not just copying it) is the signal.
- Using `hash % capacity` instead of `hash & (capacity - 1)` after committing to a power-of-two
  capacity — functionally equivalent but misses the reason the class enforces power-of-two sizing in
  the first place.
- Recreating `Node` objects during resize instead of re-linking the existing ones — works, but is
  wasteful allocation churn on every doubling; the reference implementation moves nodes by re-pointing
  `next`, reusing the cached hash.
- Letting `key.hashCode()` be called directly on a `null` key — throws a `NullPointerException` instead
  of routing to the null-key bucket. The guard has to come *before* the `hashCode()` call.
- Off-by-one on the load-factor check (`size >= threshold` vs `size > threshold`, or checking before
  vs after the insert) — subtle but changes exactly when a resize triggers relative to the stated
  "past 0.75" contract.

## Extensions
- **Open addressing** — linear or quadratic probing with tombstone markers for deletion, trading
  chain-pointer overhead for cache locality.
- **Treeify hot buckets** — once a single bucket's chain exceeds a threshold (as `java.util.HashMap`
  does at 8), convert that bucket to a red-black tree keyed by `hashCode()` so a pathological chain
  degrades to O(log n) instead of O(n).

## Reference
- Worked solution: this package (`solution/src/main/java/org/kata/hashmap/`)
- Java Interview Primer: hashing / the `equals`-`hashCode` contract
