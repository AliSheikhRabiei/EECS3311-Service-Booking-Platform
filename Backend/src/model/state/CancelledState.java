package model.state;

import model.Booking;
import payment.PaymentTransaction;

public class CancelledState implements BookingState {
    @Override public void confirm(Booking b)                          { throw new IllegalStateException("Booking is CANCELLED."); }
    @Override public void reject(Booking b, String reason)           { throw new IllegalStateException("Booking is CANCELLED."); }
    @Override public void cancel(Booking b, String reason)           { throw new IllegalStateException("Booking is already CANCELLED."); }
    @Override public void paymentSuccessful(Booking b, PaymentTransaction tx) { throw new IllegalStateException("Booking is CANCELLED."); }
    @Override public void complete(Booking b)                        { throw new IllegalStateException("Booking is CANCELLED."); }
}
