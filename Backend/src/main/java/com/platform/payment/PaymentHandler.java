package com.platform.payment;

import com.platform.domain.Booking;

/**
 * Chain of Responsibility interface for payment processing.
 * Chain order: ValidateMethodHandler → ProcessPaymentHandler → MarkPaidHandler.
 */
public interface PaymentHandler {
    PaymentTransaction handle(Booking booking, PaymentMethod method);
}
