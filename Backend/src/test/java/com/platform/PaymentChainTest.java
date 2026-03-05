package com.platform;

import com.platform.domain.*;
import com.platform.notify.NotificationService;
import com.platform.payment.*;
import com.platform.policy.PolicyManager;
import com.platform.state.PendingPaymentState;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the Chain of Responsibility payment handlers individually and as a chain.
 * Uses fast stub handlers to avoid the real 2-3 second sleep in ProcessPaymentHandler.
 */
@DisplayName("Payment Chain of Responsibility Tests")
class PaymentChainTest {

    private Client     client;
    private Consultant consultant;
    private Service    service;
    private Booking    booking;
    private PaymentService paymentService;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        PolicyManager.resetInstance();
        notificationService = new NotificationService();
        paymentService = new PaymentService(PolicyManager.getInstance(), notificationService);

        consultant = new Consultant("C1", "Alice", "alice@test.com", "Expert");
        consultant.setRegistrationStatus(RegistrationStatus.APPROVED);
        client  = new Client("U1", "Bob", "bob@test.com");
        service = new Service("S1", "Tutoring", "Desc", 60, 100.0, consultant);
        TimeSlot slot = new TimeSlot(
                LocalDateTime.now().plusDays(1).withHour(10).withMinute(0),
                LocalDateTime.now().plusDays(1).withHour(11).withMinute(0));
        booking = new Booking("BK-1", client, service, slot);
        booking.confirm();
        booking.setState(new PendingPaymentState()); // pre-position for handlers
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ValidateMethodHandler — unit tests
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("ValidateMethodHandler: valid method passes to next and does not create FAILED tx")
    void validateHandlerPassesValidMethod() {
        ValidateMethodHandler validate = new ValidateMethodHandler();
        // Install a stub next that records it was called
        boolean[] nextCalled = { false };
        validate.setNext((b, m) -> {
            nextCalled[0] = true;
            return new PaymentTransaction("TX-STUB", b, 100.0, PaymentStatus.SUCCESS, LocalDateTime.now());
        });

        CreditCardMethod cc = new CreditCardMethod(client, "PM1", "1234567890123456", "12/27", "123");
        PaymentTransaction tx = validate.handle(booking, cc);
        assertTrue(nextCalled[0], "Next handler must be called when validation passes");
        assertEquals(PaymentStatus.SUCCESS, tx.getStatus());
    }

    @Test @DisplayName("ValidateMethodHandler: invalid method returns FAILED and stops chain")
    void validateHandlerStopsOnInvalid() {
        ValidateMethodHandler validate = new ValidateMethodHandler();
        boolean[] nextCalled = { false };
        validate.setNext((b, m) -> { nextCalled[0] = true; return null; });

        // Invalid credit card (wrong number length)
        CreditCardMethod badCard = new CreditCardMethod(client, "PM1", "123", "12/27", "123");
        PaymentTransaction tx = validate.handle(booking, badCard);

        assertFalse(nextCalled[0], "Next handler must NOT be called on invalid method");
        assertNotNull(tx);
        assertEquals(PaymentStatus.FAILED, tx.getStatus());
    }

    @Test @DisplayName("ValidateMethodHandler: invalid PayPal email stops chain")
    void validateHandlerInvalidPayPalStops() {
        ValidateMethodHandler validate = new ValidateMethodHandler();
        boolean[] nextCalled = { false };
        validate.setNext((b, m) -> { nextCalled[0] = true; return null; });

        PayPalMethod bad = new PayPalMethod(client, "PM1", "not-an-email");
        PaymentTransaction tx = validate.handle(booking, bad);

        assertFalse(nextCalled[0]);
        assertEquals(PaymentStatus.FAILED, tx.getStatus());
    }

