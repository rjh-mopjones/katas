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
- Concurrent bookings on different tables must not contend with each other.

## What you implement
Implement `InMemoryBookingService` and `ConcurrentBookingService` from scratch — both expose the `BookingService` public API (`book`, `cancel`, `bookingsFor`). You design the internal data structures yourself.

Also implement `TimeSlot.overlaps(TimeSlot)` — the `end()` helper and record components are provided and working.

(`Booking`, `Table`, and `BookingService` are provided as working fixtures.)

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/restaurant/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
