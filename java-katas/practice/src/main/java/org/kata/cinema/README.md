# Cinema Seat Booking Service

> Implement a two-phase seat reservation system that is safe under concurrent load — no double-booking, ever.

## The problem
Users pick seats, pay, and then receive a confirmed booking. Because selection and payment are separated by an arbitrary delay, you need a two-phase model: a `hold` temporarily locks seats for a TTL window; a `confirm` promotes the hold to a permanent booking once payment succeeds. Abandoned holds expire automatically. Your implementation must prevent any two users from ever holding or booking the same seat at the same time.

## Requirements
- `hold` must atomically reserve the entire requested seat set or fail completely — no partial holds.
- A hold conflicts with any seat that is already held (and not expired) or already booked; `Optional.empty()` is returned on conflict.
- `confirm` must be idempotent: retrying the same `holdId` returns the identical `Booking` without double-charging.
- A hold that has expired (wall-clock `now >= expiresAt`) cannot be confirmed; `Optional.empty()` is returned.
- `release` frees a hold manually; subsequent `hold` or `confirm` calls for those seats must succeed.
- Expiry is checked lazily on each `hold` call (sweep expired holds before the conflict check); no background thread is required.
- All operations must be safe under concurrent access from many threads for the same or different screenings.

## What you implement
Implement `ConcurrentSeatBookingService` from scratch — the public API (`hold`, `confirm`, `release`). You design the internal state layout, locking strategy, secondary indexes, and any private helpers yourself.

(`SeatBookingService` interface, `Seat`, `Hold`, `Booking`, and `Screening` are provided as fully working scaffolding.)

## The real challenge
- **Atomic check-and-act.** The conflict check (is any seat taken?) and the seat reservation (mark them held) must happen inside the same critical section. Any gap between check and act is a TOCTOU race that allows double-booking.
- **Per-screening lock, not a global lock.** Two users booking different screenings should never contend. Partition state and locks by `screeningId`; use `ConcurrentHashMap.computeIfAbsent` to create per-screening state atomically.
- **Secondary index for O(1) routing.** `confirm` and `release` receive only a `holdId`. Without a `holdId → screeningId` index you would scan every screening's state on every call. Maintain this index in lockstep with the primary hold state, inside the same critical section.
- **Idempotent confirm.** Check `bookings.get(holdId)` before doing any work; return the existing `Booking` if found. This is the property that makes payment retries safe.
- **Caller-supplied `now`.** The interface takes an `Instant` parameter rather than calling the system clock internally, making TTL expiry fully testable without `Thread.sleep` or clock stubs.

## Run
```
mvn -pl practice test -Dtest=ConcurrentSeatBookingServiceTest
```

## Reference
- Worked solution: `solution/src/main/java/org/kata/cinema/`
- Java Interview Primer: Q38 (thread safety), Q78 (optimistic vs pessimistic locking), Q241 (atomic check-and-act)
