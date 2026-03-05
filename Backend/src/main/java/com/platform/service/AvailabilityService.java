package com.platform.service;

import com.platform.domain.Consultant;
import com.platform.domain.Service;
import com.platform.domain.TimeSlot;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manages consultant availability (UC8) and validates slot availability during booking (UC2).
 */
public class AvailabilityService {

    /** consultantId → list of all their time slots (available or not). */
    private final Map<String, List<TimeSlot>> slotsByConsultant = new HashMap<>();

    /** UC8 – Adds a new time slot to a consultant's schedule. */
    public void addTimeSlot(Consultant consultant, TimeSlot slot) {
        if (consultant == null) throw new IllegalArgumentException("Consultant must not be null.");
        if (slot       == null) throw new IllegalArgumentException("TimeSlot must not be null.");
        slotsByConsultant
                .computeIfAbsent(consultant.getId(), k -> new ArrayList<>())
                .add(slot);
        System.out.println("[AvailabilityService] Added slot for " + consultant.getName() + ": " + slot);
    }

    /**
     * UC8 – Removes a time slot from a consultant's schedule by slot id.
     * slotId is treated as {@code slot.getStart().toString()} (per spec note).
     * TODO: use a proper unique slot id if the domain model is extended.
     */
    public void removeTimeSlot(Consultant consultant, String slotId) {
        if (consultant == null || slotId == null) return;
        List<TimeSlot> slots = slotsByConsultant.getOrDefault(consultant.getId(), new ArrayList<>());
        boolean removed = slots.removeIf(s -> s.getSlotId().equals(slotId));
        System.out.println(removed
                ? "[AvailabilityService] Removed slot " + slotId
                : "[AvailabilityService] Slot not found: " + slotId);
    }

    /** UC2 – Returns only the available slots for the consultant offering this service. */
    public List<TimeSlot> listAvailableSlots(Service service) {
        if (service == null) return new ArrayList<>();
        String consultantId = service.getConsultant().getId();
        return slotsByConsultant.getOrDefault(consultantId, new ArrayList<>())
                .stream()
                .filter(TimeSlot::isAvailable)
                .collect(Collectors.toList());
    }

    /** Returns all slots (available and reserved) for a consultant. */
    public List<TimeSlot> listAllSlots(Consultant consultant) {
        if (consultant == null) return new ArrayList<>();
        return Collections.unmodifiableList(
                slotsByConsultant.getOrDefault(consultant.getId(), new ArrayList<>()));
    }

    /**
     * UC8 – Splits a long availability window into consecutive 1-hour TimeSlot objects
     * and adds each one to the consultant's schedule.
     *
     * Rules:
     * - end must be after start.
     * - Each slot is exactly 1 hour: [start, start+1h], [start+1h, start+2h], ...
     * - Segments shorter than 1 hour at the tail are ignored.
     * - Duplicate slot ids (same start time) are silently skipped.
     *
     * @param consultant the consultant whose availability is being set
     * @param start      the beginning of the availability window
     * @param end        the end of the availability window
     * @return the number of new slots actually added
     */
    public int addTimeSlotBlock(Consultant consultant, LocalDateTime start, LocalDateTime end) {
        if (consultant == null) throw new IllegalArgumentException("Consultant must not be null.");
        if (start == null || end == null) throw new IllegalArgumentException("start and end must not be null.");
        if (!end.isAfter(start)) throw new IllegalArgumentException("end must be after start.");

        // Collect existing slot ids so we can skip duplicates
        List<TimeSlot> existing = slotsByConsultant.getOrDefault(consultant.getId(), new ArrayList<>());
        java.util.Set<String> existingIds = existing.stream()
                .map(TimeSlot::getSlotId)
                .collect(java.util.stream.Collectors.toSet());

        int added = 0;
        LocalDateTime cursor = start;
        while (!cursor.plusHours(1).isAfter(end)) {
            LocalDateTime slotEnd = cursor.plusHours(1);
            TimeSlot slot = new TimeSlot(cursor, slotEnd);
            if (!existingIds.contains(slot.getSlotId())) {
                addTimeSlot(consultant, slot);
                existingIds.add(slot.getSlotId());
                added++;
            }
            cursor = slotEnd;
        }
        System.out.println("[AvailabilityService] Block added " + added + " slot(s) for " + consultant.getName());
        return added;
    }

    /** Alias for listAllSlots — returns all slots (available + reserved) for a consultant. */
    public List<TimeSlot> listAllSlotsForConsultant(Consultant consultant) {
        return listAllSlots(consultant);
    }
}
