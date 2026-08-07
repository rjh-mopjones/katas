# Dynamic Array

## Approach
`DynamicArray<E>` is backed by a single `Object[]` that grows geometrically as elements are
appended, giving O(1) amortised `add(E)` and O(1) random-access `get(int)`. Java erases generics at
runtime, so `new E[capacity]` doesn't compile — the backing store is declared `Object[]` and every
read casts back to `E` with `@SuppressWarnings("unchecked")`, which is safe because `add`, `add(int,
E)`, and `set` are the only writers and all three are statically typed `E`, so nothing but an `E`
(or `null`) ever lands in a live slot.

On overflow, `ensureCapacity` grows the array to `oldCapacity + (oldCapacity >> 1)` — roughly 1.5x,
copied via `System.arraycopy`/`Arrays.copyOf`. This matches the JDK's actual `ArrayList` growth
factor (rather than the more commonly-taught 2x doubling), trading a few more resizes for less
wasted memory per resize. Either constant keeps the *amortised* cost of N appends at O(N) total:
each element is copied only O(log N) times across all resizes, and the geometric series of copy
costs sums to a constant multiple of N — this is the argument to have ready when asked to justify
"O(1) amortised."

Indexed `add(int, E)` and `remove(int)` stay O(n) because they must shift the tail to keep the
array dense: `add` copies `[index, size)` one slot right *before* writing the new element (so
nothing is overwritten before being moved); `remove` copies `[index + 1, size)` one slot left, then
nulls the now-unused last slot so the removed reference isn't pinned by a lingering array cell.
Bounds checking splits into two distinct rules that are easy to conflate: `get`/`set`/`remove`
require `0 <= index < size` (the index must name an existing element), while `add(int, E)`
additionally allows `index == size` (appending at the end is a valid insert point).

## The real challenge
- **Amortized O(1) append**: growing by a fixed increment (e.g. +1 each time) makes every append O(n) in the worst case; growing geometrically (1.5x/2x) spreads the copy cost so the *average* append across N inserts stays O(1) — be ready to explain why (the classic geometric-series argument).
- **`Object[]` + cast, not `new E[]`**: generics are erased at runtime, so you cannot allocate a generic array directly. Back the array with `Object[]` and cast on read — document why in a comment, it is not an oversight.
- **Shifting on indexed insert/remove**: `add(index, e)` must open a gap (copy `[index, size)` one slot right *before* writing); `remove(index)` must close the gap (copy `[index + 1, size)` one slot left) — get the `System.arraycopy` direction or length wrong and you silently corrupt or duplicate elements.
- **Bounds checking is two different rules**: `get`/`set`/`remove` require `0 <= index < size` (must point at an existing element); `add(index, e)` allows `0 <= index <= size` (`index == size` is a valid append) — conflating the two lets one throw when it shouldn't, or not throw when it should.

## Common mistakes & senior signal
- Growing by a fixed increment instead of geometrically — makes append O(n) worst-case and defeats the point of the exercise; a strong candidate names the growth factor choice (1.5x vs 2x) unprompted and explains the amortized-cost argument rather than just asserting "it's O(1)."
- Getting the `System.arraycopy` shift direction or length wrong on `add(int, E)`/`remove(int)` — silently corrupts or duplicates elements rather than throwing, which is the worst kind of bug to ship undetected.
- Using `index <= size` for `get`/`set`/`remove` (copying the insert-bounds rule) instead of `index < size` — off-by-one that lets a caller "read" past the logical end into stale backing-array garbage.
- Forgetting to null out the vacated slot after `remove` — a correctness non-issue but a real memory-leak trap (the reference stays reachable via the backing array past the logical end).
- Attempting `new E[capacity]` and being surprised by the compile error — a candidate who already knows to expect this and reaches straight for `Object[]` + suppressed cast signals real familiarity with generics erasure.

## Extensions
- **Fail-fast `Iterator`** — track a `modCount`, bump it on every structural change, and check it in `next()`/`hasNext()` to detect concurrent modification during iteration, like `ArrayList`'s.
- **`ensureCapacity(int)`** — expose the growth hook publicly so a caller who knows the eventual size up front can pre-size once and avoid intermediate resizes.
- **Shrinking** — halve the backing array when `size` falls far enough below `capacity` (e.g. below a quarter) to bound memory after a large removal burst, at the cost of occasional extra copies (the same amortised argument as growth, run in reverse).

## Reference
- Worked solution: this package (`solution/src/main/java/org/kata/dynamicarray/`)
- Java Interview Primer: amortized analysis / `ArrayList` internals
