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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests BookingService: createBooking, confirmBooking, rejectBooking,
 * completeBooking, cancelBooking, getBooking, getBookingsForClient,
 * getBookingsForConsultant, and edge-case validation.
 */
@DisplayName("BookingService Tests")
class BookingServiceTest {

    private BookingService    bookingService;
    private PaymentService    paymentService;
    private AvailabilityService availability;
    private BookingRepository repo;

    private Client     client;
    private Consultant consultant;
    private Service    service;
    private TimeSlot   slot1, slot2;

    @BeforeEach
    void setUp() {
        PolicyManager.resetInstance();
        NotificationService notify = new NotificationService();
        repo         = new BookingRepository();
        availability = new AvailabilityService();
        paymentService = new PaymentService(PolicyManager.getInstance(), notify);

        bookingService = new BookingService(
                repo, availability, notify, paymentService, PolicyManager.getInstance());

        consultant = new Consultant("C1", "Alice", "alice@test.com", "Expert");
        consultant.setRegistrationStatus(RegistrationStatus.APPROVED);
        client  = new Client("U1", "Bob", "bob@test.com");
        service = new Service("S1", "Java Tutoring", "Desc", 60, 100.0, consultant);

        slot1 = makeSlot(1, 10);
        slot2 = makeSlot(1, 14);
        availability.addTimeSlot(consultant, slot1);
        availability.addTimeSlot(consultant, slot2);
    }

