package com.platform;

import com.platform.domain.*;
import com.platform.notify.NotificationService;
import com.platform.payment.*;
import com.platform.policy.*;
import com.platform.repository.BookingRepository;
import com.platform.service.*;
import com.platform.state.*;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests — complete booking flows from creation to completion
 * or cancellation, with realistic wiring of all services.
 *
 * Each test runs the ENTIRE flow through the service layer, just like Main.java does.
 * Uses a fast stub payment handler so tests run in milliseconds.
 */
@DisplayName("End-to-End Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IntegrationTest {

    // All services wired together (same as Main.java does)
    private PolicyManager       policyManager;
    private NotificationService notificationService;
    private BookingRepository   repo;
    private AvailabilityService availability;
    private PaymentService      paymentService;
    private PaymentMethodService methodService;
    private BookingService      bookingService;
    private ServiceCatalog      catalog;

    // Demo fixtures
    private Admin       admin;
    private Client      client;
    private Consultant  consultant;
    private Service     service;
    private TimeSlot    slot1, slot2;
    private CreditCardMethod savedCard;

    @BeforeEach
    void setUp() {
        PolicyManager.resetInstance();
        policyManager       = PolicyManager.getInstance();
        notificationService = new NotificationService();
        repo                = new BookingRepository();
        availability        = new AvailabilityService();
        paymentService      = new PaymentService(policyManager, notificationService);
        methodService       = new PaymentMethodService();
        bookingService      = new BookingService(repo, availability, notificationService,
                                                 paymentService, policyManager);
        catalog             = new ServiceCatalog();
        admin               = new Admin("A1", "Alice Admin", "admin@test.com");

        // Consultant approved by admin
        consultant = new Consultant("C1", "Bob Consultant", "bob@test.com", "Java Expert");
        consultant.setRegistrationStatus(RegistrationStatus.APPROVED);
        consultant.addService(
                new Service("S1", "Java Tutoring", "1-on-1 Java coaching", 60, 100.0, consultant));
        service = consultant.listService().get(0);
        catalog.addService(service);

        client    = new Client("U1", "Carol Client", "carol@test.com");
        savedCard = new CreditCardMethod(client, "PM-1", "1234567890123456", "12/27", "123");
        methodService.addMethod(client, savedCard);

        slot1 = makeSlot(1, 10);
        slot2 = makeSlot(1, 14);
        availability.addTimeSlot(consultant, slot1);
        availability.addTimeSlot(consultant, slot2);

        // Install fast-success payment handler by default
        installFastSuccessHandler();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Full happy path: browse → book → confirm → pay → complete
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(1)
    @DisplayName("Happy path: REQUESTED → CONFIRMED → PAID → COMPLETED")
    void fullHappyPath() {
        // UC1 – Browse
        List<Service> services = catalog.listAllServices();
        assertFalse(services.isEmpty());
        assertEquals("Java Tutoring", services.get(0).getTitle());

        // UC2 – Request
        Booking b = bookingService.createBooking(client, service, slot1);
        assertInstanceOf(RequestedState.class, b.getState());
        assertFalse(slot1.isAvailable(), "Slot must be reserved");

        // UC9 – Consultant confirms
        bookingService.confirmBooking(b.getBookingId());
        assertInstanceOf(ConfirmedState.class, b.getState());

        // UC5 – Pay
        PaymentTransaction tx = paymentService.processPayment(b, savedCard);
        assertEquals(PaymentStatus.SUCCESS, tx.getStatus());
        assertInstanceOf(PaidState.class, b.getState());
        assertNotNull(paymentService.getLastReceipt());

        // UC10 – Complete
        bookingService.completeBooking(b.getBookingId());
        assertInstanceOf(CompletedState.class, b.getState());

        // UC4 – History
        List<Booking> history = bookingService.getBookingsForClient(client.getId());
        assertEquals(1, history.size());
        assertInstanceOf(CompletedState.class, history.get(0).getState());

        // UC7 – Payment history
        List<PaymentTransaction> payments = paymentService.getPaymentHistory(client);
        assertEquals(1, payments.size());
        assertEquals(PaymentStatus.SUCCESS, payments.get(0).getStatus());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Cancel flows
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(2)
    @DisplayName("UC3: Cancel a REQUESTED booking — slot is freed, state is CANCELLED")
    void cancelRequestedBooking() {
        Booking b = bookingService.createBooking(client, service, slot1);
        assertFalse(slot1.isAvailable());

        boolean cancelled = bookingService.cancelBooking(b.getBookingId(), "Changed mind");
        assertTrue(cancelled);
        assertInstanceOf(CancelledState.class, b.getState());
        assertTrue(slot1.isAvailable(), "Slot must be freed after cancellation");
    }

    @Test @Order(3)
    @DisplayName("UC3: Cancel a CONFIRMED booking — slot freed, CANCELLED, no refund")
    void cancelConfirmedBooking() {
        Booking b = bookingService.createBooking(client, service, slot1);
        bookingService.confirmBooking(b.getBookingId());

        boolean cancelled = bookingService.cancelBooking(b.getBookingId(), "Reason");
        assertTrue(cancelled);
        assertInstanceOf(CancelledState.class, b.getState());
        assertTrue(slot1.isAvailable());
        // No refund tx since payment was never made
        assertTrue(paymentService.getPaymentHistory(client).isEmpty());
    }

    @Test @Order(4)
    @DisplayName("UC3: Cancel a PAID booking — refund transaction created, state CANCELLED")
    void cancelPaidBookingWithRefund() {
        Booking b = bookingService.createBooking(client, service, slot1);
        bookingService.confirmBooking(b.getBookingId());
        paymentService.processPayment(b, savedCard);
        assertInstanceOf(PaidState.class, b.getState());

        boolean cancelled = bookingService.cancelBooking(b.getBookingId(), "Reason");
        assertTrue(cancelled);
        assertInstanceOf(CancelledState.class, b.getState());

        long refunds = paymentService.getPaymentHistory(client).stream()
                .filter(t -> t.getStatus() == PaymentStatus.REFUNDED).count();
        assertEquals(1, refunds, "One REFUNDED transaction must exist");
    }

    @Test @Order(5)
    @DisplayName("UC3: Cannot cancel a COMPLETED booking (policy blocks it)")
    void cannotCancelCompletedBooking() {
        Booking b = bookingService.createBooking(client, service, slot1);
        bookingService.confirmBooking(b.getBookingId());
        paymentService.processPayment(b, savedCard);
        bookingService.completeBooking(b.getBookingId());

        boolean cancelled = bookingService.cancelBooking(b.getBookingId(), "Too late");
        assertFalse(cancelled, "Cannot cancel a COMPLETED booking");
        assertInstanceOf(CompletedState.class, b.getState(), "State must remain COMPLETED");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Reject flow (UC9)
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(6)
    @DisplayName("UC9: Consultant rejects → REJECTED, slot is freed")
    void consultantRejectsBooking() {
        Booking b = bookingService.createBooking(client, service, slot1);
        assertFalse(slot1.isAvailable());

        bookingService.rejectBooking(b.getBookingId(), "Not available that day");
        assertInstanceOf(RejectedState.class, b.getState());
        assertTrue(slot1.isAvailable(), "Slot must be freed on rejection");
    }

    @Test @Order(7)
    @DisplayName("After rejection, the freed slot can be booked by another client")
    void freedSlotCanBeRebooked() {
        Client other = new Client("U2", "Dave", "dave@test.com");
        Booking b1 = bookingService.createBooking(client, service, slot1);
        bookingService.rejectBooking(b1.getBookingId(), "Busy");

        // Slot is now free — other client can book it
        Booking b2 = bookingService.createBooking(other, service, slot1);
        assertInstanceOf(RequestedState.class, b2.getState());
        assertFalse(slot1.isAvailable(), "Slot must be re-reserved for new booking");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Admin use cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(8)
    @DisplayName("UC11: Admin approves pending consultant, who can then accept bookings")
    void adminApprovesConsultant() {
        Consultant pending = new Consultant("C2", "Eve", "eve@test.com", "Coach");
        assertEquals(RegistrationStatus.PENDING, pending.getRegistrationStatus());

        admin.approveConsultantRegistration(pending, true);
        assertEquals(RegistrationStatus.APPROVED, pending.getRegistrationStatus());

        // Now a booking can be created with this consultant
        Service newSvc = new Service("S2", "Career Coaching", "Desc", 45, 75.0, pending);
        TimeSlot newSlot = makeSlot(3, 10);
        availability.addTimeSlot(pending, newSlot);
        assertDoesNotThrow(() -> bookingService.createBooking(client, newSvc, newSlot));
    }

    @Test @Order(9)
    @DisplayName("UC11: Admin rejects consultant — booking creation then throws")
    void adminRejectsConsultantBlocksBooking() {
        Consultant rejected = new Consultant("C3", "Frank", "frank@test.com", "Bio");
        admin.approveConsultantRegistration(rejected, false);
        assertEquals(RegistrationStatus.REJECTED, rejected.getRegistrationStatus());

        Service rejSvc = new Service("S3", "Bad Service", "Desc", 30, 50.0, rejected);
        assertThrows(IllegalStateException.class,
                () -> bookingService.createBooking(client, rejSvc, slot2));
    }

    @Test @Order(10)
    @DisplayName("UC12: Admin switches to strict no-cancellation policy — cancel returns false")
    void adminSwitchesToStrictCancellationPolicy() {
        admin.setCancellationPolicy(new CancellationPolicy() {
            public boolean canCancel(Booking b, LocalDateTime now) { return false; }
            public double cancellationFee(Booking b, LocalDateTime now) { return b.getService().getPrice(); }
        });

        Booking b = bookingService.createBooking(client, service, slot1);
        boolean result = bookingService.cancelBooking(b.getBookingId(), "Try to cancel");
        assertFalse(result, "Strict policy must block cancellation");
        // Booking must still be in REQUESTED state
        assertInstanceOf(RequestedState.class, b.getState());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Multiple clients / bookings
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(11)
    @DisplayName("Multiple clients can book different slots independently")
    void multipleClientsIndependentBookings() {
        Client client2 = new Client("U2", "Dave", "dave@test.com");
        Booking b1 = bookingService.createBooking(client,  service, slot1);
        Booking b2 = bookingService.createBooking(client2, service, slot2);

        assertNotEquals(b1.getBookingId(), b2.getBookingId());
        assertInstanceOf(RequestedState.class, b1.getState());
        assertInstanceOf(RequestedState.class, b2.getState());

        assertEquals(1, bookingService.getBookingsForClient(client.getId()).size());
        assertEquals(1, bookingService.getBookingsForClient(client2.getId()).size());
        assertEquals(2, bookingService.getBookingsForConsultant(consultant).size());
    }

    @Test @Order(12)
    @DisplayName("Same client cannot double-book the same slot")
    void sameSlotCannotBeDoubleBooked() {
        bookingService.createBooking(client, service, slot1);
        assertFalse(slot1.isAvailable());
        assertThrows(IllegalArgumentException.class,
                () -> bookingService.createBooking(client, service, slot1));
    }

    @Test @Order(13)
    @DisplayName("Payment history for client contains all their transactions across multiple bookings")
    void paymentHistoryMultipleBookings() {
        Booking b1 = bookingService.createBooking(client, service, slot1);
        Booking b2 = bookingService.createBooking(client, service, slot2);
        bookingService.confirmBooking(b1.getBookingId());
        bookingService.confirmBooking(b2.getBookingId());
        paymentService.processPayment(b1, savedCard);
        paymentService.processPayment(b2, savedCard);

        List<PaymentTransaction> history = paymentService.getPaymentHistory(client);
        assertEquals(2, history.size());
        assertTrue(history.stream().allMatch(t -> t.getStatus() == PaymentStatus.SUCCESS));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UC6 — Manage payment methods
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(14)
    @DisplayName("UC6: Client adds PayPal method, then uses it for payment")
    void clientAddsPayPalAndPays() {
        PayPalMethod pp = new PayPalMethod(client, "PM-PP", "carol@paypal.com");
        methodService.addMethod(client, pp);

        Booking b = bookingService.createBooking(client, service, slot1);
        bookingService.confirmBooking(b.getBookingId());
        PaymentTransaction tx = paymentService.processPayment(b, pp);
        assertEquals(PaymentStatus.SUCCESS, tx.getStatus());
    }

    @Test @Order(15)
    @DisplayName("UC6: Client removes a payment method — it no longer appears in list")
    void clientRemovesPaymentMethod() {
        assertEquals(1, methodService.listMethods(client).size());
        methodService.removeMethod(client, "PM-1");
        assertTrue(methodService.listMethods(client).isEmpty());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════════════

    private void installFastSuccessHandler() {
        paymentService.setHandler((b, method) -> {
            PaymentTransaction tx = new PaymentTransaction(
                    "TX-FAST-" + System.nanoTime(), b,
                    policyManager.getPricingStrategy().calculatePrice(b.getService()),
                    PaymentStatus.SUCCESS, LocalDateTime.now());
            paymentService.storeTransaction(tx);
            paymentService.setLastTransaction(tx);
            b.paymentSuccessful(tx);

            // Also create receipt (as MarkPaidHandler would)
            com.platform.payment.Receipt receipt = new com.platform.payment.Receipt(
                    "REC-" + System.nanoTime(), tx.getAmount(),
                    method.getMethodType(), LocalDateTime.now());
            paymentService.setLastReceipt(receipt);
            return tx;
        });
    }

    private TimeSlot makeSlot(int dayOffset, int hour) {
        return new TimeSlot(
                LocalDateTime.now().plusDays(dayOffset).withHour(hour).withMinute(0).withSecond(0).withNano(0),
                LocalDateTime.now().plusDays(dayOffset).withHour(hour + 1).withMinute(0).withSecond(0).withNano(0));
    }
}
