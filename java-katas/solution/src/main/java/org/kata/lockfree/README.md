# Lock-Free Data Structures

## Approach
All three structures share the same CAS-loop skeleton: (1) volatile-read a snapshot, (2) compute a
pure, side-effect-free proposed next state, (3) `compareAndSet` — commit on success, retry on
failure. The differences are in what state gets swapped:

- **`TreiberStack`** — a single `AtomicReference<Node>` head. `push` builds a new node pointing at
  the snapshotted head and CASes head to it; `pop` reads `head.next` from the snapshot and CASes
  head down to it. `Node.item`/`Node.next` are `final`, which is what makes reading `node.next`
  safe without a lock once the node is installed. There's no `size()` — a counter can't be updated
  atomically together with `head` inside a single CAS, so only `isEmpty()` (one volatile read) is
  offered.
- **`MichaelScottQueue`** — a dummy/sentinel node decouples `head` and `tail` so enqueue and
  dequeue barely interact. Enqueue is two CASes: append the new node onto `tail.next` (the
  linearization point), then swing `tail` forward. Between those two CASes the tail can lag; any
  thread that observes `tail.next != null` *helps* by advancing `tail` itself before doing its own
  work — this cooperative helping is what makes the algorithm lock-free rather than merely
  obstruction-free. Both enqueue and dequeue re-check their snapshot (`curTail != tail.get()` /
  `curHead != head.get()`) before acting, to detect a concurrent mutation between two reads.
- **`AtomicStampedStack`** — structurally identical to `TreiberStack`, but pairs `head` with a
  monotonically-increasing stamp via `AtomicStampedReference`. Reference and stamp are read
  together with `get(int[])` so they're a consistent pair, and the four-arg `compareAndSet`
  fails if *either* has changed — defeating ABA even when a recycled node happens to have the same
  identity as the one originally snapshotted.

## The real challenge
- **CAS-loop skeleton**: every mutating operation follows the same three steps — (1) volatile-read a snapshot, (2) compute the proposed next state (pure, no side-effects), (3) `compareAndSet` and return on success or retry on failure. Getting step (2) right — building a new node from a snapshot before the CAS — is the key insight.
- **Michael-Scott two-CAS enqueue + helping**: enqueue requires one CAS to link the new node onto `tail.next` (the linearization point) and a second CAS to swing `tail` forward. Between these two CASes the structure is in an intermediate state. Any thread that observes `tail.next != null` must help advance `tail` before doing its own work — this is what makes the algorithm lock-free rather than merely obstruction-free.
- **Dummy node invariant**: the queue always has a sentinel node whose item is ignored. `head` points to the dummy; the true first element is `head.next`. Dequeue makes `head.next` the new dummy. This eliminates the special case when the queue has exactly one real element.
- **Consistency snapshot in Michael-Scott**: after reading `curTail` and `tailNext` separately, re-read `tail` to detect if another CAS fired between the two reads and restart with a fresh snapshot if it did.
- **ABA with `AtomicStampedStack`**: use `head.get(stampHolder)` to read reference and stamp atomically. Each successful push or pop uses `oldStamp + 1` as `newStamp`. The four-argument `compareAndSet(expectedRef, newRef, expectedStamp, newStamp)` fails if either the reference or the stamp has changed — a recycled node with the same identity but a different generation is correctly rejected.
- **Immutable nodes**: `Node.next` (in Treiber and AtomicStamped) and `Node.item` must be `final`. Immutability is not style — it is what makes reading `node.next` safe without a lock after the node is installed via CAS.

## Common mistakes & senior signal
- Reaching for `synchronized`/`ReentrantLock` "just to be safe" — defeats the point of the kata; a
  strong answer explains *why* CAS wins here (no kernel transition, no priority inversion, retries
  cost nanoseconds) instead of defaulting to a lock.
- Making `Node.next` mutable — reintroduces a race that only shows up under adversarial
  interleaving/stress, not a quick single-threaded smoke test.
- Michael-Scott: skipping the "help advance tail" step — looks correct single-threaded but breaks
  correctness (and lock-freedom) the moment tail lags under real contention; the stress test is
  designed to expose exactly this.
- Michael-Scott: omitting the re-read/consistency check after snapshotting `curTail` and
  `tailNext` — a classic TOCTOU-inside-a-CAS-loop bug.
- `AtomicStampedStack`: reading the reference and stamp via two separate calls instead of the
  atomic `get(int[])` — silently reintroduces the exact race the stamp exists to close.
- Treating ABA as an abstract danger everywhere instead of explaining precisely when it bites (node
  recycling via free-lists, off-heap memory) and why plain `TreiberStack` is safe from it under
  ordinary GC.

## Extensions
- Two-lock queue (separate head/tail locks) — the middle ground from the original Michael & Scott
  paper, letting one producer and one consumer proceed in parallel without full lock-freedom.
- `AtomicMarkableReference` — pairs a reference with a boolean mark for logical deletion; useful for
  mark-and-sweep linked lists but not itself sufficient to defeat ABA.
- Hazard pointers / epoch-based reclamation — alternatives to stamping for safe memory reclamation,
  more common in C/C++ or off-heap Java, avoiding stamp-overflow concerns at the cost of more
  bookkeeping.
- Approximate/relaxed `size()` via a separate counter, if an interviewer accepts eventual
  (non-atomic) consistency with `head`.

## Reference
- Worked solution: this package (`solution/src/main/java/org/kata/lockfree/`)
- Java Interview Primer: Q256 (Treiber stack), Q257 (Michael-Scott queue), Q261 (ABA / AtomicStampedReference), Q49 (happens-before)
