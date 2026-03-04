package com.platform.policy;

import com.platform.payment.PaymentTransaction;
import java.time.LocalDateTime;

/**
 * Default refund policy: 80% of the original transaction amount is refunded.
 * TODO: implement time-based tiers (e.g., full refund if > 48h before slot).
 */
public class DefaultRefundPolicy implements RefundPolicy {

    @Override
    public double calculateRefund(PaymentTransaction tx, LocalDateTime now) {
        if (tx == null) return 0.0;
        return tx.getAmount() * 0.80;
    }
}
