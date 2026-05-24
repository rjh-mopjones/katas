package org.kata.parking;

/**
 * Immutable value object identifying a vehicle by plate and type.
 *
 * <p>Records give us value equality, a canonical constructor, and an unforgeable contract
 * with no setters — exactly what we want for a key that flows through tickets and concurrent
 * maps. Validation lives in the compact constructor so no {@code Vehicle} can exist in an
 * invalid state; callers never need defensive null checks downstream.
 */
public record Vehicle(String plate, VehicleType type) {
    public Vehicle {
        if (plate == null || plate.isBlank()) throw new IllegalArgumentException("plate required");
        if (type == null) throw new IllegalArgumentException("type required");
    }
}
