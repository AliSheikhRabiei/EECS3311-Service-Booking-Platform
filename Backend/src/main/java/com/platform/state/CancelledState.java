package com.platform.state;

import com.platform.domain.Booking;
import com.platform.payment.PaymentTransaction;

/**
 * Terminal state: booking was cancelled.
 * No further transitions are permitted.
 */
public class CancelledState extends AbstractBookingState {
    // All methods throw IllegalStateException via AbstractBookingState.
}
