package com.platform.policy;

import com.platform.domain.Booking;
import java.time.LocalDateTime;

/** Strategy: encapsulates cancellation rules configurable by the Admin (UC12). */
public interface CancellationPolicy {
    boolean canCancel(Booking b, LocalDateTime now);
    double  cancellationFee(Booking b, LocalDateTime now);
}
