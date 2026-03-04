package com.platform.state;

import com.platform.domain.Booking;
import com.platform.payment.PaymentTransaction;

/**
 * Payment has been initiated but not yet confirmed.
 * Valid transitions: paymentSuccessful → PAID, cancel → CANCELLED.
 *
 * Note: The slot is already reserved from createBooking(); no re-reserve is needed here.
 * On cancel, the slot is released so another booking can use it.
 */
public class PendingPaymentState extends AbstractBookingState {

    @Override
    public void paymentSuccessful(Booking b, PaymentTransaction tx) {
        System.out.println("[State] " + b.getBookingId()
                + ": PENDING_PAYMENT → PAID  (tx=" + tx.getTransactionId() + ")");
        // Slot stays reserved (already done in createBooking).
        b.setState(new PaidState());
    }

    @Override
    public void cancel(Booking b, String reason) {
        System.out.println("[State] " + b.getBookingId()
                + ": PENDING_PAYMENT → CANCELLED (reason: " + reason + ")");
        b.getSlot().release();   // Return slot to the available pool
        b.setState(new CancelledState());
    }
}
