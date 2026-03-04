package model.state;

import model.Booking;
import payment.PaymentTransaction;

public class RejectedState implements BookingState {
    @Override public void confirm(Booking b)                          { throw new IllegalStateException("Booking is REJECTED."); }
    @Override public void reject(Booking b, String reason)           { throw new IllegalStateException("Booking is already REJECTED."); }
    @Override public void cancel(Booking b, String reason)           { throw new IllegalStateException("Booking is REJECTED."); }
    @Override public void paymentSuccessful(Booking b, PaymentTransaction tx) { throw new IllegalStateException("Booking is REJECTED."); }
    @Override public void complete(Booking b)                        { throw new IllegalStateException("Booking is REJECTED."); }
}
