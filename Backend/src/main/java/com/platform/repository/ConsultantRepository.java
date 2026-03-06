package com.platform.repository;

import com.platform.domain.Booking;
import com.platform.domain.Client;
import com.platform.domain.Consultant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ConsultantRepository {
    private final Map<String, Consultant> store = new LinkedHashMap<>();

    /** Persists (insert or update) a Consultant. */
    public void save(Consultant c) {
        if (c == null) throw new IllegalArgumentException("Booking must not be null.");
        store.put(c.getId(), c);
    }

    /** Returns the Consultant with the given id, or {@code null} if not found. */
    public Consultant findById(String id) {
        return store.get(id);
    }




    /** Returns every stored booking (defensive copy). */
    public List<Consultant> findAll() {
        return new ArrayList<>(store.values());
    }
}

