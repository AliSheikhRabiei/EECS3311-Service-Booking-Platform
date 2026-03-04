package service;

import model.Service;

import java.util.ArrayList;
import java.util.List;

public class ServiceCatalog {
    private final List<Service> services = new ArrayList<>();

    public void addService(Service s) {
        services.add(s);
    }

    public List<Service> listAllServices() {
        return new ArrayList<>(services);
    }

    public Service findById(String serviceId) {
        return services.stream()
                .filter(s -> s.getServiceId().equals(serviceId))
                .findFirst().orElse(null);
    }
}
