package policy;

import model.Booking;
import java.time.LocalDateTime;

public interface CancellationPolicy {
    boolean canCancel(Booking b, LocalDateTime now);
    double  cancellationFee(Booking b, LocalDateTime now);
}
