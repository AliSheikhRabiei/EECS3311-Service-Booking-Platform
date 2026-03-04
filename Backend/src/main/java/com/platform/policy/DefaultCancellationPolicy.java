package com.platform.policy;

import com.platform.domain.Booking;
import com.platform.domain.Service;
import com.platform.payment.PaymentTransaction;
import com.platform.state.CancelledState;
import com.platform.state.CompletedState;
import com.platform.state.PaidState;
import com.platform.state.RejectedState;

import java.time.LocalDateTime;

/**
 * Default cancellation policy:
 * - Allows cancellation unless the booking is PAID, COMPLETED, REJECTED or CANCELLED.
 * - No cancellation fee.
 */
public class DefaultCancellationPolicy implements CancellationPolicy {

    @Override
    public boolean canCancel(Booking b, LocalDateTime now) {
        // Cannot cancel terminal states (except PAID which is handled specially in service)
        return !(b.getState() instanceof RejectedState
              || b.getState() instanceof CancelledState
              || b.getState() instanceof CompletedState);
    }

    @Override
    public double cancellationFee(Booking b, LocalDateTime now) {
        // TODO: implement time-based fee (e.g., 10% if < 24h before slot)
        return 0.0;
    }
}
