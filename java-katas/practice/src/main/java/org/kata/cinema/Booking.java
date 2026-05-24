package org.kata.cinema;

import java.util.Set;
import java.util.UUID;

/**
 * Phase-two artefact of the two-phase commit: a confirmed, paid reservation.
 *
 * <p>Where a {@link Hold} is temporary and TTL-bounded, a {@code Booking} is durable for the
 * lifetime of the screening — there's no expiry field because, once confirmed, the seats are
 * the customer's. A booking always originates from a hold, so the seats here mirror the seats
 * that were held; the service guarantees the hold has not expired at confirm time.
 *
 * <p>As with {@link Hold}, the seat set is defensively copied so the record stays genuinely
 * immutable even if the caller retains a reference to the original set. The {@code customer}
 * is validated non-blank because an anonymous booking is a programming error — payment flows
 * always attach an identity.
 */
public record Booking(UUID id, UUID screeningId, Set<Seat> seats, String customer) {
    public Booking {
        seats = Set.copyOf(seats);
        if (customer == null || customer.isBlank()) throw new IllegalArgumentException("customer required");
    }
}
