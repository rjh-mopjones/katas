# Restaurant Booking

> Design a table reservation service that is correct under concurrent load.

## The problem
A restaurant has a fixed set of tables, each with a capacity. Guests call in to book a table for a given time slot; the system must find the smallest available table that fits the party, confirm the booking, and later allow cancellations. A second implementation must be safe when many booking requests arrive simultaneously.

## Requirements
- `book` returns `Optional.empty()` when no suitable table is available — not an exception.
- Table selection uses best-fit (smallest table that fits the party size) to minimise fragmentation.
- A `TimeSlot` overlaps another when the intervals intersect: `[start, start+duration)` half-open. Zero-length or negative durations are rejected at construction.
- `bookingsFor(date)` returns all bookings whose slot starts on the given date.
- `cancel` returns `true` if the booking existed and was removed, `false` otherwise.
- `ConcurrentBookingService` must be free of double-bookings under concurrent calls — a stress test (`InMemoryBookingServiceRaceTest`) intentionally demonstrates that the unsynchronised version breaks; that test is non-deterministic and may pass occasionally.
- Per-table locking: concurrent bookings on different tables must not contend with each other.

## What you implement
Implement `InMemoryBookingService` and `ConcurrentBookingService` from scratch — both expose the `BookingService` public API (`book`, `cancel`, `bookingsFor`). You design the internal data structures yourself.

Also implement `TimeSlot.overlaps(TimeSlot)` — the `end()` helper and record components are provided and working.

(`Booking`, `Table`, and `BookingService` are provided as working fixtures.)

## The real challenge
- **Overlap logic**: the predicate `start < other.end && other.start < end` (strict inequalities) is a precise half-open interval test — adjacent slots must not be treated as overlapping.
- **Atomic check-and-act**: checking `isFree` and inserting the booking must be a single locked unit per table; any gap between the two steps is a race window for a double-booking.
- **Lock granularity**: holding one per-table `ReentrantLock` — not a single service-wide lock — allows parallel bookings on different tables. Each `book` call must hold at most one lock at a time, which eliminates deadlock by construction.
- **Why the race test breaks**: the unsynchronised `InMemoryBookingService` has an unsynchronised check-then-act, so two threads can both see a table as free and both confirm a booking for the same slot — the test exists to make this observable.

## Run
```
mvn -pl practice test -Dtest=InMemoryBookingServiceTest,ConcurrentBookingServiceTest,InMemoryBookingServiceRaceTest,TimeSlotTest
```

## Reference
- Worked solution: `solution/src/main/java/org/kata/restaurant/`
- Java Interview Primer: Q38 (thread safety), Q40 (deadlock), Q241 (atomic check-and-act)
