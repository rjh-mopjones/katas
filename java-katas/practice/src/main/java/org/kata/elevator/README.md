# Elevator Controller

> Design and implement a multi-car elevator system with LOOK scheduling and a dispatcher that routes hall calls to the best car.

## The problem
Model a bank of elevators serving a building. Each car runs the LOOK algorithm: it travels in one direction, servicing all pending stops on that side, then reverses only when the current side is exhausted. A central dispatcher receives external hall calls (floor button presses) and assigns them to the lowest-cost available car. Internal cab-button presses go directly to the addressed car. A `tick()` advances the simulation by one floor step across all cars.

## Requirements
- Each `Elevator` advances exactly one floor per `tick()`. A tick returns `true` if a scheduled stop was reached on that step.
- When a new target is added to an idle car, the car immediately chooses a direction (toward the nearer target; ties broken toward UP).
- LOOK direction rules after every step: (1) continue if stops remain on the current side; (2) reverse if only the opposite side has stops; (3) go idle if no stops remain.
- The controller's `call(Request)` must pick the car with the lowest `costFor` score and add the floor to that car's target queue. Returns the chosen car's id.
- `selectFloor(elevatorId, floor)` routes an in-car button press to the specified car.
- All controller methods are `synchronized` (the controller is the single serialisation point).
- `Request.direction` must be `UP` or `DOWN`; `IDLE` is rejected.

## What you implement
Implement `Elevator` and `ElevatorController` from scratch — the public API (`addTarget`, `tick`, `costFor`, `id`, `currentFloor`, `direction` on `Elevator`; `call`, `selectFloor`, `tick`, `elevators` on `ElevatorController`). You design the internal fields, LOOK state machine, and any private helpers yourself.

(`Direction` enum and `Request` record are provided as fully working scaffolding.)

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/elevator/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
