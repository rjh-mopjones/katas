package org.kata.parking;

/**
 * Mid-tier spot. Fits cars and EVs (an EV in a standard spot simply doesn't charge),
 * but rejects motorcycles (a separate fits-rule lane keeps bikes in compact spots where
 * they don't waste a full bay) and trucks.
 *
 * <p>{@code sizeRank == 2} — same tier as {@link EVSpot}. The {@code fits}-rule difference,
 * not size, is what separates them; tying them with the same rank lets the best-fit
 * comparator treat them as interchangeable on capacity while their predicates do the
 * actual filtering.
 */
public record StandardSpot(int id) implements Spot {
    public boolean fits(Vehicle v) {
        return v.type() == VehicleType.CAR || v.type() == VehicleType.EV;
    }
    public int sizeRank() { return 2; }
}
