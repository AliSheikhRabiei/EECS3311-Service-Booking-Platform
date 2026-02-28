package model;

import service.ServiceCatalog;
import java.util.List;
import java.util.stream.Collectors;

public class Consultant extends User {
    private String bio;
    private RegistrationStatus registrationStatus;

    public Consultant(String id, String name, String email, String bio) {
        super(id, name, email);
        this.bio = bio;
        this.registrationStatus = RegistrationStatus.PENDING;
    }

    public String getBio() { return bio; }

    public RegistrationStatus getRegistrationStatus() { return registrationStatus; }

    public void setRegistrationStatus(RegistrationStatus status) {
        this.registrationStatus = status;
    }

    public List<Service> listServices(ServiceCatalog catalog) {
        return catalog.listAllServices().stream()
                .filter(s -> s.getConsultant().getId().equals(this.getId()))
                .collect(Collectors.toList());
    }
}
