# Ring Buffer

## Approach
Back the queue with a single fixed-length `Object[]` sized to `capacity` and never reallocate it. Track three cursors: `head` (index of the next element to poll), `tail` (index of the next free slot to offer into), and `count` (how many live elements exist). Both `head` and `tail` march forward modulo `capacity`, wrapping back to `0` when they run off the end. `offer` writes at `tail` then advances it; `poll` reads at `head`, nulls the slot, then advances it — both O(1) with zero per-element allocation.

The key design choice is keeping `count` as the single source of truth for `isEmpty()`, `isFull()`, and `size()`. With only `head` and `tail`, the state `head == tail` is ambiguous: it means both "empty" and "full" (tail has wrapped all the way around to meet head). The two classic fixes are to burn one array slot (so the buffer holds at most `capacity - 1`) or to track a separate counter. The counter approach costs 4–8 bytes but buys a simpler, branch-free mental model — every method reasons directly about "how many are there" rather than back-deriving it from where the cursors sit, and every real slot in the array stays usable.

Because generic array creation (`new E[capacity]`) is illegal under type erasure, the backing store is `Object[]` and reads cast to `E`. The cast is unchecked but safe: this class alone controls what enters the array, so `(E) elements[i]` can never actually fail.

## The real challenge
- **Modular wrap-around**: both the head and tail cursors advance mod `capacity`, wrapping back to index `0` when they run off the end of the array — get the wrap-around index math right in both `offer` and `poll`.
- **Full vs. empty ambiguity**: with only a head and tail cursor, `head == tail` means either "completely empty" or "completely full" — you need a third signal (a count, or burning one array slot) to tell them apart.
- **No element loitering**: nulling out a slot after `poll` avoids pinning a polled object's reference from a stale array cell.
- **Generic array creation**: `new E[capacity]` doesn't compile (type erasure) — back the buffer with `Object[]` and cast on read, documenting why the cast is safe.

## Common mistakes & senior signal
- **Leaving `head == tail` ambiguous.** Advancing cursors without a disambiguator silently conflates full and empty — a full buffer looks empty and drops writes, or an empty one returns stale reads. A strong answer names both fixes (count field vs. burning a slot) and justifies the choice.
- **Off-by-one wrap arithmetic.** Forgetting the `% capacity` on either cursor, or wrapping only one of them, corrupts FIFO order at the seam. Test explicitly across the wrap point, not just on a fresh buffer.
- **Loitering references.** Not nulling the slot after `poll` keeps the polled object alive via a stale array cell — a subtle memory leak that a senior candidate calls out unprompted.
- **Reaching for generics on the array.** Trying `new E[capacity]`, then flailing when it won't compile. The signal is knowing erasure forces `Object[]` + a documented unchecked cast, not treating the cast as a code smell.
- **Mutating state on a rejected `offer` or empty `poll`.** A full `offer` must change nothing and return `false`; an empty `poll`/`peek` must return `null` without touching cursors.

## Extensions
- **`Iterable<E>` in logical order** — a custom `Iterator` that walks `count` elements starting at `head`, wrapping through the array, without exposing the raw backing order.
- **Double-ended** (`offerFirst`/`offerLast`, `pollFirst`/`pollLast`) — `head` can be decremented (mod `capacity`) as well as incremented, turning this into an array-backed deque.
- **Lock-free SPSC cousin** — a single producer and single consumer thread can share this same layout without a lock by making `head`/`tail` `volatile` (or `AtomicInteger`) and re-deriving full/empty from the cursors instead of a shared `count`, since a plain int written by one thread and read by another needs a happens-before edge.

## Reference
- Worked solution: this package (`solution/src/main/java/org/kata/ringbuffer/`)
- Java Interview Primer: `ArrayDeque` internals / circular buffers
