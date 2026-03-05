package com.platform;

import com.platform.domain.*;
import com.platform.service.AvailabilityService;
import com.platform.service.ServiceCatalog;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests ServiceCatalog (UC1) and AvailabilityService (UC8).
 */
@DisplayName("ServiceCatalog & AvailabilityService Tests")
class ServiceCatalogAndAvailabilityTest {

    private Consultant      consultant;
    private Service         service1, service2;
    private ServiceCatalog  catalog;
    private AvailabilityService availability;

    @BeforeEach
    void setUp() {
        consultant   = new Consultant("C1", "Alice", "alice@test.com", "Expert");
        service1     = new Service("S1", "Java Tutoring", "One-on-one", 60, 100.0, consultant);
        service2     = new Service("S2", "Career Coaching", "Resume help", 45, 75.0, consultant);
        catalog      = new ServiceCatalog();
        availability = new AvailabilityService();
    }

    private TimeSlot makeSlot(int dayOffset, int hour) {
        return new TimeSlot(
                LocalDateTime.now().plusDays(dayOffset).withHour(hour).withMinute(0).withSecond(0).withNano(0),
                LocalDateTime.now().plusDays(dayOffset).withHour(hour + 1).withMinute(0).withSecond(0).withNano(0));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ServiceCatalog
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("listAllServices: empty catalog returns empty list")
    void catalogEmptyList() {
        assertTrue(catalog.listAllServices().isEmpty());
    }

    @Test @DisplayName("addService and listAllServices returns all added services")
    void catalogAddAndList() {
        catalog.addService(service1);
        catalog.addService(service2);
        List<Service> all = catalog.listAllServices();
        assertEquals(2, all.size());
        assertTrue(all.contains(service1));
        assertTrue(all.contains(service2));
    }

    @Test @DisplayName("listAllServices returns unmodifiable list")
    void catalogListIsUnmodifiable() {
        catalog.addService(service1);
        assertThrows(UnsupportedOperationException.class,
                () -> catalog.listAllServices().clear());
    }

    @Test @DisplayName("addService null throws")
    void catalogAddNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> catalog.addService(null));
    }

    @Test @DisplayName("findById returns correct service")
    void catalogFindById() {
        catalog.addService(service1);
        catalog.addService(service2);
        assertSame(service1, catalog.findById("S1"));
        assertSame(service2, catalog.findById("S2"));
    }

    @Test @DisplayName("findById returns null for unknown id")
    void catalogFindByIdUnknown() {
        catalog.addService(service1);
        assertNull(catalog.findById("DOES-NOT-EXIST"));
    }

    @Test @DisplayName("findById null argument returns null safely")
    void catalogFindByIdNull() {
        catalog.addService(service1);
        assertNull(catalog.findById(null));
    }

    @Test @DisplayName("Multiple services can share the same consultant")
    void catalogMultipleServicesPerConsultant() {
        catalog.addService(service1);
        catalog.addService(service2);
        assertEquals(2, catalog.listAllServices().size());
        assertEquals(consultant, catalog.listAllServices().get(0).getConsultant());
        assertEquals(consultant, catalog.listAllServices().get(1).getConsultant());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // AvailabilityService
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("addTimeSlot and listAllSlots returns added slots")
    void availabilityAddAndListAll() {
        TimeSlot s1 = makeSlot(1, 10);
        TimeSlot s2 = makeSlot(1, 14);
        availability.addTimeSlot(consultant, s1);
        availability.addTimeSlot(consultant, s2);
        List<TimeSlot> all = availability.listAllSlots(consultant);
        assertEquals(2, all.size());
        assertTrue(all.contains(s1));
        assertTrue(all.contains(s2));
    }

    @Test @DisplayName("listAvailableSlots only returns available (not reserved) slots")
    void availabilityListAvailableFilters() {
        TimeSlot s1 = makeSlot(1, 10); // will be reserved
        TimeSlot s2 = makeSlot(1, 14); // remains available
        availability.addTimeSlot(consultant, s1);
        availability.addTimeSlot(consultant, s2);
        s1.reserve();

        List<TimeSlot> available = availability.listAvailableSlots(service1);
        assertEquals(1, available.size());
        assertSame(s2, available.get(0));
    }

    @Test @DisplayName("listAvailableSlots for unknown service returns empty")
    void availabilityUnknownServiceReturnsEmpty() {
        Consultant other = new Consultant("C99", "Other", "o@test.com", "Bio");
        Service   otherSvc = new Service("S99", "Other", "D", 30, 50.0, other);
        availability.addTimeSlot(consultant, makeSlot(1, 10));
        // other consultant has no slots registered
        assertTrue(availability.listAvailableSlots(otherSvc).isEmpty());
    }

    @Test @DisplayName("listAvailableSlots null service returns empty")
    void availabilityNullServiceReturnsEmpty() {
        availability.addTimeSlot(consultant, makeSlot(1, 10));
        assertTrue(availability.listAvailableSlots(null).isEmpty());
    }

    @Test @DisplayName("listAllSlots null consultant returns empty")
    void availabilityNullConsultantListAllEmpty() {
        availability.addTimeSlot(consultant, makeSlot(1, 10));
        assertTrue(availability.listAllSlots(null).isEmpty());
    }

    @Test @DisplayName("removeTimeSlot removes the correct slot by slotId")
    void availabilityRemoveSlot() {
        TimeSlot s1 = makeSlot(1, 10);
        TimeSlot s2 = makeSlot(1, 14);
        availability.addTimeSlot(consultant, s1);
        availability.addTimeSlot(consultant, s2);
        availability.removeTimeSlot(consultant, s1.getSlotId());
        List<TimeSlot> remaining = availability.listAllSlots(consultant);
        assertEquals(1, remaining.size());
        assertSame(s2, remaining.get(0));
    }

    @Test @DisplayName("removeTimeSlot with unknown id does not throw")
    void availabilityRemoveUnknownIdDoesNotThrow() {
        availability.addTimeSlot(consultant, makeSlot(1, 10));
        assertDoesNotThrow(() -> availability.removeTimeSlot(consultant, "UNKNOWN-ID"));
    }

    @Test @DisplayName("Two different consultants have independent slot pools")
    void availabilityConsultantsHaveIndependentSlots() {
        Consultant c2 = new Consultant("C2", "Bob", "bob@test.com", "Coach");
        Service    s2 = new Service("S99", "Career", "D", 45, 75.0, c2);

        TimeSlot slot1 = makeSlot(1, 10);
        TimeSlot slot2 = makeSlot(1, 14);
        availability.addTimeSlot(consultant, slot1);
        availability.addTimeSlot(c2, slot2);

        List<TimeSlot> c1Slots = availability.listAvailableSlots(service1);
        List<TimeSlot> c2Slots = availability.listAvailableSlots(s2);

        assertEquals(1, c1Slots.size());
        assertEquals(1, c2Slots.size());
        assertSame(slot1, c1Slots.get(0));
        assertSame(slot2, c2Slots.get(0));
    }

    @Test @DisplayName("After release, slot reappears in listAvailableSlots")
    void availabilityReleaseRestoresAvailability() {
        TimeSlot s = makeSlot(1, 10);
        availability.addTimeSlot(consultant, s);
        s.reserve();
        assertEquals(0, availability.listAvailableSlots(service1).size());
        s.release();
        assertEquals(1, availability.listAvailableSlots(service1).size());
    }
}
