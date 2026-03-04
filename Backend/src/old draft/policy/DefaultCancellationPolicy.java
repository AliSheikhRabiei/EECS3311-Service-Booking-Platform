package policy;

import model.Booking;
import model.BookingStatus;

import java.time.LocalDateTime;

/** Allows cancellation unless booking is PAID, COMPLETED, REJECTED, or already CANCELLED. No fee. */
public class DefaultCancellationPolicy implements CancellationPolicy {
    @Override
    public boolean canCancel(Booking b, LocalDateTime now) {
        BookingStatus s = b.getStatus();
        return s == BookingStatus.REQUESTED
            || s == BookingStatus.CONFIRMED
            || s == BookingStatus.PENDING_PAYMENT;
    }

    @Override
    public double cancellationFee(Booking b, LocalDateTime now) {
        return 0.0;
    }
}
