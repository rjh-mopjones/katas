package org.kata.parking;

/**
 * Smallest spot in the lot. Fits motorcycles and standard cars; cannot accommodate EVs
 * (no charger) or trucks (too small).
 *
 * <p>{@code sizeRank == 1} — the most constrained tier, so best-fit will try compact spots
 * first for any vehicle that fits here, preserving larger bays for larger vehicles.
 */
public record CompactSpot(int id) implements Spot {
    public boolean fits(Vehicle v) {
        return v.type() == VehicleType.MOTORCYCLE || v.type() == VehicleType.CAR;
    }
    public int sizeRank() { return 1; }
}
