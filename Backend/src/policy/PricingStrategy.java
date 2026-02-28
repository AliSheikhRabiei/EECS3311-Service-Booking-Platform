package policy;

import model.Booking;
import model.Service;

public interface PricingStrategy {
    double calculatePrice(Service service, Booking booking);
}
