package com.platform.policy;

import com.platform.payment.PaymentTransaction;
import java.time.LocalDateTime;

/** Strategy: calculates the refund amount for a cancelled/refunded payment. */
public interface RefundPolicy {
    double calculateRefund(PaymentTransaction tx, LocalDateTime now);
}
