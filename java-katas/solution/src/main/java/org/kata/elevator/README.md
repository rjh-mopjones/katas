# Elevator Controller

## Approach
`Elevator` tracks its current floor and pending stops in a `NavigableSet<Integer>` (a `TreeSet`).
That choice is deliberate: LOOK scheduling only ever needs to ask "what's the nearest stop strictly
above/below me right now?", and `higher()`/`lower()` answer that in O(log n) while the set naturally
de-duplicates repeated targets. A `PriorityQueue` would need direction-specific comparators and can't
de-dupe; a `HashSet` would lose the ordering the algorithm depends on every tick.

The direction state machine is split into two methods on purpose. `chooseInitialDirection()` handles
the IDLE-to-moving transition when a new target wakes a parked car — it picks the side with the nearer
target, ties toward UP. `maybeReverseOrIdle()` handles the mid-trip transition after every `tick()`:
continue if the current side still has work, reverse if only the other side does, else go idle.
Keeping these separate (rather than one branchy method) means each transition only has to reason about
its own preconditions.

`ElevatorController` is a thin dispatcher: it doesn't own scheduling logic, only picks *which* car
serves a hall call via `Elevator.costFor(Request)`, then hands the floor to that car's own queue. Each
car still drives its own LOOK independently. All controller methods are `synchronized`, making the
controller itself the single serialization point for calls, cab-presses, and ticks.

## The real challenge
- **LOOK vs SCAN.** SCAN always travels to the building's extreme floor before reversing; LOOK only travels as far as the furthest pending request. Real elevators use LOOK. The state machine has two distinct transitions: IDLE-to-moving (`chooseInitialDirection`) and mid-trip direction exhaustion (`maybeReverseOrIdle`). Keep them separate — collapsing them into one branchy method makes each case harder to reason about correctly.
- **`NavigableSet` (TreeSet) for targets.** LOOK needs "what is the nearest stop strictly above/below my current floor?" in O(log n). `TreeSet.higher()` / `lower()` give exactly that. A `PriorityQueue` can't de-duplicate or answer directional queries cleanly; a `HashSet` loses ordering entirely.
- **Dispatch cost function.** Raw floor distance is a naive heuristic. A car moving away from the caller (heading up while the call is below, or down while the call is above) must traverse its full current run before it can serve the request. Add the building height as a penalty for moving-away cars so the dispatcher prefers a car already heading toward the caller, even if it is slightly further away.
- **De-duplication.** Adding the same target twice must not cause the car to stop twice. `TreeSet` handles this naturally.

## Common mistakes & senior signal
- Collapsing `chooseInitialDirection` and `maybeReverseOrIdle` into one method — it compiles, but the
  edge cases (parked car waking up vs. a car mid-trip exhausting a side) get tangled and hard to test
  independently. A senior answer keeps the two transitions separate.
- Using raw floor distance as the only dispatch cost — it produces pathological assignments (a
  descending car gets handed an up-call right beneath it and serves it last). Recognizing the
  directional-bias penalty is the signal.
- Reaching for `PriorityQueue` for the target set out of habit — it can't answer "nearest above/below
  current floor" without extra bookkeeping and doesn't de-duplicate.
- Forgetting that `addTarget` on an idle car must pick a direction *immediately*, not wait for the next
  `tick()` — otherwise the controller observes a stale IDLE state right after a call is placed.

## Extensions
- Multi-car simulation with throughput / average-wait metrics.
- Priority floors — fire-service mode (recall to ground), VIP / express floors.
- Capacity-aware dispatch — skip a car in `costFor()` if it is full.
- Door dwell time, acceleration profiles, energy-aware scheduling.
- Use `r.direction()` in `costFor` to prefer a same-direction car already passing through the call
  floor over an opposite-direction one (the current cost function doesn't yet use rider intent).
- Replace the `synchronized` monitor with a single-dispatcher-thread + `BlockingQueue<Request>`
  pipeline: producers (floor buttons, cab buttons, tick scheduler) enqueue events, one thread drains
  the queue with no contention, and back-pressure/ordering fall out for free.

## Reference
- Worked solution: this package (`solution/src/main/java/org/kata/elevator/`)
- Java Interview Primer: Design patterns / state machine, Q82 (observer-style dispatch), Q85 (SOLID)
