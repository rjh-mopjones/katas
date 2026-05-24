package org.kata.restaurant;

import java.time.Duration;
import java.time.LocalDateTime;

public record TimeSlot(LocalDateTime startTime, Duration duration) {
    public TimeSlot {
        if (startTime == null || duration == null)
            throw new IllegalArgumentException("startTime and duration required");
        if (duration.isZero() || duration.isNegative())
            throw new IllegalArgumentException("duration must be positive");
    }

    public boolean overlaps(TimeSlot other) {
        throw new UnsupportedOperationException();
    }

    LocalDateTime end() {
        return startTime.plus(duration);
    }
}
