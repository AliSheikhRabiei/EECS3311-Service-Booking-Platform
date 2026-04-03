package com.platform.policy;

import com.platform.payment.PaymentTransaction;
import java.time.LocalDateTime;

/**
 * Refund policy that issues no refund on cancellation.
 * Named class so PolicyManager can display it cleanly (no lambda).
 */
public class NoRefundPolicy implements RefundPolicy {
    @Override
    public double calculateRefund(PaymentTransaction transaction, LocalDateTime now) {
        return 0.0;
    }
}
