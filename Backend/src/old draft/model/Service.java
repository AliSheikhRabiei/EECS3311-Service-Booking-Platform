package model;

public class Service {
    private String serviceId;
    private String title;
    private String description;
    private int durationMin;
    private double price;
    private Consultant consultant;

    public Service(String serviceId, String title, String description,
                   int durationMin, double price, Consultant consultant) {
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
        return String.format("Service[id=%s, title=%s, duration=%dmin, price=$%.2f, consultant=%s]",
                serviceId, title, durationMin, price, consultant.getName());
    }
}
