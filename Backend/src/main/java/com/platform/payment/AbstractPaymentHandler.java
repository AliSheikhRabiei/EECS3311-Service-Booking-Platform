package com.platform.payment;

import com.platform.domain.Booking;

/**
 * Abstract base for CoR payment handlers.
 * Subclasses call {@link #passToNext(Booking, PaymentMethod)} to forward
 * the request along the chain, or return a result directly.
 */
public abstract class AbstractPaymentHandler implements PaymentHandler {

    protected PaymentHandler next;

    /**
     * Sets the next handler in the chain and returns it (fluent API).
     *
     * @param h the next handler
     * @return h (so handlers can be chained: a.setNext(b).setNext(c) would set b→c, not a→b→c;
     *           for clarity prefer the explicit wiring pattern in PaymentService)
     */
    public PaymentHandler setNext(PaymentHandler h) {
        this.next = h;
        return h;
    }

    /**
     * Forwards to the next handler if one exists; otherwise returns a default
     * null-safe empty result. Subclasses should call this after their own logic.
     */
    protected PaymentTransaction passToNext(Booking booking, PaymentMethod method) {
        if (next != null) {
            return next.handle(booking, method);
        }
        return null;
    }
}
