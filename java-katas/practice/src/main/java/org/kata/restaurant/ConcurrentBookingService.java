package org.kata.restaurant;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Thread-safe booking service.
 *
 * <p>Lock granularity: per-table {@link ReentrantLock} — concurrent bookings on
 * different tables don't contend. Each {@code book} call only ever holds one lock
 * at a time, so the design is deadlock-free by construction.
 *
 * <p>Reads are lock-free via {@link ConcurrentHashMap}. Iterators are weakly
 * consistent — see {@link #cancel} for the implications.
 *
 * <p>Lock-granularity progression worth talking about:
 * <ol>
 *   <li>{@code synchronized} on the whole service — simplest, serialises everything.</li>
 *   <li>Per-table {@link ReentrantLock} (this class) — parallelism across tables.</li>
 *   <li>{@code ConcurrentHashMap.compute()} for atomic check-and-insert on the
 *       per-table index — no explicit locks at all.</li>
 *   <li>DB-level optimistic (version CAS) or pessimistic (SELECT FOR UPDATE) — when
 *       the source of truth is shared across processes.</li>
 * </ol>
 */
public class ConcurrentBookingService implements BookingService {

    private final List<Table> tables;
    private final Map<UUID, Booking> bookings = new ConcurrentHashMap<>();
    private final Map<Integer, ReentrantLock> tableLocks;

    public ConcurrentBookingService(List<Table> tables) {
        this.tables = List.copyOf(tables);
        this.tableLocks = tables.stream()
                .collect(Collectors.toUnmodifiableMap(Table::id, t -> new ReentrantLock()));
    }

    /**
     * Best-fit by capacity (smallest table that fits) — reduces fragmentation.
     *
     * <p>Sort is genuinely needed here, unlike the single-threaded version: if a small
     * party loses the race for the 2-top we fall back to the 4-top, then 6-top, etc.
     * Iterating in stable capacity order ensures larger tables are tried last.
     *
     * <p>Holding the per-table lock makes the {@code isFree → put} pair atomic with
     * respect to other writers for the same table. Writers for other tables proceed
     * in parallel; we filter them out in {@code isFree} so weakly-consistent iteration
     * is safe.
     */
    @Override
    public Optional<Booking> book(int partySize, TimeSlot slot, String customer) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    /**
     * Deliberately does <em>not</em> acquire the table lock. {@code ConcurrentHashMap.remove}
     * is atomic, so the cancel itself is safe.
     *
     * <p>Race window: a concurrent {@code book} call mid-{@code isFree} scan can miss the
     * cancellation and return {@code empty()} even though the slot just freed. This is
     * fail-safe — the caller can retry. Acquiring the table lock would add contention
     * with every booking attempt for no correctness gain. If false rejections matter
     * for UX, the fix isn't a coarser lock — it's a client-side retry or a queue.
     */
    @Override
    public boolean cancel(UUID bookingId) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public List<Booking> bookingsFor(LocalDate date) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    // O(B) full-map scan filtered by table. Safe under weak consistency because writes
    // for this table are serialised by the lock we currently hold; writes for other
    // tables are filtered out and so can't affect the result.
    //
    // Scaling fix: replace `Map<UUID, Booking>` with
    //   Map<Integer, NavigableMap<LocalDateTime, Booking>> byTable;
    // Overlap check becomes floorEntry(slot.start) + ceilingEntry(slot.start) — two
    // O(log n) lookups against just this table's bookings. The inner TreeMap doesn't
    // need to be thread-safe: we already hold the per-table lock when mutating it.
    private boolean isFree(Table table, TimeSlot slot) {
        throw new UnsupportedOperationException("TODO: implement");
    }
}
