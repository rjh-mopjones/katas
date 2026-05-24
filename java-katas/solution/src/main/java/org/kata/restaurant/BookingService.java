package org.kata.restaurant;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingService {
    /**
     * Returns {@link Optional#empty()} when no table is available.
     *
     * <p>Failure to book is an expected outcome, not exceptional — the restaurant
     * is simply full. Exceptions for control flow are expensive (stack traces),
     * harder to compose, and hide the failure mode from the signature. {@link Optional}
     * puts "might not succeed" in the type.
     *
     * <p>If multiple failure reasons mattered (no capacity, closed, blacklisted
     * customer), switch to a sealed {@code BookingResult = Confirmed | Rejected(Reason)}.
     * For a single failure mode, Optional is the right tool.
     */
    Optional<Booking> book(int partySize, TimeSlot slot, String customer);
    boolean cancel(UUID bookingId);
    List<Booking> bookingsFor(LocalDate date);
}
