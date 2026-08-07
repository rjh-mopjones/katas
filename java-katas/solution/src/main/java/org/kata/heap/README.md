# Binary Heap

## Approach
The heap is a **complete binary tree stored implicitly in an array**: for a node at index `i`, its
children live at `2i+1`/`2i+2` and its parent at `(i-1)/2`. Completeness — every level full except
possibly the last, which fills left-to-right — is exactly what lets the tree be a flat array with no
pointers: the shape is fully determined by `size`, so there's nothing to rebalance and no node objects
to allocate.

The heap-order invariant only constrains parent-to-child (`cmp.compare(parent, child) <= 0` for a
min-heap) — not siblings, not across subtrees — so the structure is *not* sorted, only the root is
guaranteed to be the extreme element. That weaker invariant is what keeps `add`/`poll` at O(log n)
instead of paying the cost a fully sorted structure would need.

Two sift operations maintain the invariant, and both only ever touch a single root-to-leaf path (hence
O(log n), bounded by `floor(log2(n))` tree height):
- `siftUp` — after `add` appends the new element at the last slot, swap it with its parent while it
  compares before the parent.
- `siftDown` — after `poll` moves the last element into the vacated root, swap it with its "better"
  child while a child compares before it.

Elements are held in `Object[]` rather than `E[]`, because Java forbids generic array creation under
erasure; every read casts back to `E`, safe because `add` is the only writer and it's fully generic.
The no-arg constructor installs an internal comparator that casts each element to `Comparable` and
delegates to `compareTo` — mirroring how `java.util.PriorityQueue` itself pushes the "E must be
Comparable" constraint to a runtime `ClassCastException` at compare time rather than encoding it in the
class's type bound, keeping the class usable with an explicit `Comparator` for non-`Comparable` types.

The bulk-build constructor loads all elements into the array first, then runs `siftDown` from the last
non-leaf index down to the root (Floyd's build-heap algorithm). Leaves are already trivially valid
heaps, so only internal nodes need fixing, and going bottom-up means each `siftDown` only ever moves
elements into subtrees that are already valid — O(n) total, versus O(n log n) for n sequential `add`s.

## The real challenge
- **Implicit tree in an array**: index `i`'s children live at `2i+1`/`2i+2`, its parent at `(i-1)/2`. Completeness (the last level fills left-to-right, no gaps) is what makes this valid — get that wrong and the parent/child math silently points at garbage or the wrong node.
- **Sift-up vs sift-down**: `add` appends at the last slot and bubbles it toward the root while it beats its parent; `poll` moves the last element into the vacated root and bubbles it toward the leaves while a child beats it. Both only ever touch one root-to-leaf path — that is the whole O(log n) argument.
- **Generic array creation**: `new E[capacity]` does not compile (type erasure). Back the heap with `Object[]` and cast on read; document why the cast is safe (only `add`, which is fully generic, ever writes into the array).
- **Natural ordering without a type bound**: the no-arg constructor cannot require `E extends Comparable<E>` without also breaking usability for a comparator-supplied `E` that isn't `Comparable` at all. Work out how `PriorityQueue` itself resolves this — and what happens (and when) if you call the no-arg constructor with a non-`Comparable` type.
- **(Optional) heapify in O(n), not O(n log n)**: naively calling `add` n times is O(n log n). Building the array first and sifting down from the last non-leaf node backward to the root is O(n) — work out why bottom-up beats top-down here.

## Common mistakes & senior signal
- Getting the parent/child index arithmetic wrong (`2i+1`/`2i+2` for children, `(i-1)/2` for parent) —
  usually surfaces as an `ArrayIndexOutOfBoundsException` or a heap that silently violates its
  invariant on deeper trees where the bug wasn't triggered by small test cases.
- Assuming the heap is fully sorted and trying to read it in order (e.g. iterating the backing array
  top to bottom expecting ascending order) — only the root is guaranteed to be extreme; siblings and
  cross-subtree elements have no defined order.
- Reaching for `E[]` and fighting the compiler instead of accepting `Object[]` with a documented,
  narrowly-scoped unchecked cast — knowing *why* `new E[]` is illegal (erasure) and where the cast is
  actually safe is the signal, not avoiding the cast altogether.
- Requiring `E extends Comparable<E>` on the class itself to support the no-arg constructor — this
  breaks the comparator-based constructors for types that aren't `Comparable`. The fix is deferring the
  cast to compare-time, exactly as `java.util.PriorityQueue` does.
- Implementing heapify by calling `add` in a loop — correct output, but O(n log n) instead of O(n);
  missing the bottom-up sift-down insight is a common miss even from candidates who know the heap
  mechanics cold.

## Extensions
- **`decreaseKey`** — an auxiliary element-to-index map turns "find and re-sift an arbitrary element"
  from O(n) into O(log n), which is what Dijkstra's algorithm and a streaming top-k tracker both need
  to update a priority in place.
- **d-ary heap** — widen the fan-out from 2 to d children per node: shallower tree (fewer comparisons
  on `siftUp`) at the cost of more comparisons per `siftDown` level; tunable per workload (Dijkstra
  with dense graphs often prefers d=4).

## Reference
- Worked solution: this package (`solution/src/main/java/org/kata/heap/`)
- Java Interview Primer: Q155 (PriorityQueue / heap)
