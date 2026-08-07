# Top-K Over a Stream

## Approach
The reference keeps two structures in sync: a `HashMap<String, Long>` of every key's current
cumulative score (the source of truth), and a `TreeSet<Entry>` ordered by score descending, then
key ascending, holding the same keys ranked. `Entry` is an immutable record, so a score change
means a different tree node — `add` removes the key's *old* `Entry` from the set (using the score
captured before the map is overwritten), updates the map, then re-inserts the new `Entry`. Both
the removal and insertion are O(log n) tree operations, so a stream of N updates over n distinct
keys costs O(N log n) total rather than O(N·n) from re-sorting every tick. `top()` is then a single
O(k) walk of the set's head, and `scoreOf` is an O(1) map lookup.

The ranking comparator — `Comparator.comparingLong(Entry::score).reversed().thenComparing(Entry::key)`
— encodes the tie-break directly, so equal scores always land in the same relative order regardless
of insertion order or hash order.

## The real challenge
- **Two structures, kept in sync**: a map of every key's current score is the source of truth; a second structure holds the same keys in ranked order. Every `add` must update both — and since a key's rank position depends on its score, updating the score means finding and moving that key's position in the ranked structure, not just overwriting a value.
- **Re-rank, don't re-sort**: a full re-sort per update is O(n log n) per tick, unaffordable on a hot feed. The efficient move is removing the key's *old* ranked position and re-inserting it at its new one — O(log n) — which means capturing the old score before you overwrite it in the map.
- **Deterministic tie-breaking**: two keys with equal scores must always compare the same way, or two readers polling `top()` a moment apart (or the same state read twice) see different orders for no reason. Break ties by key ascending, not insertion order or hash order.
- **Bounded top-K heap vs. exact tracking**: a fixed-size-K min-heap of "current leaders" is the textbook top-K structure and uses less memory when the key space vastly exceeds K — but it has no cheap way to find-and-rescore an arbitrary key that's ranked outside the heap already. Think through why an addressable/indexed heap (heap + key→index map) is needed to match this kata's "any key can move on any tick" requirement, and when the extra bookkeeping is or isn't worth it.
- **Count-Min Sketch + heap**: for a key space too large to track exactly (millions of symbols), an approximate sketch trades exactness for sublinear memory — worth naming as the extension when "exact" stops being affordable.

## Common mistakes & senior signal
- **Overwriting the map before removing from the set** — once the map holds the new score, looking
  up "the old `Entry`" by key alone can't reconstruct the node that's actually in the `TreeSet`;
  the old score must be captured first.
- **Re-sorting a list on every `add`** instead of an incremental remove/re-insert — works, but is
  O(n log n) per tick instead of O(log n), and a strong candidate names the complexity difference
  unprompted.
- **Using a bare `PriorityQueue`** for "top K of a stream" without noticing the requirement is
  rescore-any-key, not insert-and-evict-the-min — a plain heap has no efficient "find and re-rank
  an arbitrary element" operation.
- **Forgetting the tie-break** — sorting by score alone leaves equal-score keys in whatever order
  the underlying structure happens to produce, which silently varies between reads.
- **Mutable `Entry`** — using a mutable holder instead of an immutable record invites "just mutate
  the score in place," which corrupts the `TreeSet`'s ordering invariant (a `TreeSet` assumes a
  node's sort key never changes while it's stored).
- Senior signal: naming the bounded-heap alternative unprompted, explaining precisely why it
  doesn't fit *this* requirement (rescoring an arbitrary key vs. insert-and-evict), and knowing
  when it would win (huge key space, small K, append-only extremes).

## Extensions
- **Bounded top-K heap** — when the key space is small relative to K and only-ever-grows-then-
  occasionally-shrinks-out-of-view semantics apply, an indexed min-heap of size K plus a
  key→heap-index map gets updates to O(log K) instead of O(log n), at the cost of exact `scoreOf`
  for keys outside the top K.
- **Count-Min Sketch + heap** — for a massive or unbounded key space (millions of distinct symbols)
  where per-key exact tracking is too much memory, approximate every score with a Count-Min Sketch
  (sublinear memory, no per-key entry) and maintain a small heap of sketch-estimated leaders;
  trades exactness (bounded over-counting) for memory.
- **Decay** — an exponentially-decaying score (recent activity outweighs old) instead of a plain
  cumulative sum, so "biggest movers" reflects momentum, not all-time volume.

## Reference
- Worked solution: this package (`solution/src/main/java/org/kata/topk/`)
- Java Interview Primer: Q155 (PriorityQueue / heaps), Q30 (TreeMap / sorted sets)
