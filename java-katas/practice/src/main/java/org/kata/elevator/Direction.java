package org.kata.elevator;

/**
 * Travel state of an elevator car.
 *
 * <p>{@code UP} and {@code DOWN} are the two active modes that drive the LOOK algorithm —
 * the car keeps moving in its current direction until no further targets remain on that side,
 * then either reverses (if work exists the other way) or transitions to {@code IDLE}.
 *
 * <p>{@code IDLE} is a state, not a destination: an idle car is parked and waiting.
 * It is intentionally <em>not</em> a valid value for an external {@link Request} — riders
 * always press "up" or "down", never "idle".
 */
public enum Direction { UP, DOWN, IDLE }
