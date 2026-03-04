package com.platform.policy;

import com.platform.domain.Service;

/** Strategy: calculates the final price for a service. */
public interface PricingStrategy {
    double calculatePrice(Service service);
}
