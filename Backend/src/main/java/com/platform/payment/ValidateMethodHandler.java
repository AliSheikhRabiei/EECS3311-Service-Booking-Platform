package com.platform.payment;

import com.platform.domain.Booking;

import java.time.LocalDateTime;

/**
 * First CoR handler: validates the payment method's details.
 * If validation fails, returns a FAILED transaction immediately without
 * proceeding to the next handler.
 */
public class ValidateMethodHandler extends AbstractPaymentHandler {

    @Override
    public PaymentTransaction handle(Booking booking, PaymentMethod method) {
        System.out.println("[CoR:Validate] Validating " + method.getMethodType()
                + " (id=" + method.getMethodId() + ")");

        if (!method.validate()) {
            System.out.println("[CoR:Validate] FAILED – invalid payment method details.");
            return new PaymentTransaction(
                    "TX-INVALID-" + System.currentTimeMillis(),
                    booking,
                    booking.getService().getPrice(),
                    PaymentStatus.FAILED,
                    LocalDateTime.now()
            );
        }

        System.out.println("[CoR:Validate] Payment method is valid.");
        return passToNext(booking, method);
    }
}
