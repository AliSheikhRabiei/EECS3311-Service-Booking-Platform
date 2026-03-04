package com.platform.domain;

/**
 * A consulting service that can be browsed (UC1) and booked (UC2).
 */
public class Service {

    private final String     serviceId;
    private final String     title;
    private final String     description;
    private final int        durationMin;
    private final double     price;
    private final Consultant consultant;

    public Service(String serviceId, String title, String description,
                   int durationMin, double price, Consultant consultant) {
        if (consultant == null) throw new IllegalArgumentException("Service must have a consultant.");
        if (durationMin <= 0)  throw new IllegalArgumentException("Duration must be positive.");
        if (price < 0)         throw new IllegalArgumentException("Price must not be negative.");
        this.serviceId   = serviceId;
        this.title       = title;
        this.description = description;
        this.durationMin = durationMin;
        this.price       = price;
        this.consultant  = consultant;
    }

    public String     getServiceId()   { return serviceId; }
    public String     getTitle()       { return title; }
    public String     getDescription() { return description; }
    public int        getDurationMin() { return durationMin; }
    public double     getPrice()       { return price; }
    public Consultant getConsultant()  { return consultant; }

    @Override
    public String toString() {
        return String.format("Service[id=%s, title='%s', duration=%dmin, price=$%.2f, consultant=%s]",
                serviceId, title, durationMin, price, consultant.getName());
    }
}
