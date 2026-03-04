package com.platform.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A service provider who offers services, manages availability, and
 * decides on booking requests (UC8–UC10).
 */
public class Consultant extends User {

    private final String       bio;
    private RegistrationStatus registrationStatus;
    private final List<Service> services = new ArrayList<>();

    public Consultant(String id, String name, String email, String bio) {
        super(id, name, email);
        this.bio                = bio;
        this.registrationStatus = RegistrationStatus.PENDING;
    }

    /** Adds a service offered by this consultant (used during setup). */
    public void addService(Service service) {
        if (service == null) throw new IllegalArgumentException("Service must not be null.");
        services.add(service);
    }

    /**
     * UC1 – lists services offered by this consultant.
     * Returns an unmodifiable view of the internal list.
     */
    public List<Service> listService() {
        return Collections.unmodifiableList(services);
    }

    /**
     * UC9 – the consultant makes an ACCEPT or REJECT decision on a booking.
     * Actual state transitions are coordinated by BookingService; this method
     * simply applies the decision to the booking.
     *
     * @param b        the booking to decide on
     * @param decision ACCEPT or REJECT
     */
    public void decideBooking(Booking b, Decision decision) {
        if (b == null)        throw new IllegalArgumentException("Booking must not be null.");
        if (decision == null) throw new IllegalArgumentException("Decision must not be null.");
        switch (decision) {
            case ACCEPT -> b.confirm();
            case REJECT -> b.reject("Rejected by consultant " + getName());
        }
    }

    /**
     * Returns bookings for this consultant from the given list.
     * NOTE: In a real system this would query a repository.  The repository
     * reference is intentionally kept out of the domain object; call
     * BookingRepository.findByConsultant(consultant) from the service layer instead.
     *
     * @param consultantId ignored; present to match the class-diagram signature
     */
    public List<Booking> getBookingsForConsultant(String consultantId) {
        // TODO: inject BookingRepository and delegate; for now returns empty list
        return new ArrayList<>();
    }

    public String             getBio()                 { return bio; }
    public RegistrationStatus getRegistrationStatus()  { return registrationStatus; }
    public void setRegistrationStatus(RegistrationStatus status) {
        if (status == null) throw new IllegalArgumentException("Status must not be null.");
        this.registrationStatus = status;
    }
}
