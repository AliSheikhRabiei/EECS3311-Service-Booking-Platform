package com.platform.policy;

import com.platform.payment.PaymentTransaction;
import java.time.LocalDateTime;

/**
 * Full refund policy: 100% of the original transaction amount is returned.
 * Admin can activate this via UC12 when a lenient refund stance is needed.
 */
public class FullRefundPolicy implements RefundPolicy {

    @Override
    public double calculateRefund(PaymentTransaction tx, LocalDateTime now) {
        if (tx == null) return 0.0;
        return tx.getAmount();
    }
}
