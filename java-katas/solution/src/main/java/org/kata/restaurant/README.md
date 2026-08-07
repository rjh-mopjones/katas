# Restaurant Booking

## Approach
Two implementations share the same public contract (`book`, `cancel`, `bookingsFor`) but make
different concurrency trade-offs on purpose — the interest is in the second.

- **Best-fit seating.** `book` filters tables to those that fit the party, sorts by capacity
  ascending, and takes the first free one. Picking the smallest fit keeps large tables free for
  large parties (a party of 2 on the 8-top blocks a later party of 6) — the same reasoning as a
  best-fit memory allocator. It is greedy and suboptimal; optimal seating is bin-packing (NP-hard),
  fine to leave to a nightly batch solver.
- **`InMemoryBookingService`** (single-threaded reference) keeps bookings in a plain `HashMap<UUID,
  Booking>` and answers `isFree` by scanning the map filtered to the target table. Plain `HashMap`
  is deliberate: paying `ConcurrentHashMap`'s striped-locking cost for a guarantee you don't
  exercise is waste. It is **not** thread-safe, and that is the point of the kata.
- **`ConcurrentBookingService`** makes the check-and-act atomic per table. Bookings live in a
  `ConcurrentHashMap` (lock-free reads); each table has its own `ReentrantLock`. `book` walks
  candidate tables in capacity order, and for each one locks, re-checks `isFree`, inserts, and
  unlocks. Because a call holds **at most one lock at a time**, the design is deadlock-free by
  construction, and bookings on different tables run fully in parallel.
- **`cancel`** relies on `ConcurrentHashMap.remove` being atomic and deliberately takes **no** table
  lock. A concurrent `book` mid-scan can miss the cancellation and return `empty()` for a slot that
  just freed — a fail-safe false rejection the caller can retry, not a correctness bug. Adding a lock
  here would contend with every booking for no correctness gain.
- **Overlap** is a half-open interval test on `TimeSlot`: `start < other.end && other.start < end`
  (strict inequalities), so back-to-back slots do not count as overlapping.

## The real challenge
- **Overlap logic**: the predicate `start < other.end && other.start < end` (strict inequalities) is a precise half-open interval test — adjacent slots must not be treated as overlapping.
- **Atomic check-and-act**: checking `isFree` and inserting the booking must be a single locked unit per table; any gap between the two steps is a race window for a double-booking.
- **Lock granularity**: holding one per-table `ReentrantLock` — not a single service-wide lock — allows parallel bookings on different tables. Each `book` call must hold at most one lock at a time, which eliminates deadlock by construction.
- **Why the race test breaks**: the unsynchronised `InMemoryBookingService` has an unsynchronised check-then-act, so two threads can both see a table as free and both confirm a booking for the same slot — the test exists to make this observable.

## Common mistakes & senior signal
- **One service-wide lock.** Correct, but it serialises every booking — a party for table 1 waits on
  a party for table 9. A strong answer reaches for per-table locks and can explain why that is still
  deadlock-free (one lock held at a time).
- **A gap between check and act.** Reading `isFree` outside the lock and then inserting inside it (or
  vice versa) reopens the exact race the kata is about. The check and the insert must be under the
  same lock acquisition.
- **Throwing on a full restaurant.** "No table available" is an expected outcome, not exceptional —
  return `Optional.empty()`. Exceptions for control flow are expensive and hide the failure mode from
  the signature.
- **Inclusive overlap bounds.** Using `<=` treats adjacent slots (one ends exactly as the next
  begins) as overlapping, wrongly blocking a valid booking. Half-open intervals need strict `<`.
- **Over-locking `cancel`.** Locking on cancel to close the false-rejection window trades real
  contention for a fail-safe non-bug — the senior move is to name it as fail-safe and push retries to
  the client.
- **Not seeing the race test for what it is.** `InMemoryBookingServiceRaceTest` asserts the bug
  *manifests*; it is intentionally non-deterministic and may pass on a warm JVM. Recognising that
  (rather than "fixing the flaky test") is the signal.

## Extensions
- **Faster `isFree`.** The full-map scan is O(B). At 10k+ bookings, index by table:
  `Map<Integer, NavigableMap<LocalDateTime, Booking>>`, and answer overlap with
  `floorEntry`/`ceilingEntry` — two O(log n) lookups against just that table's bookings. Under the
  per-table lock the inner `TreeMap` needn't be thread-safe.
- **Lock-free writes.** Replace the explicit per-table `ReentrantLock` with
  `ConcurrentHashMap.compute()` on the per-table index for an atomic check-and-insert with no explicit
  locks at all.
- **Richer failure modes.** If more than "full" can fail (closed, blacklisted customer), switch the
  return from `Optional<Booking>` to a sealed `BookingResult = Confirmed | Rejected(Reason)`.
- **Cross-process source of truth.** When bookings are shared across processes, move the atomicity to
  the database: optimistic (version CAS) or pessimistic (`SELECT ... FOR UPDATE`) locking.

## Reference
- Worked solution: this package (`solution/src/main/java/org/kata/restaurant/`)
- Java Interview Primer: Q38 (thread safety), Q40 (deadlock), Q241 (atomic check-and-act)
