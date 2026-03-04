package com.platform.service;

import com.platform.domain.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-memory catalogue of consulting services (UC1).
 */
public class ServiceCatalog {

    private final List<Service> services = new ArrayList<>();

    public void addService(Service s) {
        if (s == null) throw new IllegalArgumentException("Service must not be null.");
        services.add(s);
    }

    /** UC1 – returns all services available in the system. */
    public List<Service> listAllServices() {
        return Collections.unmodifiableList(services);
    }

    /** Looks up a service by its id; returns {@code null} if not found. */
    public Service findById(String serviceId) {
        if (serviceId == null) return null;
        return services.stream()
                .filter(s -> s.getServiceId().equals(serviceId))
                .findFirst()
                .orElse(null);
    }
}
