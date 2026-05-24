package org.kata.restaurant;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryBookingService implements BookingService {

    public InMemoryBookingService(List<Table> tables) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Booking> book(int partySize, TimeSlot slot, String customer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean cancel(UUID bookingId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Booking> bookingsFor(LocalDate date) {
        throw new UnsupportedOperationException();
    }
}
