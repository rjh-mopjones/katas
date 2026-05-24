package org.kata.parking;

/**
 * The largest bay — fits anything that drives. {@code fits} returns true unconditionally
 * because physically a truck bay can hold a motorcycle just fine; best-fit ordering, not
 * the predicate, is what stops us wasting a truck spot on a Vespa.
 *
 * <p>{@code sizeRank == 3} — the coarsest tier, so trucks bays are only chosen when no
 * smaller fitting spot is free (or the vehicle is a truck and nothing else fits).
 */
public record TruckSpot(int id) implements Spot {
    public boolean fits(Vehicle v) { return true; }   // truck-sized fits anything
    public int sizeRank() { return 3; }
}
