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

public class ConcurrentSeatBookingService implements SeatBookingService {

    @Override
    public Optional<Hold> hold(UUID screeningId, Set<Seat> seats, Duration ttl, Instant now) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Booking> confirm(UUID holdId, String customer, Instant now) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean release(UUID holdId) {
        throw new UnsupportedOperationException();
    }
}
