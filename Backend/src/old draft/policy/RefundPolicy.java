package policy;

import payment.PaymentTransaction;
import java.time.LocalDateTime;

public interface RefundPolicy {
    double calculateRefund(PaymentTransaction tx, LocalDateTime now);
}
