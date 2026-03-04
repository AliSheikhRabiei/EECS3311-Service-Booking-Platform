package service;

import model.Consultant;
import model.Service;
import model.TimeSlot;

import java.util.*;
import java.util.stream.Collectors;

public class AvailabilityService {
    // consultantId → list of time slots
    private final Map<String, List<TimeSlot>> slots = new HashMap<>();

    public void addTimeSlot(Consultant c, TimeSlot slot) {
        slots.computeIfAbsent(c.getId(), k -> new ArrayList<>()).add(slot);
        System.out.println("[Availability] Added slot for " + c.getName() + ": " + slot);
    }

    public void removeTimeSlot(Consultant c, String slotId) {
        List<TimeSlot> list = slots.getOrDefault(c.getId(), new ArrayList<>());
        boolean removed = list.removeIf(s -> s.getSlotId().equals(slotId));
        if (removed) {
            System.out.println("[Availability] Removed slot " + slotId + " for " + c.getName());
        } else {
            System.out.println("[Availability] Slot not found: " + slotId);
        }
    }

    public List<TimeSlot> listAvailableSlots(Service service) {
        return slots.getOrDefault(service.getConsultant().getId(), new ArrayList<>())
                .stream()
                .filter(TimeSlot::isAvailable)
                .collect(Collectors.toList());
    }

    public List<TimeSlot> listAllSlots(Consultant c) {
        return new ArrayList<>(slots.getOrDefault(c.getId(), new ArrayList<>()));
    }
}
