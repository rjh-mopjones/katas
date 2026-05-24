package org.kata.cinema;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Phase-one artefact of the two-phase commit: a temporary reservation of seats with a TTL.
 *
 * <p>Holds exist because real-world booking UX requires the user to <em>select</em> seats
 * before they pay. We can't book the seats outright (the user may never finish checkout) and
 * we can't leave them unreserved (a second user could grab the same seats mid-payment). A
 * hold splits the difference: seats are exclusively reserved against {@code expiresAt}, then
 * either promoted to a {@link Booking} via confirm or reclaimed on expiry / release.
 *
 * <p>The seat set is defensively copied via {@link Set#copyOf} in the compact constructor.
 * Without that, a caller retaining a reference to the original mutable set could mutate the
 * hold's seats <em>after</em> it had been registered in the service's state maps — corrupting
 * the held/booked invariants. Records advertise immutability; we have to enforce it for
 * mutable component types.
 *
 * <p>{@code expiresAt} is an absolute {@link Instant}, not a relative duration: once the hold
 * is constructed, its expiry is pinned and doesn't drift with later wall-clock reads.
 */
public record Hold(UUID id, UUID screeningId, Set<Seat> seats, Instant expiresAt) {
    public Hold {
        seats = Set.copyOf(seats);
    }

    /**
     * A hold is expired when {@code now} has reached or passed {@code expiresAt}.
     * Uses {@code !now.isBefore(expiresAt)} so the boundary instant counts as expired —
     * matches the usual "TTL elapsed" intuition.
     */
    public boolean isExpired(Instant now) {
        throw new UnsupportedOperationException("TODO: implement");
    }
}
