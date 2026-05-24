package org.kata.restaurant;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryBookingServiceTest {

    private static final TimeSlot SEVEN_PM = slot("2026-05-10T19:00", 2);
    private static final TimeSlot NINE_PM  = slot("2026-05-10T21:00", 2);
    private static final TimeSlot EIGHT_PM = slot("2026-05-10T20:00", 2);  // overlaps SEVEN_PM

    @Test
    void books_an_available_table_for_a_fitting_party() {
        var service = new InMemoryBookingService(List.of(new Table(1, 4)));

        Optional<Booking> result = service.book(2, SEVEN_PM, "Rory");

        assertTrue(result.isPresent());
        assertEquals(1, result.get().table().id());
        assertEquals("Rory", result.get().customer());
    }

    @Test
    void rejects_when_no_table_fits_party_size() {
        var service = new InMemoryBookingService(List.of(new Table(1, 2)));

        var result = service.book(4, SEVEN_PM, "Rory");

        assertTrue(result.isEmpty());
    }

    @Test
    void rejects_when_only_fitting_table_is_taken_at_overlapping_time() {
        var service = new InMemoryBookingService(List.of(new Table(1, 4)));
        service.book(2, SEVEN_PM, "Alice");

        var result = service.book(2, EIGHT_PM, "Bob");

        assertTrue(result.isEmpty());
    }

    @Test
    void picks_alternative_table_when_first_is_busy() {
        var service = new InMemoryBookingService(List.of(new Table(1, 4), new Table(2, 4)));
        service.book(2, SEVEN_PM, "Alice");

        var result = service.book(2, EIGHT_PM, "Bob");

        assertTrue(result.isPresent());
        assertEquals(2, result.get().table().id());
    }

    @Test
    void same_table_can_be_booked_for_non_overlapping_slots() {
        var service = new InMemoryBookingService(List.of(new Table(1, 4)));
        service.book(2, SEVEN_PM, "Alice");

        var result = service.book(2, NINE_PM, "Bob");

        assertTrue(result.isPresent());
        assertEquals(1, result.get().table().id());
    }

    @Test
    void best_fit_picks_smallest_sufficient_table() {
        var service = new InMemoryBookingService(List.of(new Table(1, 8), new Table(2, 4)));

        var result = service.book(2, SEVEN_PM, "Rory");

        assertEquals(2, result.get().table().id(), "should pick the 4-seater, not the 8-seater");
    }

    @Test
    void cancel_frees_the_table() {
        var service = new InMemoryBookingService(List.of(new Table(1, 4)));
        var first = service.book(2, SEVEN_PM, "Alice").orElseThrow();

        assertTrue(service.cancel(first.id()));

        var second = service.book(2, SEVEN_PM, "Bob");
        assertTrue(second.isPresent());
    }

    @Test
    void cancel_returns_false_for_unknown_booking() {
        var service = new InMemoryBookingService(List.of(new Table(1, 4)));

        assertFalse(service.cancel(UUID.randomUUID()));
    }

    @Test
    void bookings_for_returns_only_that_dates_bookings() {
        var service = new InMemoryBookingService(List.of(new Table(1, 4), new Table(2, 4)));
        service.book(2, slot("2026-05-10T19:00", 2), "Alice");
        service.book(2, slot("2026-05-11T19:00", 2), "Bob");

        var may10 = service.bookingsFor(LocalDate.parse("2026-05-10"));

        assertEquals(1, may10.size());
        assertEquals("Alice", may10.get(0).customer());
    }

    private static TimeSlot slot(String start, int hours) {
        return new TimeSlot(LocalDateTime.parse(start), Duration.ofHours(hours));
    }
}
