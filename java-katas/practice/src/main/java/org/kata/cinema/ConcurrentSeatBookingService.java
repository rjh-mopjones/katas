package org.kata.cinema;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe, in-memory implementation of the two-phase {@link SeatBookingService} contract.
 *
 * <h2>Pattern: two-phase commit (hold-then-confirm with TTL)</h2>
 *
 * <p>Booking a seat is logically two acts separated by an arbitrary delay:
 * <ol>
 *   <li><b>Hold</b> — user picks seats; we exclusively reserve them for a TTL window so the
 *       checkout UI has a stable selection to pay against.</li>
 *   <li><b>Confirm</b> — payment succeeds; the hold is atomically promoted to a permanent
 *       {@link Booking}. If the user abandons checkout (closes the tab, app crash, network
 *       drop) the hold simply expires and the seats become available again — no compensating
 *       action is required.</li>
 * </ol>
 *
 * <h2>State layout</h2>
 *
 * <p>State is partitioned per screening and guarded by one lock per screening (see
 * {@link ScreeningState}). A cross-screening secondary index {@link #holdToScreening} gives
 * O(1) routing from a holdId to its owning screening on {@code confirm} / {@code release} —
 * the alternative would be a linear scan across every screening's state on every call.
 *
 * <h2>Concurrency model</h2>
 *
 * <ul>
 *   <li>One {@link ReentrantLock} per screening. Two users booking different screenings never
 *       contend. Two users booking the <em>same</em> screening serialise — this is the right
 *       trade-off, because the contention boundary is the seat map for that screening.</li>
 *   <li>{@link ConcurrentHashMap} for the top-level {@link #states} and {@link #holdToScreening}
 *       indexes so first-touch creation of a screening's state is itself thread-safe.</li>
 *   <li>All reads and mutations of a {@link ScreeningState}'s internal maps happen under that
 *       screening's lock, so the inner maps could in principle be plain {@link java.util.HashMap}s;
 *       they're {@link ConcurrentHashMap}s out of belt-and-braces defensiveness against future
 *       lock-free read paths.</li>
 * </ul>
 *
 * <h2>Idempotency</h2>
 *
 * <p>{@link #confirm} is idempotent on {@code holdId}. Mobile clients on flaky networks will
 * retry the confirm RPC; without idempotency a retry after a successful-but-lost response
 * would either double-charge the user or fail spuriously. We retain the {@code holdId →
 * Booking} mapping in {@code bookings} indefinitely (for the lifetime of the screening state)
 * specifically so retries can re-resolve to the same booking.
 */
public class ConcurrentSeatBookingService implements SeatBookingService {

    /**
     * Per-screening mutable state, guarded by {@link #lock}. The four maps are intentionally
     * separated rather than fused into a single richer structure — each answers a different
     * question on the hot path, and merging them would force every query to walk extra fields.
     *
     * <ul>
     *   <li>{@code held} — {@code seat → holdId}. The authority on "is this seat currently
     *       reserved by an unexpired hold?". Keyed by seat so the hold-time conflict check
     *       ({@code containsKey(seat)}) is O(1) per seat.</li>
     *
     *   <li>{@code booked} — {@code seat → bookingId}. The authority on "is this seat
     *       permanently sold?". Separate from {@code held} because a seat moves from one map
     *       to the other on confirm; collapsing them would lose the held/booked distinction
     *       which matters for diagnostics and would complicate the conflict check.</li>
     *
     *   <li>{@code holds} — {@code holdId → Hold}. The reverse index for confirm/release/sweep:
     *       given a hold, find its seats and expiry. Without this we'd scan {@code held}
     *       linearly to reconstruct a hold from its id.</li>
     *
     *   <li>{@code bookings} — {@code holdId → Booking}. Kept keyed by the originating
     *       {@code holdId} (not the bookingId) precisely to power idempotent confirm: a retry
     *       arrives with the holdId and we look up directly.</li>
     *
     *   <li>{@code lock} — single-writer mutex over the four maps for this screening.</li>
     * </ul>
     */
    private record ScreeningState(
            Map<Seat, UUID> held,
            Map<Seat, UUID> booked,
            Map<UUID, Hold> holds,
            Map<UUID, Booking> bookings,
            ReentrantLock lock) {}

    /** Per-screening state, created on first touch in {@link #stateFor}. */
    private final Map<UUID, ScreeningState> states = new ConcurrentHashMap<>();

    /**
     * Cross-screening secondary index: {@code holdId → screeningId}. Lets {@code confirm} and
     * {@code release} route to the right per-screening lock without scanning every screening's
     * {@code holds} map. This is denormalisation — the same shape as a database secondary
     * index — and like any secondary index it must be maintained in lockstep with the primary
     * (the per-screening {@code holds} map). See {@link #sweepExpired}, {@link #confirm} and
     * {@link #release} for the maintenance points.
     */
    private final Map<UUID, UUID> holdToScreening = new ConcurrentHashMap<>();

    private ScreeningState stateFor(UUID screeningId) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public Optional<Hold> hold(UUID screeningId, Set<Seat> seats, Duration ttl, Instant now) {
        // Lazy cleanup: clear any expired holds before we evaluate conflicts. Otherwise an
        // expired hold would spuriously block a new hold on the same seats.
        //
        // Conflict check is all-or-nothing: if any requested seat is taken (held or
        // booked) we fail the whole hold rather than partially reserve. Partial holds
        // would surprise callers and complicate rollback.
        //
        // Maintain the secondary index in the same critical section as the primary write.
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public Optional<Booking> confirm(UUID holdId, String customer, Instant now) {
        // Could still be a replay where hold was confirmed and the index was cleaned up.
        // Walk states to find a matching booking; if none, truly unknown.
        //
        // Idempotent confirm: if we've already minted a booking for this holdId, return
        // exactly the same Booking instance. This is the critical retry-safety property —
        // a mobile client whose confirm RPC times out and retries must not get charged
        // twice or see a different booking on the second call.
        //
        // Expired between hold and confirm — clean up and report failure. The caller
        // (typically the payment flow) is responsible for not charging the user.
        //
        // Promote: record booking, drop hold, flip each seat from held → booked.
        // All under the same lock so other threads never observe a half-promoted state.
        //
        // Note: holdToScreening is deliberately NOT removed here. Keeping it lets a
        // retry's first lookup short-circuit to the right state without the fallback
        // linear scan above.
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public boolean release(UUID holdId) {
        // Remove from the secondary index first; if it wasn't there, the hold either never
        // existed or has already been confirmed/released — either way there's nothing to do.
        //
        // remove(key, value) is the keyed-conditional remove: we only clear the seat
        // entry if it still maps to this holdId, defending against the (impossible-under-
        // -lock but cheap-to-guard) case of another writer having repurposed the seat.
        throw new UnsupportedOperationException("TODO: implement");
    }

    /**
     * Lazy cleanup of expired holds: invoked on the hot path of {@link #hold}, not by a
     * background thread.
     *
     * <p>Trade-off: simpler — no scheduler, no thread-pool lifecycle to manage, no risk of a
     * janitor thread holding the lock when a user request needs it. Cost: an expired hold
     * lingers in memory until the next operation on its screening touches the sweep. For an
     * in-memory kata that's fine; in a real system you'd reconsider if the screening could go
     * cold for long enough that memory pressure mattered.
     */
    private void sweepExpired(ScreeningState s, Instant now) {
        // Two-pass to avoid ConcurrentModificationException on the holds map while iterating.
        throw new UnsupportedOperationException("TODO: implement");
    }

    /** Single-hold cleanup used by {@link #confirm} when it discovers a hold has expired. */
    private void expire(ScreeningState s, Hold h) {
        throw new UnsupportedOperationException("TODO: implement");
    }
}
