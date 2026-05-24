package org.kata.cinema;

/**
 * Value type identifying a single seat within a screening's hall by its (row, col) coordinate.
 *
 * <p>Modelled as a {@code record} so equality and hashing are derived from the components — this
 * is critical because seats are used as keys in the {@code held} and {@code booked} maps inside
 * {@link ConcurrentSeatBookingService}. Two {@code Seat} instances with the same coordinates
 * must collide in a {@link java.util.HashMap}, otherwise concurrency checks would silently fail.
 *
 * <p>Coordinates are 1-indexed (row 1 / col 1 is the first seat) to match how humans label
 * cinema seats; the compact constructor rejects non-positive values up front rather than
 * letting bad data propagate into the booking state.
 */
public record Seat(int row, int col) {
    public Seat {
        if (row < 1 || col < 1) throw new IllegalArgumentException("row/col must be positive");
    }
}
