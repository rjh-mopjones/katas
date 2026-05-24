package org.kata.parking;

/**
 * Standard-sized spot with a charger. Accepts only EVs — a regular car here would block
 * the charger from the vehicle that needs it.
 *
 * <p>This is the variant that makes the Liskov argument concrete: physically it looks like
 * a {@link StandardSpot}, but its {@code fits} predicate is strictly narrower. Modelling
 * it as a sibling rather than a subclass of {@code StandardSpot} keeps the type system
 * honest. See {@link Spot} for the full discussion.
 *
 * <p>{@code sizeRank == 2} — same physical size as a standard spot.
 */
public record EVSpot(int id) implements Spot {
    public boolean fits(Vehicle v) {
        return v.type() == VehicleType.EV;
    }
    public int sizeRank() { return 2; }
}
