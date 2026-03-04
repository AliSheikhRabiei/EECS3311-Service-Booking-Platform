package model.state;

import model.Booking;
import model.BookingStatus;
import payment.PaymentTransaction;

public class PaidState implements BookingState {

    @Override
    public void confirm(Booking b) {
        throw new IllegalStateException("Booking is already paid.");
    }

    @Override
    public void reject(Booking b, String reason) {
        throw new IllegalStateException("Cannot reject a booking that has been paid.");
    }

    @Override
    public void cancel(Booking b, String reason) {
        throw new IllegalStateException("Cannot cancel a paid booking directly. Initiate a refund instead.");
    }

    @Override
    public void paymentSuccessful(Booking b, PaymentTransaction tx) {
        throw new IllegalStateException("Booking is already paid.");
    }

    @Override
    public void complete(Booking b) {
        b.setStatus(BookingStatus.COMPLETED);
        b.setState(new CompletedState());
        System.out.println("[State] Booking " + b.getBookingId() + ": PAID → COMPLETED");
    }
}
