# Concurrent Parking Lot

## Approach
The four spot types (`CompactSpot`, `StandardSpot`, `EVSpot`, `TruckSpot`) are siblings of a sealed
`Spot` interface, each answering its own `fits` predicate independently rather than inheriting from one
another. `park` filters the lot's spots to the ones that fit the vehicle, sorts by `sizeRank` ascending
(best-fit — smallest spot that works), and walks the sorted candidates trying to claim each one in
turn.

Claiming is a per-spot `ReentrantLock`: one lock per spot, created once at construction and held for
the lifetime of the lot (`spots` and `spotLocks` are both frozen immutable snapshots, so no
synchronisation is needed just to iterate them). Inside a candidate's lock, `park` re-checks
`occupants.containsKey(spot.id())` — the classic check-then-act pattern, and it has to happen *inside*
the critical section because another thread may have taken that exact spot between the candidate scan
and the lock acquisition. A thread only ever holds one spot lock at a time, so there's no lock-ordering
deadlock risk. `occupants` and `tickets` are `ConcurrentHashMap`s; `unpark`'s `tickets.remove(...)`
being atomic is what makes that half of the lot race-free without needing a spot lock at all — only one
caller can ever observe a non-null removed ticket for a given id.

Billing avoids `double` entirely: `(millis + 3_599_999) / 3_600_000` is integer ceiling division
(any partial hour rounds up to a full hour), and the final charge is computed in `BigDecimal` with
`HALF_EVEN` (banker's) rounding on the cents.

## The real challenge
- **Sealed spot hierarchy, not inheritance.** The instinctive design — `EVSpot extends StandardSpot` — violates the Liskov Substitution Principle: `EVSpot.fits` strengthens the precondition (rejects `CAR`) relative to `StandardSpot.fits`, so an `EVSpot` cannot substitute for a `StandardSpot`. Model the four spot types as sealed interface siblings instead — each answers its own `fits` predicate independently, and the compiler enforces exhaustive `switch` when you pattern-match.
- **Best-fit, not first-fit.** First-fit wastes large bays on small vehicles. Sort candidates by `sizeRank` ascending and take the first available. This keeps truck bays free for trucks.
- **Check-then-act inside the lock.** Between filtering candidates and acquiring a spot's lock, another thread may have taken that spot. Re-check `occupants.containsKey(spot.id())` inside the critical section — never outside.
- **Per-spot locks, not a global lock.** A lot-wide lock serialises all parks across every bay for no benefit. Use one `ReentrantLock` per spot; a thread holds at most one lock at a time, so there is no deadlock risk.
- **Ceiling billing without `double`.** `(milliseconds + 3_599_999) / 3_600_000` gives ceiling hours in pure integer arithmetic. Converting to `double` for `Math.ceil` introduces the very floating-point error `BigDecimal` was meant to prevent.

## Common mistakes & senior signal
- Modelling `EVSpot`/`StandardSpot` with inheritance — a strengthened precondition on an override is
  a textbook LSP violation, even if it compiles fine.
- First-fit allocation instead of best-fit — technically satisfies "park the car" but fragments
  capacity, starving larger vehicles later.
- Checking occupancy before acquiring the lock instead of inside it — a textbook check-then-act race.
- One lot-wide lock "to be safe" — correct, but serialises every park across the whole lot for no
  reason; per-spot locks give contention only where cars are actually racing for the same bay.
- Converting to `double`/`Math.ceil` for the hour rounding — reintroduces the exact floating-point
  imprecision `BigDecimal` was chosen to avoid.
- Forgetting the minimum one-hour charge for a very short stay, or using plain rounding instead of
  banker's rounding on the final cents.

## Extensions
- **Tiered/dynamic pricing** — the reference lot uses one flat hourly rate; a real lot has day/night
  or demand-based tiers, which would push the rate lookup into its own strategy rather than a single
  constant.
- **Reservations** — allow a vehicle to hold a specific spot ahead of arrival, which changes `park`
  from "find any fitting free spot" to "honour a reservation if one exists, else best-fit."
- **Overflow/waitlist** — when `available` hits zero for a type, queue incoming vehicles instead of
  rejecting outright.

## Reference
- Worked solution: this package (`solution/src/main/java/org/kata/parking/`)
- Java Interview Primer: Q85 (SOLID / LSP), Q79 (design patterns), Q38 (thread safety)
