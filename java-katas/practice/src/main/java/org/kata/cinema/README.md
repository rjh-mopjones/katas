# Cinema Seat Booking Service

> Implement a two-phase seat reservation system that is safe under concurrent load — no double-booking, ever.

## The problem
Users pick seats, pay, and then receive a confirmed booking. Because selection and payment are
separated by an arbitrary delay, you need a two-phase model: a temporary hold locks seats for a
TTL window; confirming promotes a hold to a permanent booking once payment succeeds. Abandoned
holds expire automatically. Your implementation must prevent any two users from ever holding or
booking the same seat at the same time.

## Requirements
- Reserving a set of seats must be atomic — either the entire requested set succeeds or the whole
  reservation fails; no partial holds.
- A reservation attempt conflicts with any seat that is already held (and not expired) or already
  booked; a conflict yields no result.
- Promoting a hold to a booking must be idempotent: retrying with the same hold identifier returns
  the identical booking, without double-charging.
- A hold that has expired (wall-clock `now >= expiresAt`) cannot be promoted to a booking; that
  also yields no result.
- Manually releasing a hold frees its seats; subsequent reservation or promotion attempts for
  those seats must then succeed.
- Expiry is checked lazily on each reservation attempt (expired holds are swept before the
  conflict check); no background thread is required.
- All operations must be safe under concurrent access from many threads, for the same or
  different screenings.

## What you're given
- `SeatBookingService` — the interface the implementation satisfies.
- `Seat`, `Hold`, `Booking`, `Screening` — the domain/value types the service operates on, provided
  as fully working scaffolding.

You design the entire internal implementation — the state layout, locking strategy, secondary
indexes, and any private helpers — from scratch.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/cinema/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
