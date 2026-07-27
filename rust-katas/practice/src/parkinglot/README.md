# Parking Lot

> The "hello world" of low-level design: vehicles of different sizes park in spots of different sizes, best-fit, and a ticket frees the exact spot.

## The problem

A single-level lot has spots of three sizes — Small, Medium, Large. Vehicles park under fit rules and
we allocate the smallest spot that fits (best-fit, so big vehicles aren't starved). Parking returns a
`Ticket`; returning the ticket frees that spot.

## Requirements

- **Fit rules:** a Motorcycle fits Small, Medium, or Large; a Car fits Medium or Large; a Truck fits
  Large only.
- **Best-fit:** `park` uses the *smallest* free spot that fits (a motorcycle takes a Small if one's
  free, spilling up to Medium then Large only when smaller sizes are full).
- No fitting spot free → `Err(ParkError::Full)`.
- `unpark(ticket)` frees exactly that spot; a bogus or already-returned ticket → `Err(UnknownTicket)`.
- `available(kind)` = free spots of that size.

## What you implement

- `ParkingLot::new(small, medium, large)`, `park(&mut self, Vehicle) -> Result<Ticket, ParkError>`,
  `unpark(&mut self, Ticket) -> Result<(), ParkError>`, `available(&self, SpotKind) -> usize`.

`VehicleKind`, `SpotKind`, `Vehicle`, `Ticket`, `ParkError` are provided. You design the storage.

## The real challenge

- **Enums + exhaustive `match`.** Model the fit rules as a `match` on `VehicleKind` returning the spot
  kinds it may use, in best-fit order. The exhaustive `match` means adding a vehicle kind won't compile
  until you place it — the compiler keeps the rules honest.
- **Best-fit** is just trying those spot kinds in order and taking the first with a free spot.
- **The ticket is a capability.** It carries what's needed to free the spot; `unpark` takes it by
  value and rejects a ticket that doesn't match an occupied spot (so you can't double-free).

## Run

There are no tests here — writing them is part of the exercise. Add a `#[cfg(test)] mod tests`
(best-fit per vehicle, spill-up when a size is full, car-never-uses-small, truck-needs-large, unpark
frees + double-unpark rejected, availability), then:

```
cd rust-katas && cargo test -p practice parkinglot
```

## Reference

Worked solution: `rust-katas/solution/src/parkinglot/`.

Extension: add multiple floors; then make the lot thread-safe with a `Mutex` and write a `Barrier`-
gated test where two cars race for the last spot (only one wins).

Background: [The Rust Book — enums & `match`](https://doc.rust-lang.org/book/ch06-02-match.html).
