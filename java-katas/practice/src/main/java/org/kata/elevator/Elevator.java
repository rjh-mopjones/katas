package org.kata.elevator;

import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * Single elevator car implementing the <b>LOOK</b> scheduling algorithm.
 *
 * <p><b>LOOK</b>: keep moving in the current direction while pending stops remain on that side;
 * when none remain, reverse if there is work the other way; otherwise go idle.
 *
 * <p>Contrast with related disk-scheduling-style variants:
 * <ul>
 *   <li><b>SCAN</b> ("elevator algorithm" in OS textbooks) — always travels to the building's
 *       extreme floors regardless of whether stops are pending there. Simpler, but wastes travel.</li>
 *   <li><b>C-SCAN</b> (circular SCAN) — serves requests only while moving in one direction, then
 *       jumps back to the start without serving on the return. More uniform wait times, but the
 *       "jump back" is unnatural for a physical elevator.</li>
 *   <li><b>LOOK</b> (this implementation) — what real elevators actually do: only travel as far
 *       as the furthest pending request before reversing.</li>
 * </ul>
 *
 * <p>Extension paths worth discussing in interview:
 * <ul>
 *   <li>Multi-car simulation with throughput / average-wait metrics.</li>
 *   <li>Priority floors — fire-service mode (recall to ground), VIP / express floors.</li>
 *   <li>Capacity-aware dispatch — skip a car in {@code costFor()} if it is full.</li>
 *   <li>Door dwell time, acceleration profiles, energy-aware scheduling.</li>
 * </ul>
 */
public class Elevator {

    private final int id;
    private final int minFloor;
    private final int maxFloor;
    private int currentFloor;
    private Direction direction = Direction.IDLE;

    // NavigableSet (TreeSet) is the right structure for LOOK: it is sorted and de-duplicating,
    // so we can ask "what is the next target strictly above/below the current floor?" in O(log n)
    // via higher()/lower(). A PriorityQueue would force direction-specific comparators and
    // would not de-duplicate; a plain HashSet would lose the ordering we need every tick.
    private final NavigableSet<Integer> targets = new TreeSet<>();

    public Elevator(int id, int currentFloor, int minFloor, int maxFloor) {
        if (minFloor > maxFloor) throw new IllegalArgumentException("minFloor > maxFloor");
        if (currentFloor < minFloor || currentFloor > maxFloor)
            throw new IllegalArgumentException("currentFloor out of range");
        this.id = id;
        this.minFloor = minFloor;
        this.maxFloor = maxFloor;
        this.currentFloor = currentFloor;
    }

    public int id() { return id; }
    public int currentFloor() { return currentFloor; }
    public Direction direction() { return direction; }

    /**
     * Enqueue a stop. Used both for external hall calls (via the dispatcher) and internal
     * cab-button presses (rider selects a destination). Same data path — the LOOK algorithm
     * does not care where the target came from, only where it sits relative to the car.
     */
    public void addTarget(int floor) {
        // If we were parked, this new target is what wakes the car up — pick a direction now
        // rather than waiting for the next tick, so the controller can observe an accurate state.
        throw new UnsupportedOperationException("TODO: implement");
    }

    /**
     * One simulation step. Advances the car by exactly one floor toward the next target,
     * checks whether that floor was a scheduled stop, and then re-evaluates the direction
     * (continue / reverse / idle) for the next tick.
     *
     * <p>Returns {@code true} iff a target was reached on this step — the caller can use that
     * signal to open doors, log arrivals, or surface metrics.
     */
    public boolean tick() {
        throw new UnsupportedOperationException("TODO: implement");
    }

    /**
     * State-machine entry transition: IDLE → UP / DOWN / IDLE.
     *
     * <p>Called only when the car is currently idle and a new target has just been added.
     * Chooses the side with the nearer target, breaking ties toward UP. Split from
     * {@link #maybeReverseOrIdle()} on purpose — entering motion from rest has different
     * semantics (no current direction to continue) than reacting to direction exhaustion
     * mid-trip, and keeping them separate makes each case obviously correct.
     */
    private void chooseInitialDirection() {
        throw new UnsupportedOperationException("TODO: implement");
    }

    /**
     * State-machine mid-trip transition: UP/DOWN → UP / DOWN / IDLE.
     *
     * <p>Heart of the LOOK algorithm. Called after every step:
     * <ol>
     *   <li>If targets remain on the current side, keep going.</li>
     *   <li>If the current side is exhausted but the other side has work, reverse.</li>
     *   <li>Otherwise the queue is empty (or only had the floor we just left) — go idle.</li>
     * </ol>
     *
     * <p>Splitting this from {@link #chooseInitialDirection()} keeps each transition focused
     * on its own preconditions rather than collapsing everything into one branchy update.
     */
    private void maybeReverseOrIdle() {
        // Direction exhausted — reverse if anything remains on the other side
        throw new UnsupportedOperationException("TODO: implement");
    }

    /**
     * Dispatch cost: lower is a better candidate to serve {@code r}.
     *
     * <p>Base cost is raw floor distance — the obvious nearest-car heuristic. The two penalties
     * implement directional bias: if this car is moving <em>away</em> from the caller (heading up
     * while the call is below, or heading down while the call is above), it would have to fully
     * traverse its current run and reverse before reaching them. Adding the full building height
     * approximates that wasted travel so the dispatcher prefers a car already heading toward the
     * caller — even a slightly more distant one. Without this bias, "nearest car" produces
     * pathological assignments where a descending car gets handed an up-call right beneath it
     * and serves it last.
     *
     * <p>Note: this implementation does not yet use {@code r.direction()} to match the rider's
     * intended travel direction — a richer cost function would prefer a same-direction car
     * passing through the call floor over an opposite-direction one. Left as an extension.
     */
    public int costFor(Request r) {
        throw new UnsupportedOperationException("TODO: implement");
    }
}
