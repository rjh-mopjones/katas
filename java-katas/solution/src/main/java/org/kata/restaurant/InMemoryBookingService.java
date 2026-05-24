package org.kata.restaurant;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Single-threaded reference implementation.
 *
 * <p>Not thread-safe — see {@link ConcurrentBookingService} for the concurrent variant.
 * Uses plain {@link HashMap} deliberately: {@link java.util.concurrent.ConcurrentHashMap}
 * pays for striped locking and weakly-consistent iteration on every operation. Paying
 * for thread safety we don't use is paying for a guarantee we're not exercising.
 */
public class InMemoryBookingService implements BookingService {

    private final List<Table> tables;
    private final Map<UUID, Booking> bookings = new HashMap<>();

    public InMemoryBookingService(List<Table> tables) {
        this.tables = List.copyOf(tables);
    }

    /**
     * Picks the smallest table that fits — best-fit heuristic to reduce fragmentation.
     * If a party of 2 takes the 8-top, a later party of 6 can't be seated; picking the
     * smallest fit keeps large tables free for large parties. Same logic as best-fit
     * memory allocators. Greedy and suboptimal — optimal seating is bin-packing (NP-hard);
     * for interactive booking the greedy is fine, for nightly batch optimisation use a solver.
     *
     * <p>Note: single-threaded version could use {@code min(comparingInt(Table::capacity))}
     * — O(n) instead of O(n log n). The {@code sorted().findFirst()} shape is kept for
     * symmetry with {@link ConcurrentBookingService}, where sort order matters because
     * locks are tried in capacity order.
     */
    @Override
    public Optional<Booking> book(int partySize, TimeSlot slot, String customer) {
        return tables.stream()
                .filter(t -> t.capacity() >= partySize)
                .sorted(Comparator.comparingInt(Table::capacity))
                .filter(t -> isFree(t, slot))
                .findFirst()
                .map(t -> {
                    Booking b = new Booking(UUID.randomUUID(), t, slot, partySize, customer);
                    bookings.put(b.id(), b);
                    return b;
                });
    }

    @Override
    public boolean cancel(UUID bookingId) {
        return bookings.remove(bookingId) != null;
    }

    @Override
    public List<Booking> bookingsFor(LocalDate date) {
        return bookings.values().stream()
                .filter(b -> b.slot().startTime().toLocalDate().equals(date))
                .toList();
    }

    // O(B) over all bookings. Fine for the kata; at 10k+ bookings switch to a
    // per-table index: Map<Integer, NavigableMap<LocalDateTime, Booking>> — overlap
    // check becomes floorEntry/ceilingEntry against just this table's bookings,
    // two O(log n) lookups instead of a full scan.
    private boolean isFree(Table table, TimeSlot slot) {
        return bookings.values().stream()
                .filter(b -> b.table().equals(table))
                .noneMatch(b -> b.slot().overlaps(slot));
    }
}
