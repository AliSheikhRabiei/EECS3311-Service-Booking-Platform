package com.platform.policy;

import com.platform.domain.Service;

/**
 * Default pricing strategy: returns the service's base price unchanged.
 * TODO: implement dynamic pricing (e.g., peak-hour surcharge, discount tiers).
 */
public class DefaultPricingStrategy implements PricingStrategy {

    @Override
    public double calculatePrice(Service service) {
        if (service == null) return 0.0;
        return service.getPrice();
    }
}
