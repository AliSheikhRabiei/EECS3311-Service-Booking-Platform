package com.platform.state;

import com.platform.domain.Booking;
import com.platform.payment.PaymentTransaction;

/**
 * Consultant has accepted the booking.
 * From here, PaymentService.processPayment() transitions to PENDING_PAYMENT.
 * The only other valid action from here is cancel → CANCELLED.
 */
public class ConfirmedState extends AbstractBookingState {

    @Override
    public void cancel(Booking b, String reason) {
        System.out.println("[State] " + b.getBookingId() + ": CONFIRMED → CANCELLED (reason: " + reason + ")");
        b.getSlot().release();
        b.setState(new CancelledState());
    }
}
