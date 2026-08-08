# Concurrent Parking Lot

> Design a type-safe parking lot with vehicle/spot fit rules and best-fit allocation that holds up under concurrent access.

## The problem
Model a multi-type parking lot containing compact, standard, EV-charging, and truck bays. Each spot type has its own rules about which vehicles it accepts. When a vehicle parks, allocate the smallest spot that fits (best-fit), issue a timed ticket, and charge on exit (partial hours rounded up). Under concurrent load — many cars arriving and leaving simultaneously — there must never be two vehicles in the same spot.

## Requirements
- Parking a vehicle allocates the smallest fitting spot (lowest `sizeRank`) and issues a ticket, or reports that no compatible spot is free.
- Fit rules: `CompactSpot` fits `MOTORCYCLE` and `CAR`; `StandardSpot` fits `CAR` and `EV`; `EVSpot` fits `EV` only; `TruckSpot` fits everything.
- Ending a stay returns the charge, or reports an unknown ticket. Minimum charge is one hour; partial hours round up to the next full hour.
- Checking availability for a vehicle type returns the count of unoccupied spots that fit it.
- All operations must be safe under concurrent use. Two threads racing to park in the same spot — one wins, the other moves to the next candidate without producing duplicate occupancy.
- Pricing is a flat hourly rate; use banker's rounding (`HALF_EVEN`) on the final amount.

## What you're given
`ParkingLot` interface, `Spot` sealed hierarchy (`CompactSpot`, `StandardSpot`, `EVSpot`, `TruckSpot`), `Vehicle`, `VehicleType`, and `Ticket` are provided as fully working scaffolding.

You design the entire public API — method names, parameters, return types — and the internals from scratch.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/parking/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
