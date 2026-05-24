package org.kata.restaurant;

public record Table(int id, int capacity) {
    public Table {
        if (capacity <= 0)
            throw new IllegalArgumentException("capacity must be positive");
    }
}
