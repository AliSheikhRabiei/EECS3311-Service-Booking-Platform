package policy;

import model.Booking;
import model.Service;

/** Returns the base price defined on the service. */
public class DefaultPricingStrategy implements PricingStrategy {
    @Override
    public double calculatePrice(Service service, Booking booking) {
        return service.getPrice();
    }
}
