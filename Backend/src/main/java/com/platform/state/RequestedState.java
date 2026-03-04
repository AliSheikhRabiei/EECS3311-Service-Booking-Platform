package com.platform.state;

import com.platform.domain.Booking;
import com.platform.payment.PaymentTransaction;

/**
 * Initial state: booking has been submitted by the client but not yet decided.
 * Valid transitions: confirm → CONFIRMED, reject → REJECTED, cancel → CANCELLED.
 */
public class RequestedState extends AbstractBookingState {

    @Override
    public void confirm(Booking b) {
        System.out.println("[State] " + b.getBookingId() + ": REQUESTED → CONFIRMED");
        b.setState(new ConfirmedState());
    }

    @Override
    public void reject(Booking b, String reason) {
        System.out.println("[State] " + b.getBookingId() + ": REQUESTED → REJECTED (reason: " + reason + ")");
        b.getSlot().release();
        b.setState(new RejectedState());
    }

    @Override
    public void cancel(Booking b, String reason) {
        System.out.println("[State] " + b.getBookingId() + ": REQUESTED → CANCELLED (reason: " + reason + ")");
        b.getSlot().release();
        b.setState(new CancelledState());
    }
}
