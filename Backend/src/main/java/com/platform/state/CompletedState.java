package com.platform.state;

import com.platform.domain.Booking;
import com.platform.payment.PaymentTransaction;

/**
 * Terminal state: consulting session has been completed.
 * No further transitions are permitted.
 */
public class CompletedState extends AbstractBookingState {
    // All methods throw IllegalStateException via AbstractBookingState.
}
