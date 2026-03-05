package com.platform;

import com.platform.domain.Consultant;
import com.platform.domain.RegistrationStatus;
import com.platform.domain.TimeSlot;
import com.platform.service.AvailabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests AvailabilityService.addTimeSlotBlock() — the 1-hour slot splitting behaviour.
 */
@DisplayName("AvailabilityService Block Splitting Tests")
class AvailabilityBlockTest {

    private AvailabilityService availability;
    private Consultant          consultant;

    @BeforeEach
    void setUp() {
        availability = new AvailabilityService();
        consultant   = new Consultant("C1", "Alice", "alice@test.com", "Expert");
        consultant.setRegistrationStatus(RegistrationStatus.APPROVED);
    }

    @Test
    @DisplayName("10-hour block creates exactly 10 one-hour slots, all available")
    void tenHourBlockCreatesTenSlots() {
        LocalDateTime start = LocalDateTime.of(2026, 3, 10, 9, 0);
        LocalDateTime end   = LocalDateTime.of(2026, 3, 10, 19, 0);

        int added = availability.addTimeSlotBlock(consultant, start, end);

        assertEquals(10, added, "10-hour window must produce 10 slots");
        List<TimeSlot> slots = availability.listAllSlots(consultant);
        assertEquals(10, slots.size());

        for (TimeSlot s : slots) {
            assertEquals(1,
                    java.time.Duration.between(s.getStart(), s.getEnd()).toHours(),
                    "Each slot must be exactly 1 hour");
            assertTrue(s.isAvailable(), "All slots must start as available");
        }
    }

    @Test
    @DisplayName("Slots are consecutive with no gaps")
    void slotsAreConsecutive() {
        LocalDateTime start = LocalDateTime.of(2026, 3, 10, 9, 0);
        availability.addTimeSlotBlock(consultant, start, start.plusHours(3));

        List<TimeSlot> slots = availability.listAllSlots(consultant);
        assertEquals(3, slots.size());
        assertEquals(start,              slots.get(0).getStart());
        assertEquals(start.plusHours(1), slots.get(1).getStart());
        assertEquals(start.plusHours(2), slots.get(2).getStart());
        assertEquals(start.plusHours(3), slots.get(2).getEnd());
    }

    @Test
    @DisplayName("Partial trailing hour (e.g. 1.5h window) produces only 1 slot — tail ignored")
    void partialTrailingHourIgnored() {
        LocalDateTime start = LocalDateTime.of(2026, 3, 10, 9, 0);
        int added = availability.addTimeSlotBlock(consultant, start, start.plusMinutes(90));
        assertEquals(1, added, "Only 1 full hour slot; trailing 30 min is ignored");
    }

    @Test
    @DisplayName("Exact 1-hour window produces exactly 1 slot")
    void oneHourWindowProducesOneSlot() {
        LocalDateTime start = LocalDateTime.of(2026, 3, 10, 9, 0);
        int added = availability.addTimeSlotBlock(consultant, start, start.plusHours(1));
        assertEquals(1, added);
    }

    @Test
    @DisplayName("Duplicate slots are skipped — calling block twice on same window adds 0 the second time")
    void duplicateSlotsSkipped() {
        LocalDateTime start = LocalDateTime.of(2026, 3, 10, 9, 0);
        LocalDateTime end   = start.plusHours(3);
        availability.addTimeSlotBlock(consultant, start, end);
        int second = availability.addTimeSlotBlock(consultant, start, end);
        assertEquals(0, second, "No duplicates should be added on second call");
        assertEquals(3, availability.listAllSlots(consultant).size());
    }

    @Test
    @DisplayName("end before start throws IllegalArgumentException")
    void endBeforeStartThrows() {
        LocalDateTime start = LocalDateTime.of(2026, 3, 10, 12, 0);
        assertThrows(IllegalArgumentException.class,
                () -> availability.addTimeSlotBlock(consultant, start, start.minusHours(1)));
    }

    @Test
    @DisplayName("end equal to start throws IllegalArgumentException")
    void endEqualStartThrows() {
        LocalDateTime start = LocalDateTime.of(2026, 3, 10, 12, 0);
        assertThrows(IllegalArgumentException.class,
                () -> availability.addTimeSlotBlock(consultant, start, start));
    }

    @Test
    @DisplayName("null consultant throws")
    void nullConsultantThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> availability.addTimeSlotBlock(null,
                        LocalDateTime.of(2026, 3, 10, 9, 0),
                        LocalDateTime.of(2026, 3, 10, 11, 0)));
    }

    @Test
    @DisplayName("listAllSlotsForConsultant returns same list as listAllSlots")
    void listAllSlotsForConsultantAlias() {
        LocalDateTime start = LocalDateTime.of(2026, 3, 10, 9, 0);
        availability.addTimeSlotBlock(consultant, start, start.plusHours(2));
        assertEquals(
                availability.listAllSlots(consultant),
                availability.listAllSlotsForConsultant(consultant));
    }

    @Test
    @DisplayName("Blocks on different days do not interfere with each other")
    void differentDaysAreIndependent() {
        LocalDateTime day1 = LocalDateTime.of(2026, 3, 10, 9, 0);
        LocalDateTime day2 = LocalDateTime.of(2026, 3, 11, 9, 0);
        availability.addTimeSlotBlock(consultant, day1, day1.plusHours(2));
        availability.addTimeSlotBlock(consultant, day2, day2.plusHours(3));
        assertEquals(5, availability.listAllSlots(consultant).size());
    }
}
