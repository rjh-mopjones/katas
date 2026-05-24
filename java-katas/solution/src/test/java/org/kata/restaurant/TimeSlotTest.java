package org.kata.restaurant;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeSlotTest {

    @Test
    void slots_with_gap_dont_overlap() {
        TimeSlot a = new TimeSlot(LocalDateTime.parse("2026-05-10T19:00"), Duration.ofHours(2));
        TimeSlot b = new TimeSlot(LocalDateTime.parse("2026-05-10T22:00"), Duration.ofHours(2));
        assertFalse(a.overlaps(b));
    }

    @Test
    void slots_that_touch_dont_overlap() {
        TimeSlot a = new TimeSlot(LocalDateTime.parse("2026-05-10T19:00"), Duration.ofHours(3));
        TimeSlot b = new TimeSlot(LocalDateTime.parse("2026-05-10T22:00"), Duration.ofHours(2));
        assertFalse(a.overlaps(b));
    }

    @Test
    void slots_overlap() {
        TimeSlot a = new TimeSlot(LocalDateTime.parse("2026-05-10T19:00"), Duration.ofHours(4));
        TimeSlot b = new TimeSlot(LocalDateTime.parse("2026-05-10T22:00"), Duration.ofHours(2));
        assertTrue(a.overlaps(b));
    }

    @Test
    void slots_contained_in_another_overlaps() {
        TimeSlot a = new TimeSlot(LocalDateTime.parse("2026-05-10T19:00"), Duration.ofHours(4));
        TimeSlot b = new TimeSlot(LocalDateTime.parse("2026-05-10T20:00"), Duration.ofHours(1));
        assertTrue(a.overlaps(b));
        assertTrue(b.overlaps(a));
    }

}
