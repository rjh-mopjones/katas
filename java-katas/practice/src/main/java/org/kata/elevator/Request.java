package org.kata.elevator;

/**
 * External hall call: a rider standing on {@code floor} pressing the {@code direction} button.
 *
 * <p>The direction matters for dispatch: an up-call at floor 5 should ideally be served by a car
 * already heading up past 5, not by one descending toward 1 that would have to fully reverse.
 * The cost function in {@link Elevator#costFor(Request)} uses it to penalise moving-away cars.
 *
 * <p>{@link Direction#IDLE} is rejected in the compact constructor: a request must declare
 * intent (up or down). Idle is a car's state, not something a rider can ask for.
 */
public record Request(int floor, Direction direction) {
    public Request {
        if (direction == Direction.IDLE) throw new IllegalArgumentException("request must have UP or DOWN");
    }
}
