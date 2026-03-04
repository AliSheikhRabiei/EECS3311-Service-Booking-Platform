package com.platform.state;

import com.platform.domain.Booking;
import com.platform.payment.PaymentTransaction;

/**
 * State pattern interface: defines the lifecycle operations that a Booking can
 * perform.  Each concrete state permits or denies these operations.
 */
public interface BookingState {
    void confirm(Booking b);
    void reject(Booking b, String reason);
    void cancel(Booking b, String reason);
    void paymentSuccessful(Booking b, PaymentTransaction tx);
    void complete(Booking b);
}
