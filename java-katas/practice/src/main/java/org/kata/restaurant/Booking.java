package org.kata.restaurant;

import java.util.UUID;

public record Booking(UUID id, Table table, TimeSlot slot, int partySize, String customer) {
    public Booking {
        if (partySize <= 0)
            throw new IllegalArgumentException("partySize must be positive");
        if (partySize > table.capacity())
            throw new IllegalArgumentException("party exceeds table capacity");
    }
}
