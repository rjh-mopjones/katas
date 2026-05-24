package org.kata.parking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class ConcurrentParkingLot implements ParkingLot {

    public ConcurrentParkingLot(List<Spot> spots) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Ticket> park(Vehicle vehicle, Instant entry) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<BigDecimal> unpark(UUID ticketId, Instant exit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long available(VehicleType type) {
        throw new UnsupportedOperationException();
    }
}
