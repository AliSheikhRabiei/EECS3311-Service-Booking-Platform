package com.platform.policy;

import com.platform.domain.Booking;
import java.time.LocalDateTime;

/**
 * Cancellation policy that blocks all cancellations.
 * Named class so PolicyManager can display it cleanly (no anonymous class).
 */
public class StrictCancellationPolicy implements CancellationPolicy {
    @Override
    public boolean canCancel(Booking booking, LocalDateTime now) {
        return false;
    }

    @Override
    public double cancellationFee(Booking booking, LocalDateTime now) {
        return booking.getService().getPrice();
    }
}
