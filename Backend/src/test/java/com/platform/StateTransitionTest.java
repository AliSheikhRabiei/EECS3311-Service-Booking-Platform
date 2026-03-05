package com.platform;

import com.platform.domain.*;
import com.platform.payment.*;
import com.platform.state.*;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exhaustive state-machine tests.
 * Verifies EVERY valid transition succeeds and EVERY invalid one throws.
 */
@DisplayName("Booking State Machine Tests")
class StateTransitionTest {

    private Client     client;
    private Consultant consultant;
    private Service    service;
    private TimeSlot   slot;

    @BeforeEach
    void setUp() {
        consultant = new Consultant("C1", "Alice", "alice@test.com", "Expert");
        consultant.setRegistrationStatus(RegistrationStatus.APPROVED);
        client  = new Client("U1", "Bob", "bob@test.com");
        service = new Service("S1", "Tutoring", "Desc", 60, 100.0, consultant);
        slot    = freshSlot();
    }

    private TimeSlot freshSlot() {
        return new TimeSlot(
                LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0),
                LocalDateTime.now().plusDays(1).withHour(11).withMinute(0).withSecond(0).withNano(0));
    }

    private Booking freshBooking() {
        return new Booking("BK-" + System.nanoTime(), client, service, slot);
    }

    private PaymentTransaction successTx(Booking b) {
        return new PaymentTransaction("TX-1", b, 100.0, PaymentStatus.SUCCESS, LocalDateTime.now());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // REQUESTED STATE — valid transitions
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("REQUESTED → CONFIRMED via confirm()")
    void requestedToConfirmed() {
        Booking b = freshBooking();
        assertInstanceOf(RequestedState.class, b.getState());
        b.confirm();
        assertInstanceOf(ConfirmedState.class, b.getState());
    }

    @Test @DisplayName("REQUESTED → REJECTED via reject()")
    void requestedToRejected() {
        Booking b = freshBooking();
        b.reject("No availability");
        assertInstanceOf(RejectedState.class, b.getState());
    }

    @Test @DisplayName("REQUESTED → CANCELLED via cancel()")
    void requestedToCancelled() {
        Booking b = freshBooking();
        b.cancel("Client changed mind");
        assertInstanceOf(CancelledState.class, b.getState());
    }

    @Test @DisplayName("REQUESTED → cancel() releases the slot")
    void requestedCancelReleasesSlot() {
        slot.reserve();
        Booking b = freshBooking();
        b.cancel("Test");
        assertTrue(slot.isAvailable(), "Slot must be released after cancel from REQUESTED");
    }

    @Test @DisplayName("REQUESTED → reject() releases the slot")
    void requestedRejectReleasesSlot() {
        slot.reserve();
        Booking b = freshBooking();
        b.reject("Test");
        assertTrue(slot.isAvailable(), "Slot must be released after reject");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // REQUESTED STATE — invalid transitions
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("REQUESTED: paymentSuccessful() throws")
    void requestedPaymentSuccessfulThrows() {
        Booking b = freshBooking();
        assertThrows(IllegalStateException.class, () -> b.paymentSuccessful(successTx(b)));
    }

    @Test @DisplayName("REQUESTED: complete() throws")
    void requestedCompleteThrows() {
        Booking b = freshBooking();
        assertThrows(IllegalStateException.class, b::complete);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CONFIRMED STATE — valid transitions
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("CONFIRMED → CANCELLED via cancel()")
    void confirmedToCancelled() {
        Booking b = freshBooking();
        b.confirm();
        b.cancel("Oops");
        assertInstanceOf(CancelledState.class, b.getState());
    }

    @Test @DisplayName("CONFIRMED → PENDING_PAYMENT via setState (as PaymentService does it)")
    void confirmedToPendingPaymentViaSetState() {
        Booking b = freshBooking();
        b.confirm();
        b.setState(new PendingPaymentState());
        assertInstanceOf(PendingPaymentState.class, b.getState());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CONFIRMED STATE — invalid transitions
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("CONFIRMED: confirm() again throws")
    void confirmedConfirmAgainThrows() {
        Booking b = freshBooking();
        b.confirm();
        assertThrows(IllegalStateException.class, b::confirm);
    }

    @Test @DisplayName("CONFIRMED: reject() throws")
    void confirmedRejectThrows() {
        Booking b = freshBooking();
        b.confirm();
        assertThrows(IllegalStateException.class, () -> b.reject("Late"));
    }

    @Test @DisplayName("CONFIRMED: paymentSuccessful() throws")
    void confirmedPaymentSuccessfulThrows() {
        Booking b = freshBooking();
        b.confirm();
        assertThrows(IllegalStateException.class, () -> b.paymentSuccessful(successTx(b)));
    }

    @Test @DisplayName("CONFIRMED: complete() throws")
    void confirmedCompleteThrows() {
        Booking b = freshBooking();
        b.confirm();
        assertThrows(IllegalStateException.class, b::complete);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PENDING_PAYMENT STATE — valid transitions
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("PENDING_PAYMENT → PAID via paymentSuccessful()")
    void pendingPaymentToPaid() {
        Booking b = freshBooking();
        b.confirm();
        b.setState(new PendingPaymentState());
        b.paymentSuccessful(successTx(b));
        assertInstanceOf(PaidState.class, b.getState());
    }

    @Test @DisplayName("PENDING_PAYMENT → CANCELLED via cancel()")
    void pendingPaymentToCancelled() {
        Booking b = freshBooking();
        b.confirm();
        b.setState(new PendingPaymentState());
        b.cancel("Changed mind");
        assertInstanceOf(CancelledState.class, b.getState());
    }

    @Test @DisplayName("PENDING_PAYMENT → cancel() releases the slot")
    void pendingPaymentCancelReleasesSlot() {
        slot.reserve();
        Booking b = freshBooking();
        b.confirm();
        b.setState(new PendingPaymentState());
        b.cancel("Test");
        assertTrue(slot.isAvailable());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PENDING_PAYMENT — invalid transitions
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("PENDING_PAYMENT: confirm() throws")
    void pendingPaymentConfirmThrows() {
        Booking b = freshBooking();
        b.confirm();
        b.setState(new PendingPaymentState());
        assertThrows(IllegalStateException.class, b::confirm);
    }

    @Test @DisplayName("PENDING_PAYMENT: reject() throws")
    void pendingPaymentRejectThrows() {
        Booking b = freshBooking();
        b.confirm();
        b.setState(new PendingPaymentState());
        assertThrows(IllegalStateException.class, () -> b.reject("Late"));
    }

    @Test @DisplayName("PENDING_PAYMENT: complete() throws")
    void pendingPaymentCompleteThrows() {
        Booking b = freshBooking();
        b.confirm();
        b.setState(new PendingPaymentState());
        assertThrows(IllegalStateException.class, b::complete);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PAID STATE — valid transitions
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("PAID → COMPLETED via complete()")
    void paidToCompleted() {
        Booking b = freshBooking();
        b.confirm();
        b.setState(new PendingPaymentState());
        b.paymentSuccessful(successTx(b));
        b.complete();
        assertInstanceOf(CompletedState.class, b.getState());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PAID STATE — invalid transitions
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("PAID: confirm() throws")
    void paidConfirmThrows() {
        Booking b = paidBooking();
        assertThrows(IllegalStateException.class, b::confirm);
    }

    @Test @DisplayName("PAID: reject() throws")
    void paidRejectThrows() {
        Booking b = paidBooking();
        assertThrows(IllegalStateException.class, () -> b.reject("Too late"));
    }

    @Test @DisplayName("PAID: cancel() throws (service layer handles PAID cancels via refund)")
    void paidCancelThrows() {
        Booking b = paidBooking();
        // PAID has no cancel() override, so it throws
        assertThrows(IllegalStateException.class, () -> b.cancel("Reason"));
    }

    @Test @DisplayName("PAID: paymentSuccessful() again throws")
    void paidPaymentSuccessfulAgainThrows() {
        Booking b = paidBooking();
        assertThrows(IllegalStateException.class, () -> b.paymentSuccessful(successTx(b)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TERMINAL STATES — nothing allowed
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("REJECTED: all transitions throw")
    void rejectedAllTransitionsThrow() {
        Booking b = freshBooking();
        b.reject("Reason");
        assertThrows(IllegalStateException.class, b::confirm);
        assertThrows(IllegalStateException.class, () -> b.reject("Again"));
        assertThrows(IllegalStateException.class, () -> b.cancel("Cancel"));
        assertThrows(IllegalStateException.class, () -> b.paymentSuccessful(successTx(b)));
        assertThrows(IllegalStateException.class, b::complete);
    }

    @Test @DisplayName("CANCELLED: all transitions throw")
    void cancelledAllTransitionsThrow() {
        Booking b = freshBooking();
        b.cancel("Reason");
        assertThrows(IllegalStateException.class, b::confirm);
        assertThrows(IllegalStateException.class, () -> b.reject("Again"));
        assertThrows(IllegalStateException.class, () -> b.cancel("Again"));
        assertThrows(IllegalStateException.class, () -> b.paymentSuccessful(successTx(b)));
        assertThrows(IllegalStateException.class, b::complete);
    }

    @Test @DisplayName("COMPLETED: all transitions throw")
    void completedAllTransitionsThrow() {
        Booking b = paidBooking();
        b.complete();
        assertThrows(IllegalStateException.class, b::confirm);
        assertThrows(IllegalStateException.class, () -> b.reject("Again"));
        assertThrows(IllegalStateException.class, () -> b.cancel("Cancel"));
        assertThrows(IllegalStateException.class, () -> b.paymentSuccessful(successTx(b)));
        assertThrows(IllegalStateException.class, b::complete);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // AbstractBookingState — error messages
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("IllegalStateException message describes the current state")
    void illegalStateExceptionMessageIsDescriptive() {
        Booking b = freshBooking();
        IllegalStateException ex = assertThrows(IllegalStateException.class, b::complete);
        String msg = ex.getMessage().toLowerCase();
        // Message should reference the state
        assertTrue(msg.contains("requested") || msg.contains("state"),
                "Message should mention the current state. Got: " + ex.getMessage());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helper
    // ══════════════════════════════════════════════════════════════════════════

    private Booking paidBooking() {
        Booking b = freshBooking();
        b.confirm();
        b.setState(new PendingPaymentState());
        b.paymentSuccessful(successTx(b));
        return b;
    }
}
