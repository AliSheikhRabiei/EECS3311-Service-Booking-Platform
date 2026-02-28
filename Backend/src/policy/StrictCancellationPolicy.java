package policy;

import model.Booking;
import java.time.LocalDateTime;

/** Strict policy: no cancellations allowed at any point. */
public class StrictCancellationPolicy implements CancellationPolicy {
    @Override
    public boolean canCancel(Booking b, LocalDateTime now) {
        return false;
    }

    @Override
    public double cancellationFee(Booking b, LocalDateTime now) {
        return b.getService().getPrice(); // Full price as fee
    }
}
