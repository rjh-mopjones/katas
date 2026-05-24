package org.kata.parking;

/**
 * The closed set of vehicles the lot knows how to price and place.
 *
 * <p>Modelled as an enum because the domain is finite and switch-exhaustive: adding a new
 * variant deliberately breaks every {@code switch} and {@code fits} check, surfacing the
 * placement decision rather than letting a stringly-typed value drift through the system.
 */
public enum VehicleType { MOTORCYCLE, CAR, EV, TRUCK }
