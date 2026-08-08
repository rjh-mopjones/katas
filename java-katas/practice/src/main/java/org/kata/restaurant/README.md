# Restaurant Booking

> Design a table reservation service that is correct under concurrent load.

## The problem
A restaurant has a fixed set of tables, each with a capacity. Guests call in to book a table for a given time slot; the system must find the smallest available table that fits the party, confirm the booking, and later allow cancellations. A second implementation must be safe when many booking requests arrive simultaneously.

## Requirements
- Attempting to book returns an empty result when no suitable table is available — not an exception.
- Table selection uses best-fit (the smallest table that fits the party size) to minimise fragmentation.
- Two time slots overlap when their intervals intersect, using a half-open range (`[start, start+duration)`). Zero-length or negative durations are already rejected at construction by the given time-slot fixture.
- Looking up bookings for a date returns all bookings whose slot starts on that date.
- Cancelling reports whether a booking existed and was removed.
- The concurrent implementation must be free of double-bookings under concurrent calls — a stress test intentionally demonstrates that an unsynchronised version breaks; that kind of test is inherently non-deterministic and may pass occasionally even against an unsafe implementation.
- Concurrent bookings on different tables must not contend with each other.

## What you're given
- `Table` — a table with an id and seating capacity (record, provided and working).
- `Booking` — a confirmed booking tying together an id, table, slot, party size, and customer (record, provided; validates party size against table capacity).
- `TimeSlot` — a start time + duration value. Its validation and an `end()` helper are provided and working; its overlap check is left for you to implement.
- `BookingService` — the interface both implementations satisfy.

You implement two versions of `BookingService` — an in-memory one and a concurrency-safe one — designing their internal data structures and concurrency strategy from scratch, plus `TimeSlot`'s overlap logic.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/restaurant/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
