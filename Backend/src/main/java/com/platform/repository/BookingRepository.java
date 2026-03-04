package com.platform.repository;

import com.platform.domain.Booking;
import com.platform.domain.Client;
import com.platform.domain.Consultant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * In-memory repository for {@link Booking} objects.
 * Uses a LinkedHashMap so insertion order is preserved for display.
 */
public class BookingRepository {

    private final Map<String, Booking> store = new LinkedHashMap<>();

    /** Persists (insert or update) a booking. */
    public void save(Booking b) {
        if (b == null) throw new IllegalArgumentException("Booking must not be null.");
        store.put(b.getBookingId(), b);
    }

    /** Returns the booking with the given id, or {@code null} if not found. */
    public Booking findById(String id) {
        return store.get(id);
    }

    /** Returns all bookings belonging to a specific client. */
    public List<Booking> findByClient(Client c) {
        if (c == null) return new ArrayList<>();
        return store.values().stream()
                .filter(b -> b.getClient().getId().equals(c.getId()))
                .collect(Collectors.toList());
    }

    /** Returns all bookings whose service is offered by the given consultant. */
    public List<Booking> findByConsultant(Consultant con) {
        if (con == null) return new ArrayList<>();
        return store.values().stream()
                .filter(b -> b.getService().getConsultant().getId().equals(con.getId()))
                .collect(Collectors.toList());
    }

    /** Returns every stored booking (defensive copy). */
    public List<Booking> findAll() {
        return new ArrayList<>(store.values());
    }
}
