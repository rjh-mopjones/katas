package org.kata.parking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Lot contract. Kept as an interface so a single-threaded reference implementation and a
 * thread-safe variant ({@link ConcurrentParkingLot}) can be swapped without callers noticing —
 * useful for benchmarking the cost of locking, and for tests that don't need concurrency.
 *
 * <p>Both park and unpark return {@link Optional} rather than throwing: "lot full" and
 * "unknown ticket" are normal, predictable outcomes, not exceptional ones.
 */
public interface ParkingLot {
    /** Best-fit park: smallest spot whose {@code fits} returns true. */
    Optional<Ticket> park(Vehicle vehicle, Instant entry);

    /** Returns the charge for the stay, or empty if ticket unknown. */
    Optional<BigDecimal> unpark(UUID ticketId, Instant exit);

    long available(VehicleType type);
}
