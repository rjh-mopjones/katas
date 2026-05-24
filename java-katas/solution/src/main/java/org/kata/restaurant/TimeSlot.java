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
        return startTime.isBefore(other.end()) && other.startTime.isBefore(end());
    }

    // Package-private: derived value. Callers should ask overlaps() rather than
    // computing against end-times directly. Promote to public only if a legitimate
    // out-of-package caller needs it.
    LocalDateTime end() {
        return startTime.plus(duration);
    }
}
