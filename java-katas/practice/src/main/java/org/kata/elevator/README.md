# Elevator Controller

> Design and implement a multi-car elevator system with LOOK scheduling and a dispatcher that routes hall calls to the best car.

## The problem
Model a bank of elevators serving a building. Each car runs the LOOK algorithm: it travels in one direction, servicing all pending stops on that side, then reverses only when the current side is exhausted. A central dispatcher receives external hall calls (floor button presses) and assigns them to the lowest-cost available car. Internal cab-button presses go directly to the addressed car. Advancing the simulation by one floor step moves all cars.

## Requirements
- Each car advances exactly one floor per simulation step. A step should report whether a scheduled stop was reached on that step.
- When a new target is added to an idle car, the car immediately chooses a direction (toward the nearer target; ties broken toward UP).
- LOOK direction rules after every step: (1) continue if stops remain on the current side; (2) reverse if only the opposite side has stops; (3) go idle if no stops remain.
- A hall call must be assigned to the lowest-cost available car, and that car's target queue gains the requested floor. The dispatch result identifies the chosen car.
- An in-car button press routes directly to the specified car.
- All controller entry points are `synchronized` (the controller is the single serialisation point).
- A request's direction must be `UP` or `DOWN`; `IDLE` is rejected.

## What you're given
- `Direction` — enum (`UP`, `DOWN`, `IDLE`).
- `Request` — record of a hall call (`floor`, `direction`); rejects `IDLE` in its compact constructor.

You design the entire public API — method names, parameters, return types — and the internals from scratch.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/elevator/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
