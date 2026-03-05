package com.platform;

import com.platform.domain.*;
import com.platform.repository.BookingRepository;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests BookingRepository: save, findById, findByClient, findByConsultant, findAll.
 */
@DisplayName("BookingRepository Tests")
class RepositoryTest {

    private BookingRepository repo;
    private Client     client1, client2;
    private Consultant consultant1, consultant2;
    private Service    service1, service2;
    private Booking    b1, b2, b3;

    @BeforeEach
    void setUp() {
        repo       = new BookingRepository();
        client1    = new Client("U1", "Alice", "alice@test.com");
        client2    = new Client("U2", "Bob",   "bob@test.com");
        consultant1 = new Consultant("C1", "Carol", "carol@test.com", "Expert");
        consultant2 = new Consultant("C2", "Dave",  "dave@test.com",  "Coach");
        service1   = new Service("S1", "Java",    "Desc", 60, 100.0, consultant1);
        service2   = new Service("S2", "Career",  "Desc", 45,  75.0, consultant2);

        TimeSlot slot1 = makeSlot(1, 10);
        TimeSlot slot2 = makeSlot(1, 12);
        TimeSlot slot3 = makeSlot(2, 10);

        b1 = new Booking("BK-1", client1, service1, slot1);
        b2 = new Booking("BK-2", client2, service2, slot2);
        b3 = new Booking("BK-3", client1, service2, slot3);  // client1 booking with consultant2
    }

    private TimeSlot makeSlot(int dayOffset, int hour) {
        return new TimeSlot(
                LocalDateTime.now().plusDays(dayOffset).withHour(hour).withMinute(0).withSecond(0).withNano(0),
                LocalDateTime.now().plusDays(dayOffset).withHour(hour + 1).withMinute(0).withSecond(0).withNano(0));
    }

    // ── save / findById ───────────────────────────────────────────────────────

    @Test @DisplayName("save and findById returns the same booking")
    void saveAndFindById() {
        repo.save(b1);
        Booking found = repo.findById("BK-1");
        assertSame(b1, found);
    }

    @Test @DisplayName("findById returns null for unknown id")
    void findByIdUnknownReturnsNull() {
        assertNull(repo.findById("DOES-NOT-EXIST"));
    }

    @Test @DisplayName("save overwrites existing booking with same id (update behaviour)")
    void saveOverwritesExisting() {
        repo.save(b1);
        // Simulate state change, then re-save
        b1.confirm();
        repo.save(b1);
        Booking found = repo.findById("BK-1");
        assertSame(b1, found);
        assertInstanceOf(com.platform.state.ConfirmedState.class, found.getState());
    }

    @Test @DisplayName("save null throws IllegalArgumentException")
    void saveNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> repo.save(null));
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @Test @DisplayName("findAll returns all saved bookings")
    void findAllReturnsAll() {
        repo.save(b1);
        repo.save(b2);
        repo.save(b3);
        List<Booking> all = repo.findAll();
        assertEquals(3, all.size());
        assertTrue(all.contains(b1));
        assertTrue(all.contains(b2));
        assertTrue(all.contains(b3));
    }

    @Test @DisplayName("findAll returns empty list when repo is empty")
    void findAllEmptyRepo() {
        assertTrue(repo.findAll().isEmpty());
    }

    @Test @DisplayName("findAll returns a defensive copy (modifying it does not affect repo)")
    void findAllDefensiveCopy() {
        repo.save(b1);
        List<Booking> copy = repo.findAll();
        copy.clear();
        assertEquals(1, repo.findAll().size(), "Repo must not be affected by clearing the returned list");
    }

    // ── findByClient ──────────────────────────────────────────────────────────

    @Test @DisplayName("findByClient returns only bookings for the given client")
    void findByClientFiltersCorrectly() {
        repo.save(b1);
        repo.save(b2);
        repo.save(b3);
        List<Booking> forClient1 = repo.findByClient(client1);
        assertEquals(2, forClient1.size(), "client1 has 2 bookings: b1 and b3");
        assertTrue(forClient1.contains(b1));
        assertTrue(forClient1.contains(b3));
        assertFalse(forClient1.contains(b2));
    }

    @Test @DisplayName("findByClient returns empty list for client with no bookings")
    void findByClientNoBookings() {
        repo.save(b1);
        Client stranger = new Client("U99", "Stranger", "s@test.com");
        assertTrue(repo.findByClient(stranger).isEmpty());
    }

    @Test @DisplayName("findByClient null client returns empty list")
    void findByClientNullReturnsEmpty() {
        repo.save(b1);
        assertTrue(repo.findByClient(null).isEmpty());
    }

    // ── findByConsultant ──────────────────────────────────────────────────────

    @Test @DisplayName("findByConsultant returns only bookings for that consultant's service")
    void findByConsultantFiltersCorrectly() {
        repo.save(b1); // consultant1
        repo.save(b2); // consultant2
        repo.save(b3); // consultant2

        List<Booking> forC1 = repo.findByConsultant(consultant1);
        assertEquals(1, forC1.size());
        assertTrue(forC1.contains(b1));

        List<Booking> forC2 = repo.findByConsultant(consultant2);
        assertEquals(2, forC2.size());
        assertTrue(forC2.contains(b2));
        assertTrue(forC2.contains(b3));
    }

    @Test @DisplayName("findByConsultant null returns empty list")
    void findByConsultantNullReturnsEmpty() {
        repo.save(b1);
        assertTrue(repo.findByConsultant(null).isEmpty());
    }

    @Test @DisplayName("Insertion order is preserved by findAll")
    void insertionOrderPreserved() {
        repo.save(b1);
        repo.save(b2);
        repo.save(b3);
        List<Booking> all = repo.findAll();
        assertEquals("BK-1", all.get(0).getBookingId());
        assertEquals("BK-2", all.get(1).getBookingId());
        assertEquals("BK-3", all.get(2).getBookingId());
    }
}
