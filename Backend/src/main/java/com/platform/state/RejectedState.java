package com.platform.state;

import com.platform.domain.Booking;
import com.platform.payment.PaymentTransaction;

/**
 * Terminal state: consultant declined the booking.
 * No further transitions are permitted (default-deny from AbstractBookingState).
 */
public class RejectedState extends AbstractBookingState {
    // All methods throw IllegalStateException via AbstractBookingState.
}
