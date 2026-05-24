package org.kata.parking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Thread-safe parking lot.
 *
 * <p>Concurrency strategy: per-spot {@link ReentrantLock}, tried in best-fit order. Two cars
 * racing for the same spot — one wins the lock + occupies, the other releases and moves to
 * the next candidate. We deliberately avoid a single lot-wide lock; that would serialise all
 * parks across an N-bay lot for no good reason. Per-spot locks give us contention only on
 * the bays actually being fought over.
 *
 * <p>Pricing: flat hourly rate, partial hours rounded up. Real lots use tiered rates; out of scope.
 */
public class ConcurrentParkingLot implements ParkingLot {

    // BigDecimal, not double: money must not silently lose pennies to binary floating-point
    // rounding. Constructed from a String literal so the value is exact (new BigDecimal(2.50)
    // would inherit double's imprecision before BigDecimal ever saw the number).
    private static final BigDecimal HOURLY_RATE = new BigDecimal("2.50");

    private final List<Spot> spots;
    private final Map<Integer, ReentrantLock> spotLocks;
    private final Map<Integer, Ticket> occupants = new ConcurrentHashMap<>();   // spotId → Ticket
    private final Map<UUID, Ticket> tickets = new ConcurrentHashMap<>();        // ticketId → Ticket

    public ConcurrentParkingLot(List<Spot> spots) {
        // List.copyOf produces an immutable snapshot — the lot's physical layout cannot mutate
        // after construction, so readers (park, available) need no synchronisation to iterate it.
        // toUnmodifiableMap freezes the spotId→lock mapping for the same reason: the set of locks
        // is fixed at construction, so lookups are safe lock-free reads from any thread.
        this.spots = List.copyOf(spots);
        this.spotLocks = spots.stream()
                .collect(Collectors.toUnmodifiableMap(Spot::id, s -> new ReentrantLock()));
    }

    /**
     * Park using best-fit allocation.
     *
     * <p>Best-fit = smallest {@link Spot#sizeRank} whose {@link Spot#fits} accepts this vehicle.
     * The alternative — first-fit — wastes capacity: a motorcycle parked in the first matching
     * spot might land in a truck bay, leaving no room for a truck. Best-fit minimises that
     * fragmentation by reserving larger bays for vehicles that genuinely need them.
     *
     * <p>Concurrency: we walk the sorted candidates and try to acquire each spot's lock in
     * turn. Inside the critical section we re-check occupancy — another thread may have taken
     * the spot between our filter and our lock acquisition. On loss we release and try the
     * next candidate; on win we record the ticket and return. No deadlock risk because each
     * call acquires at most one lock at a time.
     */
    @Override
    public Optional<Ticket> park(Vehicle vehicle, Instant entry) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public Optional<BigDecimal> unpark(UUID ticketId, Instant exit) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public long available(VehicleType type) {
        throw new UnsupportedOperationException("TODO: implement");
    }
}
