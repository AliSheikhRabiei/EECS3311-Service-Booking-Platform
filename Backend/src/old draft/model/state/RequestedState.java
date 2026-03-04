package model.state;

import model.Booking;
import model.BookingStatus;
import payment.PaymentTransaction;

public class RequestedState implements BookingState {

    @Override
    public void confirm(Booking b) {
        // Confirm then immediately move to PENDING_PAYMENT in one step
        b.setStatus(BookingStatus.CONFIRMED);
        System.out.println("[State] Booking " + b.getBookingId() + ": REQUESTED → CONFIRMED → PENDING_PAYMENT");
        b.setStatus(BookingStatus.PENDING_PAYMENT);
        b.setState(new PendingPaymentState());
    }

    @Override
    public void reject(Booking b, String reason) {
        b.setStatus(BookingStatus.REJECTED);
        b.setState(new RejectedState());
        System.out.println("[State] Booking " + b.getBookingId() + ": REQUESTED → REJECTED. Reason: " + reason);
    }

    @Override
    public void cancel(Booking b, String reason) {
        b.setStatus(BookingStatus.CANCELLED);
        b.setState(new CancelledState());
        System.out.println("[State] Booking " + b.getBookingId() + ": REQUESTED → CANCELLED. Reason: " + reason);
    }

    @Override
    public void paymentSuccessful(Booking b, PaymentTransaction tx) {
        throw new IllegalStateException("Cannot process payment for a booking that hasn't been confirmed yet.");
    }

    @Override
    public void complete(Booking b) {
        throw new IllegalStateException("Cannot complete a booking that is still in REQUESTED state.");
    }
}
