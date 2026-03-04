package com.platform.state;

import com.platform.domain.Booking;
import com.platform.payment.PaymentTransaction;

/**
 * Payment has been successfully processed.
 * The only valid transition is complete → COMPLETED.
 * Cancellation of a paid booking is handled at the service layer (refund + force state).
 */
public class PaidState extends AbstractBookingState {

    @Override
    public void complete(Booking b) {
        System.out.println("[State] " + b.getBookingId() + ": PAID → COMPLETED");
        b.setState(new CompletedState());
    }
}
