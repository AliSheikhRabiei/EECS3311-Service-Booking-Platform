package policy;

import payment.PaymentTransaction;

import java.time.LocalDateTime;

/** 80% refund on successful transactions. */
public class DefaultRefundPolicy implements RefundPolicy {
    @Override
    public double calculateRefund(PaymentTransaction tx, LocalDateTime now) {
        return tx.getAmount() * 0.8;
    }
}
