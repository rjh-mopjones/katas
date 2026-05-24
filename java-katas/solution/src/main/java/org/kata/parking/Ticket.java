package org.kata.parking;

import java.time.Instant;
import java.util.UUID;

/**
 * Receipt issued on park, redeemed on unpark.
 *
 * <p>The ticket — not the {@link Vehicle} — is the unit of identity inside the lot:
 * a vehicle could in principle be parked twice (different sessions), but each session
 * has exactly one {@code UUID}. Carrying the {@link Spot} and entry {@link Instant} on
 * the ticket means unpark needs no secondary lookup to compute the charge.
 */
public record Ticket(UUID id, Spot spot, String plate, Instant entry) {}
