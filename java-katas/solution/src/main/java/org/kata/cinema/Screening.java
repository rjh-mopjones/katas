package org.kata.cinema;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A single scheduled showing of a film in a hall of {@code rows x cols} seats.
 *
 * <p>Identity is the {@link UUID} — two showings of the same film at the same time are still
 * distinct screenings. The hall geometry ({@code rows}, {@code cols}) is captured here because
 * it bounds the legal seat coordinates for this screening; the booking service trusts the
 * caller to supply seats within these bounds (validation could be added but is out of scope
 * for the kata's two-phase booking focus).
 *
 * <p>{@link LocalDateTime} (rather than {@link java.time.Instant}) is deliberate for the
 * showing time: a screening is wall-clock-local to its cinema, not a global instant. The
 * booking service's TTL logic uses {@code Instant} separately — those are different concerns.
 */
public record Screening(UUID id, String film, LocalDateTime when, int rows, int cols) {
    public Screening {
        if (rows < 1 || cols < 1) throw new IllegalArgumentException("hall must have at least one seat");
    }
}
