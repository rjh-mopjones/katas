# Concurrent Parking Lot

> Design a type-safe parking lot with vehicle/spot fit rules and best-fit allocation that holds up under concurrent access.

## The problem
Model a multi-type parking lot containing compact, standard, EV-charging, and truck bays. Each spot type has its own rules about which vehicles it accepts. When a vehicle parks, allocate the smallest spot that fits (best-fit), issue a timed ticket, and charge on exit (partial hours rounded up). Under concurrent load — many cars arriving and leaving simultaneously — there must never be two vehicles in the same spot.

## Requirements
- `park(vehicle, entry)` allocates the smallest fitting spot (lowest `sizeRank`) and returns a `Ticket`, or `Optional.empty()` if no compatible spot is free.
- Fit rules: `CompactSpot` fits `MOTORCYCLE` and `CAR`; `StandardSpot` fits `CAR` and `EV`; `EVSpot` fits `EV` only; `TruckSpot` fits everything.
- `unpark(ticketId, exit)` returns the charge (`BigDecimal`), or `Optional.empty()` for an unknown ticket. Minimum charge is one hour; partial hours round up to the next full hour.
- `available(vehicleType)` returns the count of unoccupied spots that fit the given type.
- All three operations must be safe under concurrent use. Two threads racing to park in the same spot — one wins, the other moves to the next candidate without producing duplicate occupancy.
- `BigDecimal` pricing at a flat hourly rate; use banker's rounding (`HALF_EVEN`) on the final amount.

## What you implement
The signatures, fields, constructors, and Javadoc are already in place — fill in the logic for:
- `ConcurrentParkingLot` — `park`, `unpark`, `available`

(`ParkingLot` interface, `Spot` sealed hierarchy (`CompactSpot`, `StandardSpot`, `EVSpot`, `TruckSpot`), `Vehicle`, `VehicleType`, `Ticket` are provided as scaffolding.)

## The real challenge
- **Sealed spot hierarchy, not inheritance.** The instinctive design — `EVSpot extends StandardSpot` — violates the Liskov Substitution Principle: `EVSpot.fits` strengthens the precondition (rejects `CAR`) relative to `StandardSpot.fits`, so an `EVSpot` cannot substitute for a `StandardSpot`. Model the four spot types as sealed interface siblings instead — each answers its own `fits` predicate independently, and the compiler enforces exhaustive `switch` when you pattern-match.
- **Best-fit, not first-fit.** First-fit wastes large bays on small vehicles. Sort candidates by `sizeRank` ascending and take the first available. This keeps truck bays free for trucks.
- **Check-then-act inside the lock.** Between filtering candidates and acquiring a spot's lock, another thread may have taken that spot. Re-check `occupants.containsKey(spot.id())` inside the critical section — never outside.
- **Per-spot locks, not a global lock.** A lot-wide lock serialises all parks across every bay for no benefit. Use one `ReentrantLock` per spot; a thread holds at most one lock at a time, so there is no deadlock risk.
- **Ceiling billing without `double`.** `(milliseconds + 3_599_999) / 3_600_000` gives ceiling hours in pure integer arithmetic. Converting to `double` for `Math.ceil` introduces the very floating-point error `BigDecimal` was meant to prevent.

## Run
```
mvn -pl practice test -Dtest=ConcurrentParkingLotTest
```

## Reference
- Worked solution: `solution/src/main/java/org/kata/parking/`
- Java Interview Primer: Q85 (SOLID / LSP), Q79 (design patterns), Q38 (thread safety)
