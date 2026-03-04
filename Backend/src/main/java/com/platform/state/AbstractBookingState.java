package com.platform.state;

import com.platform.domain.Booking;
import com.platform.payment.PaymentTransaction;

/**
 * Abstract base state providing <strong>default-deny</strong> behaviour.
 *
 * <p>Every method throws {@link IllegalStateException} describing what is not
 * allowed from the current state.  Concrete states override only the transitions
 * that are valid for them, keeping each class small and focused.</p>
 */
public abstract class AbstractBookingState implements BookingState {

    /** Returns the human-readable name of this state for error messages. */
    protected String name() {
        return getClass().getSimpleName().replace("State", "").toUpperCase();
    }

    @Override
    public void confirm(Booking b) {
        throw new IllegalStateException(
                "Cannot confirm a booking that is in state: " + name());
    }

    @Override
    public void reject(Booking b, String reason) {
        throw new IllegalStateException(
                "Cannot reject a booking that is in state: " + name());
    }

    @Override
    public void cancel(Booking b, String reason) {
        throw new IllegalStateException(
                "Cannot cancel a booking that is in state: " + name());
    }

    @Override
    public void paymentSuccessful(Booking b, PaymentTransaction tx) {
        throw new IllegalStateException(
                "Cannot process payment for a booking that is in state: " + name());
    }

    @Override
    public void complete(Booking b) {
        throw new IllegalStateException(
                "Cannot complete a booking that is in state: " + name()
                + ". Booking must be PAID first.");
    }
}
