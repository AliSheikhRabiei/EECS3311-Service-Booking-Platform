package model.state;

import model.Booking;
import model.BookingStatus;
import payment.PaymentTransaction;

public class PendingPaymentState implements BookingState {

    @Override
    public void confirm(Booking b) {
        throw new IllegalStateException("Booking is already confirmed and awaiting payment.");
    }

    @Override
    public void reject(Booking b, String reason) {
        throw new IllegalStateException("Cannot reject a booking that is awaiting payment.");
    }

    @Override
    public void cancel(Booking b, String reason) {
        b.setStatus(BookingStatus.CANCELLED);
        b.setState(new CancelledState());
        b.getSlot().release();
        System.out.println("[State] Booking " + b.getBookingId() + ": PENDING_PAYMENT → CANCELLED. Reason: " + reason);
    }

    @Override
    public void paymentSuccessful(Booking b, PaymentTransaction tx) {
        b.setStatus(BookingStatus.PAID);
        b.setState(new PaidState());
        b.getSlot().reserve();
        System.out.println("[State] Booking " + b.getBookingId() + ": PENDING_PAYMENT → PAID. Transaction: " + tx.getTransactionId());
    }

    @Override
    public void complete(Booking b) {
        throw new IllegalStateException("Cannot complete a booking that hasn't been paid yet.");
    }
}
