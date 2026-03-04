package com.platform;

import com.platform.domain.*;
import com.platform.notify.NotificationService;
import com.platform.payment.*;
import com.platform.policy.PolicyManager;
import com.platform.repository.BookingRepository;
import com.platform.service.*;
import com.platform.state.*;

import org.junit.jupiter.api.*;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests covering the critical booking lifecycle rules.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BookingStateTest {

    private Client     client;
    private Consultant consultant;
    private Service    service;
    private TimeSlot   slot;
    private Booking    booking;

    private PolicyManager       policyManager;
    private NotificationService notificationService;
    private PaymentService      paymentService;
    private BookingService      bookingService;

    @BeforeEach
    void setUp() {
        PolicyManager.resetInstance();
        policyManager       = PolicyManager.getInstance();
        notificationService = new NotificationService();
        paymentService      = new PaymentService(policyManager, notificationService);

        BookingRepository   repo  = new BookingRepository();
        AvailabilityService avail = new AvailabilityService();

        bookingService = new BookingService(
                repo, avail, notificationService, paymentService, policyManager);

        consultant = new Consultant("C1", "Test Consultant", "c@test.com", "Expert");
        consultant.setRegistrationStatus(RegistrationStatus.APPROVED);

        service = new Service("S1", "Test Service", "Description", 60, 100.0, consultant);

        slot = new TimeSlot(
                LocalDateTime.now().plusDays(1).withHour(10).withMinute(0),
                LocalDateTime.now().plusDays(1).withHour(11).withMinute(0));

        avail.addTimeSlot(consultant, slot);
        client  = new Client("U1", "Test Client", "u@test.com");
        booking = bookingService.createBooking(client, service, slot);
    }

    // ── Test 1 ────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("UC10: Booking cannot be completed unless PAID")
    void bookingCannotCompleteUnlessPaid() {
        // REQUESTED → complete() must throw
        assertThrows(IllegalStateException.class, booking::complete,
                "complete() on REQUESTED booking must throw");

        // CONFIRMED → complete() must throw
        booking.confirm();
        assertTrue(booking.getState() instanceof ConfirmedState);
        assertThrows(IllegalStateException.class, booking::complete,
                "complete() on CONFIRMED booking must throw");

        // PENDING_PAYMENT → complete() must throw
        booking.setState(new PendingPaymentState());
        assertThrows(IllegalStateException.class, booking::complete,
                "complete() on PENDING_PAYMENT booking must throw");

        // PAID → complete() must succeed
        PaymentTransaction tx = new PaymentTransaction(
                "TX-1", booking, 100.0, PaymentStatus.SUCCESS, LocalDateTime.now());
        booking.paymentSuccessful(tx);
        assertTrue(booking.getState() instanceof PaidState);
        assertDoesNotThrow(booking::complete,
                "complete() on PAID booking must succeed");
        assertTrue(booking.getState() instanceof CompletedState);
    }

    // ── Test 2 ────────────────────────────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("UC3: Cancelling a PAID booking creates a REFUNDED transaction")
    void cancelPaidBookingCreatesRefundTransaction() {
        // Force to PENDING_PAYMENT then PAID
        booking.confirm();
        booking.setState(new PendingPaymentState());

        PaymentTransaction originalTx = new PaymentTransaction(
                "TX-ORIG", booking, 100.0, PaymentStatus.SUCCESS, LocalDateTime.now());
        paymentService.storeTransaction(originalTx);
        booking.paymentSuccessful(originalTx);

        assertTrue(booking.getState() instanceof PaidState);

        // Cancel should trigger refund
        boolean result = bookingService.cancelBooking(booking.getBookingId(), "Changed mind");
        assertTrue(result, "cancelBooking on PAID booking should return true");

        // Booking must now be CANCELLED
        assertTrue(booking.getState() instanceof CancelledState,
                "Booking must be CANCELLED after paid-booking cancellation");

        // A REFUNDED transaction must exist
        long refunds = paymentService.getPaymentHistory(client).stream()
                .filter(t -> t.getStatus() == PaymentStatus.REFUNDED)
                .count();
        assertEquals(1, refunds, "Exactly one REFUNDED transaction must exist");
    }

    // ── Test 3 ────────────────────────────────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("State: Invalid transitions throw IllegalStateException")
    void invalidStateTransitionsThrow() {
        // REQUESTED: paymentSuccessful and complete are invalid
        assertThrows(IllegalStateException.class,
                () -> booking.paymentSuccessful(null));
        assertThrows(IllegalStateException.class,
                booking::complete);

        // After REJECT → all transitions blocked
        booking.reject("Rejected for test");
        assertThrows(IllegalStateException.class, booking::confirm);
        assertThrows(IllegalStateException.class, () -> booking.reject("again"));
        assertThrows(IllegalStateException.class, () -> booking.cancel("reason"));
        assertThrows(IllegalStateException.class, booking::complete);

        // CANCELLED → all transitions blocked
        Booking b2 = bookingService.createBooking(client,
                new Service("S2", "S2", "d", 30, 50.0, consultant),
                new TimeSlot(
                    LocalDateTime.now().plusDays(3).withHour(9).withMinute(0),
                    LocalDateTime.now().plusDays(3).withHour(10).withMinute(0)));
        b2.cancel("test cancel");
        assertThrows(IllegalStateException.class, b2::confirm);
        assertThrows(IllegalStateException.class, b2::complete);

        // COMPLETED → all transitions blocked
        Booking b3 = bookingService.createBooking(client,
                new Service("S3", "S3", "d", 30, 50.0, consultant),
                new TimeSlot(
                    LocalDateTime.now().plusDays(4).withHour(9).withMinute(0),
                    LocalDateTime.now().plusDays(4).withHour(10).withMinute(0)));
        b3.confirm();
        b3.setState(new PendingPaymentState());
        PaymentTransaction tx = new PaymentTransaction(
                "TX-C3", b3, 50.0, PaymentStatus.SUCCESS, LocalDateTime.now());
        b3.paymentSuccessful(tx);
        b3.complete();
        assertTrue(b3.getState() instanceof CompletedState);
        assertThrows(IllegalStateException.class, b3::complete);
        assertThrows(IllegalStateException.class, b3::confirm);
    }

    // ── Test 4 ────────────────────────────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("UC5: processPayment transitions booking CONFIRMED → PENDING_PAYMENT → PAID")
    void processPaymentMovesToPaidOnSuccess() {
        bookingService.confirmBooking(booking.getBookingId());
        assertTrue(booking.getState() instanceof ConfirmedState);

        // Replace CoR chain with a fast success stub (avoids 2-3s Thread.sleep)
        paymentService.setHandler((b, method) -> {
            // processPayment already set state to PendingPaymentState
            assertTrue(b.getState() instanceof PendingPaymentState,
                    "Booking must be PENDING_PAYMENT when handler is called");
            PaymentTransaction tx = new PaymentTransaction(
                    "TX-STUB", b, b.getService().getPrice(),
                    PaymentStatus.SUCCESS, LocalDateTime.now());
            paymentService.setLastTransaction(tx);
            paymentService.storeTransaction(tx);
            b.paymentSuccessful(tx);
            return tx;
        });

        CreditCardMethod cc = new CreditCardMethod(
                client, "PM-T", "4111111111111111", "12/27", "123");

        PaymentTransaction result = paymentService.processPayment(booking, cc);

        assertNotNull(result, "Transaction must not be null");
        assertEquals(PaymentStatus.SUCCESS, result.getStatus(), "Status must be SUCCESS");
        assertTrue(booking.getState() instanceof PaidState,
                "Booking must be in PaidState after successful payment");

        // Verify history contains the transaction
        assertEquals(1, paymentService.getPaymentHistory(client).size(),
                "Payment history must have one entry");
    }
}
