package org.kata.cinema;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Two-phase seat booking contract: {@code hold} → {@code confirm}, with manual {@code release}
 * and TTL-driven expiry as escape hatches.
 *
 * <p>Design notes worth understanding before reading implementations:
 *
 * <ul>
 *   <li><b>Two-phase commit.</b> Booking a seat for real money is not an atomic act from the
 *       user's perspective — they pick seats, then pay. This interface mirrors that: phase one
 *       ({@code hold}) takes seats off the market briefly; phase two ({@code confirm}) makes
 *       them permanent. Abandonment is handled by TTL on the hold, not by a callback.</li>
 *
 *   <li><b>Caller-supplied {@code now}.</b> Every time-sensitive method takes an {@link Instant}
 *       rather than reading the system clock internally. This is a deliberate testability
 *       choice — tests can pin "now" to specific instants and exercise expiry boundaries
 *       without {@link Thread#sleep} or clock stubs. Production callers simply pass
 *       {@code Instant.now()}.</li>
 *
 *   <li><b>{@link Optional} returns instead of exceptions.</b> "Seat already taken" and
 *       "hold expired" are expected outcomes in a contended booking flow, not exceptional
 *       conditions. Forcing callers to catch checked exceptions for the happy unhappy path
 *       would clutter call sites.</li>
 * </ul>
 */
public interface SeatBookingService {
    /**
     * Reserve a set of seats for a screening for {@code ttl}.
     * Returns empty if any seat is already held (and not expired) or already booked.
     */
    Optional<Hold> hold(UUID screeningId, Set<Seat> seats, Duration ttl, Instant now);

    /**
     * Convert a non-expired hold into a confirmed booking.
     * Idempotent: re-confirming the same holdId returns the same booking.
     */
    Optional<Booking> confirm(UUID holdId, String customer, Instant now);

    /** Manually release a hold (user abandons selection). */
    boolean release(UUID holdId);
}
