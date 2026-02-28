package model.state;

import model.Booking;
import payment.PaymentTransaction;

public interface BookingState {
    void confirm(Booking b);
    void reject(Booking b, String reason);
    void cancel(Booking b, String reason);
    void paymentSuccessful(Booking b, PaymentTransaction tx);
    void complete(Booking b);
}