    @Test @DisplayName("ValidateMethodHandler: no next handler returns null when valid")
    void validateHandlerNoNextReturnsNull() {
        ValidateMethodHandler validate = new ValidateMethodHandler();
        // No next set
        CreditCardMethod cc = new CreditCardMethod(client, "PM1", "1234567890123456", "12/27", "123");
        // passToNext returns null when no next
        PaymentTransaction tx = validate.handle(booking, cc);
        assertNull(tx, "Should return null when there is no next handler");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MarkPaidHandler — unit tests (using pre-set lastTransaction on service)
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("MarkPaidHandler: SUCCESS tx causes booking to become PAID")
    void markPaidHandlerTransitionsToPaid() {
        MarkPaidHandler markPaid = new MarkPaidHandler(paymentService, notificationService);

        PaymentTransaction tx = new PaymentTransaction(
                "TX-TEST", booking, 100.0, PaymentStatus.SUCCESS, LocalDateTime.now());
        paymentService.storeTransaction(tx);
        paymentService.setLastTransaction(tx);

        CreditCardMethod cc = new CreditCardMethod(client, "PM1", "1234567890123456", "12/27", "123");
        markPaid.handle(booking, cc);

        assertInstanceOf(com.platform.state.PaidState.class, booking.getState(),
                "Booking must be PAID after MarkPaidHandler handles a SUCCESS tx");
    }

    @Test @DisplayName("MarkPaidHandler: SUCCESS tx creates a receipt")
    void markPaidHandlerCreatesReceipt() {
        MarkPaidHandler markPaid = new MarkPaidHandler(paymentService, notificationService);

        PaymentTransaction tx = new PaymentTransaction(
                "TX-TEST", booking, 100.0, PaymentStatus.SUCCESS, LocalDateTime.now());
        paymentService.storeTransaction(tx);
        paymentService.setLastTransaction(tx);

        CreditCardMethod cc = new CreditCardMethod(client, "PM1", "1234567890123456", "12/27", "123");
        markPaid.handle(booking, cc);

        Receipt receipt = paymentService.getLastReceipt();
        assertNotNull(receipt, "Receipt must be created on SUCCESS");
        assertEquals(100.0, receipt.getAmount(), 0.001);
        assertEquals("CREDIT_CARD", receipt.getMethod());
        assertNotNull(receipt.getReceiptId());
    }

    @Test @DisplayName("MarkPaidHandler: null lastTransaction does not transition booking")
    void markPaidHandlerNullTxDoesNotTransition() {
        MarkPaidHandler markPaid = new MarkPaidHandler(paymentService, notificationService);
        paymentService.setLastTransaction(null);

        CreditCardMethod cc = new CreditCardMethod(client, "PM1", "1234567890123456", "12/27", "123");
        // Should not throw, booking stays in PendingPayment
        assertDoesNotThrow(() -> markPaid.handle(booking, cc));
        assertInstanceOf(PendingPaymentState.class, booking.getState());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // AbstractPaymentHandler — setNext / chain wiring
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("AbstractPaymentHandler: setNext returns the next handler (for fluent chaining)")
    void abstractHandlerSetNextReturnsNext() {
        ValidateMethodHandler validate = new ValidateMethodHandler();
        ProcessPaymentHandler process  = new ProcessPaymentHandler(paymentService);
        PaymentHandler returned = validate.setNext(process);
        assertSame(process, returned, "setNext must return the next handler");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Full chain — via PaymentService with fast stub
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("Full chain: valid card → SUCCESS → booking PAID")
    void fullChainValidCardSuccess() {
        // Replace ProcessPaymentHandler with a fast stub so no 2-3s delay
        paymentService.setHandler((b, method) -> {
            assertTrue(b.getState() instanceof PendingPaymentState,
                    "Handler must see PENDING_PAYMENT state");
            assertTrue(method.validate(), "Method must be valid");

            PaymentTransaction tx = new PaymentTransaction(
                    "TX-FAST", b, b.getService().getPrice(),
                    PaymentStatus.SUCCESS, LocalDateTime.now());
            paymentService.storeTransaction(tx);
            paymentService.setLastTransaction(tx);
            b.paymentSuccessful(tx);
            return tx;
        });

        // Reset booking to CONFIRMED so processPayment can do CONFIRMED→PENDING
        booking.setState(new com.platform.state.ConfirmedState());
        CreditCardMethod cc = new CreditCardMethod(client, "PM1", "1234567890123456", "12/27", "123");
        PaymentTransaction tx = paymentService.processPayment(booking, cc);

        assertEquals(PaymentStatus.SUCCESS, tx.getStatus());
        assertInstanceOf(com.platform.state.PaidState.class, booking.getState());
    }

    @Test @DisplayName("Full chain: invalid card → FAILED → booking stays PENDING_PAYMENT")
    void fullChainInvalidCardFails() {
        // Use real ValidateMethodHandler for this test
        ValidateMethodHandler validate = new ValidateMethodHandler();
        paymentService.setHandler(validate);
        // No next set — if validate passes it would return null; but here it should short-circuit

        booking.setState(new com.platform.state.ConfirmedState());
        CreditCardMethod bad = new CreditCardMethod(client, "PM1", "SHORT", "12/27", "123");

        // processPayment sets state to PENDING_PAYMENT first, then chain runs
        PaymentTransaction tx = paymentService.processPayment(booking, bad);

        assertNotNull(tx);
        assertEquals(PaymentStatus.FAILED, tx.getStatus());
    }

    @Test @DisplayName("processPayment: transitions booking to PENDING_PAYMENT before chain runs")
    void processPaymentSetsPendingPaymentFirst() {
        boolean[] seenPending = { false };
        paymentService.setHandler((b, m) -> {
            if (b.getState() instanceof PendingPaymentState) {
                seenPending[0] = true;
            }
            return new PaymentTransaction("TX", b, 100.0, PaymentStatus.FAILED, LocalDateTime.now());
        });

        booking.setState(new com.platform.state.ConfirmedState());
        CreditCardMethod cc = new CreditCardMethod(client, "PM1", "1234567890123456", "12/27", "123");
        paymentService.processPayment(booking, cc);
        assertTrue(seenPending[0], "Booking must be in PENDING_PAYMENT when handler chain receives it");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PaymentService guard tests
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("PaymentService: null booking throws")
    void paymentServiceNullBookingThrows() {
        CreditCardMethod cc = new CreditCardMethod(client, "PM1", "1234567890123456", "12/27", "123");
        assertThrows(IllegalArgumentException.class,
                () -> paymentService.processPayment(null, cc));
    }

    @Test @DisplayName("PaymentService: null payment method throws")
    void paymentServiceNullMethodThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> paymentService.processPayment(booking, null));
    }

    @Test @DisplayName("PaymentService: setHandler null throws")
    void paymentServiceSetHandlerNullThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> paymentService.setHandler(null));
    }
}