    private TimeSlot makeSlot(int dayOffset, int hour) {
        return new TimeSlot(
                LocalDateTime.now().plusDays(dayOffset).withHour(hour).withMinute(0).withSecond(0).withNano(0),
                LocalDateTime.now().plusDays(dayOffset).withHour(hour + 1).withMinute(0).withSecond(0).withNano(0));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // createBooking (UC2)
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("createBooking: returns booking in REQUESTED state")
    void createBookingReturnsRequested() {
        Booking b = bookingService.createBooking(client, service, slot1);
        assertNotNull(b);
        assertInstanceOf(RequestedState.class, b.getState());
    }

    @Test @DisplayName("createBooking: booking is saved to repository")
    void createBookingSavedToRepo() {
        Booking b = bookingService.createBooking(client, service, slot1);
        assertSame(b, repo.findById(b.getBookingId()));
    }

    @Test @DisplayName("createBooking: slot is reserved after creation")
    void createBookingReservesSlot() {
        bookingService.createBooking(client, service, slot1);
        assertFalse(slot1.isAvailable(), "Slot must be reserved after booking creation");
    }

    @Test @DisplayName("createBooking: booking has correct client, service, slot")
    void createBookingFieldsCorrect() {
        Booking b = bookingService.createBooking(client, service, slot1);
        assertSame(client,  b.getClient());
        assertSame(service, b.getService());
        assertSame(slot1,   b.getSlot());
    }

    @Test @DisplayName("createBooking: unapproved consultant throws IllegalStateException")
    void createBookingUnapprovedConsultantThrows() {
        Consultant pending = new Consultant("C2", "Carol", "carol@test.com", "New");
        // stays PENDING
        Service pendingSvc = new Service("S2", "Career", "Desc", 45, 75.0, pending);
        assertThrows(IllegalStateException.class,
                () -> bookingService.createBooking(client, pendingSvc, slot1));
    }

    @Test @DisplayName("createBooking: rejected consultant throws")
    void createBookingRejectedConsultantThrows() {
        consultant.setRegistrationStatus(RegistrationStatus.REJECTED);
        assertThrows(IllegalStateException.class,
                () -> bookingService.createBooking(client, service, slot1));
    }

    @Test @DisplayName("createBooking: unavailable slot throws")
    void createBookingUnavailableSlotThrows() {
        slot1.reserve();
        assertThrows(IllegalArgumentException.class,
                () -> bookingService.createBooking(client, service, slot1));
    }

    @Test @DisplayName("createBooking: null client throws")
    void createBookingNullClientThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> bookingService.createBooking(null, service, slot1));
    }

    @Test @DisplayName("createBooking: null service throws")
    void createBookingNullServiceThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> bookingService.createBooking(client, null, slot1));
    }

    @Test @DisplayName("createBooking: null slot throws")
    void createBookingNullSlotThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> bookingService.createBooking(client, service, null));
    }

    @Test @DisplayName("createBooking: booking IDs are unique across multiple bookings")
    void createBookingUniqueIds() {
        Booking b1 = bookingService.createBooking(client, service, slot1);
        Booking b2 = bookingService.createBooking(client, service, slot2);
        assertNotEquals(b1.getBookingId(), b2.getBookingId());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // confirmBooking / rejectBooking (UC9)
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("confirmBooking: moves booking from REQUESTED to CONFIRMED")
    void confirmBookingMovesToConfirmed() {
        Booking b = bookingService.createBooking(client, service, slot1);
        bookingService.confirmBooking(b.getBookingId());
        assertInstanceOf(ConfirmedState.class, b.getState());
    }

    @Test @DisplayName("confirmBooking: booking is re-saved after state change")
    void confirmBookingUpdatesRepo() {
        Booking b = bookingService.createBooking(client, service, slot1);
        bookingService.confirmBooking(b.getBookingId());
        Booking found = repo.findById(b.getBookingId());
        assertInstanceOf(ConfirmedState.class, found.getState());
    }

    @Test @DisplayName("confirmBooking: unknown booking id throws")
    void confirmBookingUnknownIdThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> bookingService.confirmBooking("BK-NONE"));
    }

    @Test @DisplayName("rejectBooking: moves booking from REQUESTED to REJECTED")
    void rejectBookingMovesToRejected() {
        Booking b = bookingService.createBooking(client, service, slot1);
        bookingService.rejectBooking(b.getBookingId(), "No availability");
        assertInstanceOf(RejectedState.class, b.getState());
    }

    @Test @DisplayName("rejectBooking: releases the slot so it becomes available again")
    void rejectBookingReleasesSlot() {
        Booking b = bookingService.createBooking(client, service, slot1);
        assertFalse(slot1.isAvailable(), "slot reserved after booking");
        bookingService.rejectBooking(b.getBookingId(), "Reason");
        assertTrue(slot1.isAvailable(), "slot must be free after rejection");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // completeBooking (UC10)
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("completeBooking: moves PAID booking to COMPLETED")
    void completeBookingMovesPaidToCompleted() {
        Booking b = createPaidBooking();
        bookingService.completeBooking(b.getBookingId());
        assertInstanceOf(CompletedState.class, b.getState());
    }

    @Test @DisplayName("completeBooking: throws if booking is only CONFIRMED (not paid)")
    void completeBookingNotPaidThrows() {
        Booking b = bookingService.createBooking(client, service, slot1);
        bookingService.confirmBooking(b.getBookingId());
        assertThrows(IllegalStateException.class,
                () -> bookingService.completeBooking(b.getBookingId()));
    }

    @Test @DisplayName("completeBooking: throws if booking is still REQUESTED")
    void completeBookingRequestedThrows() {
        Booking b = bookingService.createBooking(client, service, slot1);
        assertThrows(IllegalStateException.class,
                () -> bookingService.completeBooking(b.getBookingId()));
    }

    @Test @DisplayName("completeBooking: unknown id throws")
    void completeBookingUnknownIdThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> bookingService.completeBooking("BK-NONE"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // cancelBooking (UC3)
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("cancelBooking REQUESTED: returns true and moves to CANCELLED")
    void cancelRequestedBooking() {
        Booking b = bookingService.createBooking(client, service, slot1);
        boolean result = bookingService.cancelBooking(b.getBookingId(), "Changed mind");
        assertTrue(result);
        assertInstanceOf(CancelledState.class, b.getState());
    }

    @Test @DisplayName("cancelBooking CONFIRMED: returns true and moves to CANCELLED")
    void cancelConfirmedBooking() {
        Booking b = bookingService.createBooking(client, service, slot1);
        bookingService.confirmBooking(b.getBookingId());
        boolean result = bookingService.cancelBooking(b.getBookingId(), "Reason");
        assertTrue(result);
        assertInstanceOf(CancelledState.class, b.getState());
    }

    @Test @DisplayName("cancelBooking REQUESTED: releases the slot")
    void cancelReleasesSlot() {
        Booking b = bookingService.createBooking(client, service, slot1);
        bookingService.cancelBooking(b.getBookingId(), "Reason");
        assertTrue(slot1.isAvailable());
    }

    @Test @DisplayName("cancelBooking COMPLETED: returns false (policy blocks it)")
    void cancelCompletedBookingFalse() {
        Booking b = createPaidBooking();
        bookingService.completeBooking(b.getBookingId());
        boolean result = bookingService.cancelBooking(b.getBookingId(), "Too late");
        assertFalse(result, "Cancelling a COMPLETED booking should be blocked by policy");
    }

    @Test @DisplayName("cancelBooking REJECTED: returns false (policy blocks it)")
    void cancelRejectedBookingFalse() {
        Booking b = bookingService.createBooking(client, service, slot1);
        bookingService.rejectBooking(b.getBookingId(), "Reason");
        boolean result = bookingService.cancelBooking(b.getBookingId(), "Reason");
        assertFalse(result);
    }

    @Test @DisplayName("cancelBooking already CANCELLED: returns false")
    void cancelAlreadyCancelledFalse() {
        Booking b = bookingService.createBooking(client, service, slot1);
        bookingService.cancelBooking(b.getBookingId(), "First cancel");
        boolean result = bookingService.cancelBooking(b.getBookingId(), "Second cancel");
        assertFalse(result);
    }

    @Test @DisplayName("cancelBooking PAID booking: triggers refund and moves to CANCELLED")
    void cancelPaidBookingTriggersRefund() {
        Booking b = createPaidBooking();
        boolean result = bookingService.cancelBooking(b.getBookingId(), "Reason");
        assertTrue(result);
        assertInstanceOf(CancelledState.class, b.getState());
        long refunds = paymentService.getPaymentHistory(client).stream()
                .filter(t -> t.getStatus() == PaymentStatus.REFUNDED).count();
        assertEquals(1, refunds, "One refund transaction must exist");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getBooking / getBookingsForClient / getBookingsForConsultant
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("getBooking returns correct booking by id")
    void getBookingById() {
        Booking b = bookingService.createBooking(client, service, slot1);
        assertSame(b, bookingService.getBooking(b.getBookingId()));
    }

    @Test @DisplayName("getBooking returns null for unknown id")
    void getBookingUnknownNull() {
        assertNull(bookingService.getBooking("BK-NONE"));
    }

    @Test @DisplayName("getBookingsForClient returns all bookings for that client")
    void getBookingsForClient() {
        Client other = new Client("U2", "Carol", "carol@test.com");
        Booking b1 = bookingService.createBooking(client, service, slot1);
        Booking b2 = bookingService.createBooking(other, service, slot2);
        List<Booking> forClient = bookingService.getBookingsForClient(client.getId());
        assertEquals(1, forClient.size());
        assertTrue(forClient.contains(b1));
        assertFalse(forClient.contains(b2));
    }

    @Test @DisplayName("getBookingsForClient with no bookings returns empty list")
    void getBookingsForClientEmpty() {
        assertTrue(bookingService.getBookingsForClient("U-NOBODY").isEmpty());
    }

    @Test @DisplayName("getBookingsForConsultant returns bookings for that consultant's services")
    void getBookingsForConsultant() {
        Booking b = bookingService.createBooking(client, service, slot1);
        List<Booking> forConsultant = bookingService.getBookingsForConsultant(consultant);
        assertEquals(1, forConsultant.size());
        assertTrue(forConsultant.contains(b));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helper — create a booking in PAID state using a fast stub handler
    // ══════════════════════════════════════════════════════════════════════════

    private Booking createPaidBooking() {
        // Install a fast success handler so tests don't wait 2-3 seconds
        paymentService.setHandler((b, method) -> {
            PaymentTransaction tx = new PaymentTransaction(
                    "TX-STUB-" + System.nanoTime(), b,
                    b.getService().getPrice(), PaymentStatus.SUCCESS, LocalDateTime.now());
            paymentService.storeTransaction(tx);
            paymentService.setLastTransaction(tx);
            b.paymentSuccessful(tx);
            return tx;
        });

        Booking b = bookingService.createBooking(client, service, slot1);
        bookingService.confirmBooking(b.getBookingId());
        CreditCardMethod cc = new CreditCardMethod(client, "PM-T", "4111111111111111", "12/27", "123");
        paymentService.processPayment(b, cc);
        return b;
    }
}
