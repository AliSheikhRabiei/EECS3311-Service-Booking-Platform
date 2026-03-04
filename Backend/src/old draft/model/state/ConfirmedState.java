package model.state;

import model.Booking;
import model.BookingStatus;
import payment.PaymentTransaction;

public class ConfirmedState implements BookingState {

    @Override
    public void confirm(Booking b) {
        // Already confirmed — transition to pending payment
        b.setStatus(BookingStatus.PENDING_PAYMENT);
        b.setState(new PendingPaymentState());
        System.out.println("[State] Booking " + b.getBookingId() + ": CONFIRMED → PENDING_PAYMENT");
    }

    @Override
    public void reject(Booking b, String reason) {
        throw new IllegalStateException("Cannot reject a booking that is already CONFIRMED.");
    }

    @Override
    public void cancel(Booking b, String reason) {
        b.setStatus(BookingStatus.CANCELLED);
        b.setState(new CancelledState());
        System.out.println("[State] Booking " + b.getBookingId() + ": CONFIRMED → CANCELLED. Reason: " + reason);
    }

    @Override
    public void paymentSuccessful(Booking b, PaymentTransaction tx) {
        throw new IllegalStateException("Booking must be in PENDING_PAYMENT state to process payment.");
    }

    @Override
    public void complete(Booking b) {
        throw new IllegalStateException("Cannot complete a booking that hasn't been paid yet.");
    }
}
