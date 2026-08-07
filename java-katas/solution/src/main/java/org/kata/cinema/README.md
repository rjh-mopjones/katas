# Cinema Seat Booking Service

## Approach
State is partitioned per screening: a `ScreeningState` record holds four maps — `held` (seat →
holdId), `booked` (seat → bookingId), `holds` (holdId → `Hold`), and `bookings` (holdId →
`Booking`) — guarded by a single `ReentrantLock` for that screening only. Two users booking
different screenings never contend; two users booking the *same* screening serialize, which is the
correct contention boundary since that's the shared seat map. A top-level
`ConcurrentHashMap<UUID, ScreeningState>` creates each screening's state lazily and atomically via
`computeIfAbsent`.

`hold` is an all-or-nothing check-and-act: it sweeps expired holds first (lazy cleanup, no
background thread), then checks every requested seat against both `held` and `booked` before
reserving any of them — all inside the same critical section, so there's no gap between "is it
free?" and "mark it taken." A cross-screening secondary index, `holdToScreening` (holdId →
screeningId), gives `confirm` and `release` O(1) routing to the right per-screening lock without
scanning every screening; it's maintained in lockstep with the primary `holds` map at every
mutation point (`hold`, `sweepExpired`, `confirm`).

`confirm` is idempotent: it checks `bookings.get(holdId)` first and returns the existing `Booking`
unchanged if found, before touching anything else. This is what makes payment-retry-on-timeout
safe — a mobile client resending a lost-response confirm gets the same booking back rather than a
double charge or an error. Promotion (hold → booking) removes the hold, flips each seat from
`held` to `booked`, and writes the booking — all under the same lock, so no other thread ever
observes a half-promoted state.

## The real challenge
- **Atomic check-and-act.** The conflict check (is any seat taken?) and the seat reservation (mark them held) must happen inside the same critical section. Any gap between check and act is a TOCTOU race that allows double-booking.
- **Per-screening lock, not a global lock.** Two users booking different screenings should never contend. Partition state and locks by `screeningId`; use `ConcurrentHashMap.computeIfAbsent` to create per-screening state atomically.
- **Secondary index for O(1) routing.** `confirm` and `release` receive only a `holdId`. Without a `holdId → screeningId` index you would scan every screening's state on every call. Maintain this index in lockstep with the primary hold state, inside the same critical section.
- **Idempotent confirm.** Check `bookings.get(holdId)` before doing any work; return the existing `Booking` if found. This is the property that makes payment retries safe.
- **Caller-supplied `now`.** The interface takes an `Instant` parameter rather than calling the system clock internally, making TTL expiry fully testable without `Thread.sleep` or clock stubs.

## Common mistakes & senior signal
- Checking all seats, then reserving them in a second pass without holding the lock across both — this reopens the TOCTOU race the whole exercise is testing for.
- Using one global lock for all screenings "to keep it simple" — correct, but a strong candidate volunteers the throughput cost and proposes per-screening partitioning unprompted.
- Forgetting to maintain `holdToScreening` at every place a hold is created or removed (`hold`, `sweepExpired`, expiry-inside-`confirm`) — a stale secondary index silently breaks `release`/`confirm` routing.
- Re-checking `now >= expiresAt` with `System.currentTimeMillis()` internally instead of taking `Instant now` as a parameter — makes TTL behavior untestable without real sleeps.
- Not special-casing an already-confirmed `holdId` in `confirm` — this is the idempotency property, and skipping it is the single most common way this kata's implementation silently fails a retry test.

## Extensions
- Replace lazy sweep-on-`hold` with a scheduled background sweeper, and discuss the trade-off: simpler mental model for callers vs. a thread-pool lifecycle to manage and the risk of a janitor thread contending with request-handling threads for the same screening lock.
- Add a waitlist: when `hold` fails due to conflict, let a caller register interest and get notified if the seats free up (release or expiry).
- Support partial holds with a defined fallback (e.g. best-effort alternate seats) instead of strict all-or-nothing — and discuss why that complicates the atomicity guarantee.
- Expire `holdToScreening` entries for long-confirmed bookings once idempotency is no longer needed, to bound memory in a long-running screening.

## Reference
- Worked solution: this package (`solution/src/main/java/org/kata/cinema/`)
- Java Interview Primer: Q38 (thread safety), Q78 (optimistic vs pessimistic locking), Q241 (atomic check-and-act)
