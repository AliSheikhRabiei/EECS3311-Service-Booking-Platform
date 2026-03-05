package com.platform;

import com.platform.domain.*;
import com.platform.state.RequestedState;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests every domain class: User hierarchy, Service, TimeSlot, Booking.
 * Verifies constructors, getters, and defensive null/blank checks.
 */
@DisplayName("Domain Objects Tests")
class DomainTest {

    // ─── Fixtures shared across tests ─────────────────────────────────────────
    private Consultant consultant;
    private Client     client;
    private Service    service;
    private TimeSlot   slot;

    @BeforeEach
    void setUp() {
        consultant = new Consultant("C1", "Alice", "alice@test.com", "Java expert");
        consultant.setRegistrationStatus(RegistrationStatus.APPROVED);
        client     = new Client("U1", "Bob", "bob@test.com");
        service    = new Service("S1", "Tutoring", "One-on-one", 60, 100.0, consultant);
        slot       = new TimeSlot(
                LocalDateTime.now().plusDays(1).withHour(10).withMinute(0),
                LocalDateTime.now().plusDays(1).withHour(11).withMinute(0));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // User (abstract) — tested via Client
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("Client: getters return the values passed to the constructor")
    void clientGettersReturnConstructorValues() {
        assertEquals("U1",           client.getId());
        assertEquals("Bob",          client.getName());
        assertEquals("bob@test.com", client.getEmail());
    }

    @Test @DisplayName("User: blank id throws IllegalArgumentException")
    void userBlankIdThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Client("", "Bob", "bob@test.com"));
        assertThrows(IllegalArgumentException.class, () -> new Client("  ", "Bob", "bob@test.com"));
    }

    @Test @DisplayName("User: blank name throws IllegalArgumentException")
    void userBlankNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Client("U1", "", "bob@test.com"));
    }

    @Test @DisplayName("User: blank email throws IllegalArgumentException")
    void userBlankEmailThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Client("U1", "Bob", ""));
    }

    @Test @DisplayName("User: null values throw IllegalArgumentException")
    void userNullValuesThrow() {
        assertThrows(IllegalArgumentException.class, () -> new Client(null, "Bob", "bob@test.com"));
        assertThrows(IllegalArgumentException.class, () -> new Client("U1", null, "bob@test.com"));
        assertThrows(IllegalArgumentException.class, () -> new Client("U1", "Bob", null));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Consultant
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("Consultant: default registration status is PENDING")
    void consultantDefaultStatusIsPending() {
        Consultant fresh = new Consultant("C9", "Fresh", "f@test.com", "Bio");
        assertEquals(RegistrationStatus.PENDING, fresh.getRegistrationStatus());
    }

    @Test @DisplayName("Consultant: setRegistrationStatus changes status correctly")
    void consultantSetRegistrationStatus() {
        consultant.setRegistrationStatus(RegistrationStatus.REJECTED);
        assertEquals(RegistrationStatus.REJECTED, consultant.getRegistrationStatus());
        consultant.setRegistrationStatus(RegistrationStatus.APPROVED);
        assertEquals(RegistrationStatus.APPROVED, consultant.getRegistrationStatus());
    }

    @Test @DisplayName("Consultant: null status throws IllegalArgumentException")
    void consultantNullStatusThrows() {
        assertThrows(IllegalArgumentException.class, () -> consultant.setRegistrationStatus(null));
    }

    @Test @DisplayName("Consultant: addService and listService work correctly")
    void consultantAddAndListService() {
        assertTrue(consultant.listService().isEmpty(), "starts empty");
        consultant.addService(service);
        assertEquals(1, consultant.listService().size());
        assertEquals("Tutoring", consultant.listService().get(0).getTitle());
    }

    @Test @DisplayName("Consultant: addService with null throws")
    void consultantAddNullServiceThrows() {
        assertThrows(IllegalArgumentException.class, () -> consultant.addService(null));
    }

    @Test @DisplayName("Consultant: listService returns unmodifiable list")
    void consultantListServiceIsUnmodifiable() {
        consultant.addService(service);
        assertThrows(UnsupportedOperationException.class,
                () -> consultant.listService().clear());
    }

    @Test @DisplayName("Consultant: decideBooking ACCEPT calls confirm on the booking")
    void consultantDecideBookingAccept() {
        Booking b = new Booking("BK-1", client, service, slot);
        consultant.decideBooking(b, Decision.ACCEPT);
        assertInstanceOf(com.platform.state.ConfirmedState.class, b.getState());
    }

    @Test @DisplayName("Consultant: decideBooking REJECT calls reject on the booking")
    void consultantDecideBookingReject() {
        Booking b = new Booking("BK-1", client, service, slot);
        consultant.decideBooking(b, Decision.REJECT);
        assertInstanceOf(com.platform.state.RejectedState.class, b.getState());
    }

    @Test @DisplayName("Consultant: decideBooking null booking throws")
    void consultantDecideBookingNullThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> consultant.decideBooking(null, Decision.ACCEPT));
    }

    @Test @DisplayName("Consultant: getBio returns correct bio")
    void consultantGetBio() {
        assertEquals("Java expert", consultant.getBio());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Service
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("Service: getters return constructor values")
    void serviceGetters() {
        assertEquals("S1",        service.getServiceId());
        assertEquals("Tutoring",  service.getTitle());
        assertEquals("One-on-one",service.getDescription());
        assertEquals(60,          service.getDurationMin());
        assertEquals(100.0,       service.getPrice(), 0.001);
        assertSame(consultant,    service.getConsultant());
    }

    @Test @DisplayName("Service: null consultant throws")
    void serviceNullConsultantThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new Service("S2", "T", "D", 60, 50.0, null));
    }

    @Test @DisplayName("Service: zero or negative duration throws")
    void serviceInvalidDurationThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new Service("S2", "T", "D", 0, 50.0, consultant));
        assertThrows(IllegalArgumentException.class,
                () -> new Service("S2", "T", "D", -1, 50.0, consultant));
    }

    @Test @DisplayName("Service: negative price throws")
    void serviceNegativePriceThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new Service("S2", "T", "D", 60, -1.0, consultant));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TimeSlot
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("TimeSlot: starts available")
    void timeSlotStartsAvailable() {
        assertTrue(slot.isAvailable());
    }

    @Test @DisplayName("TimeSlot: reserve() makes it unavailable")
    void timeSlotReserve() {
        slot.reserve();
        assertFalse(slot.isAvailable());
    }

    @Test @DisplayName("TimeSlot: release() after reserve() makes it available again")
    void timeSlotRelease() {
        slot.reserve();
        slot.release();
        assertTrue(slot.isAvailable());
    }

    @Test @DisplayName("TimeSlot: reserve() is idempotent (no throw on double reserve)")
    void timeSlotReserveIdempotent() {
        slot.reserve();
        assertDoesNotThrow(() -> slot.reserve());
    }

    @Test @DisplayName("TimeSlot: end before start throws")
    void timeSlotEndBeforeStartThrows() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        assertThrows(IllegalArgumentException.class,
                () -> new TimeSlot(start, start.minusHours(1)));
    }

    @Test @DisplayName("TimeSlot: same start and end throws")
    void timeSlotSameStartEndThrows() {
        LocalDateTime t = LocalDateTime.now().plusDays(1);
        assertThrows(IllegalArgumentException.class, () -> new TimeSlot(t, t));
    }

    @Test @DisplayName("TimeSlot: null start or end throws")
    void timeSlotNullThrows() {
        LocalDateTime t = LocalDateTime.now().plusDays(1);
        assertThrows(IllegalArgumentException.class, () -> new TimeSlot(null, t));
        assertThrows(IllegalArgumentException.class, () -> new TimeSlot(t, null));
    }

    @Test @DisplayName("TimeSlot: getSlotId returns start.toString()")
    void timeSlotGetSlotId() {
        assertEquals(slot.getStart().toString(), slot.getSlotId());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Booking
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("Booking: constructor sets all fields and starts in RequestedState")
    void bookingConstructorSetsFields() {
        Booking b = new Booking("BK-1", client, service, slot);
        assertEquals("BK-1",   b.getBookingId());
        assertSame(client,     b.getClient());
        assertSame(service,    b.getService());
        assertSame(slot,       b.getSlot());
        assertNotNull(         b.getCreatedAt());
        assertInstanceOf(RequestedState.class, b.getState());
    }

    @Test @DisplayName("Booking: blank bookingId throws")
    void bookingBlankIdThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new Booking("", client, service, slot));
    }

    @Test @DisplayName("Booking: null fields throw")
    void bookingNullFieldsThrow() {
        assertThrows(IllegalArgumentException.class, () -> new Booking("BK-1", null, service, slot));
        assertThrows(IllegalArgumentException.class, () -> new Booking("BK-1", client, null, slot));
        assertThrows(IllegalArgumentException.class, () -> new Booking("BK-1", client, service, null));
    }

    @Test @DisplayName("Booking: setState to null throws")
    void bookingSetStateNullThrows() {
        Booking b = new Booking("BK-1", client, service, slot);
        assertThrows(IllegalArgumentException.class, () -> b.setState(null));
    }

    @Test @DisplayName("Booking: getStateName returns human-readable name")
    void bookingGetStateName() {
        Booking b = new Booking("BK-1", client, service, slot);
        assertEquals("REQUESTED", b.getStateName());
        b.confirm();
        assertEquals("CONFIRMED", b.getStateName());
    }

    @Test @DisplayName("Booking: request() does not throw and prints state")
    void bookingRequestDoesNotThrow() {
        Booking b = new Booking("BK-1", client, service, slot);
        assertDoesNotThrow(b::request);
    }

    @Test @DisplayName("Booking: toString contains bookingId and stateName")
    void bookingToString() {
        Booking b = new Booking("BK-99", client, service, slot);
        String s = b.toString();
        assertTrue(s.contains("BK-99"));
        assertTrue(s.contains("REQUESTED"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Admin
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("Admin: approveConsultantRegistration sets APPROVED correctly")
    void adminApproveConsultant() {
        com.platform.policy.PolicyManager.resetInstance();
        Admin admin = new Admin("A1", "Admin", "admin@test.com");
        Consultant c = new Consultant("C9", "New", "new@test.com", "Bio");
        admin.approveConsultantRegistration(c, true);
        assertEquals(RegistrationStatus.APPROVED, c.getRegistrationStatus());
    }

    @Test @DisplayName("Admin: approveConsultantRegistration false sets REJECTED")
    void adminRejectConsultant() {
        com.platform.policy.PolicyManager.resetInstance();
        Admin admin = new Admin("A1", "Admin", "admin@test.com");
        Consultant c = new Consultant("C9", "New", "new@test.com", "Bio");
        admin.approveConsultantRegistration(c, false);
        assertEquals(RegistrationStatus.REJECTED, c.getRegistrationStatus());
    }

    @Test @DisplayName("Admin: null consultant throws")
    void adminNullConsultantThrows() {
        com.platform.policy.PolicyManager.resetInstance();
        Admin admin = new Admin("A1", "Admin", "admin@test.com");
        assertThrows(IllegalArgumentException.class,
                () -> admin.approveConsultantRegistration((Consultant) null, true));
    }
}
