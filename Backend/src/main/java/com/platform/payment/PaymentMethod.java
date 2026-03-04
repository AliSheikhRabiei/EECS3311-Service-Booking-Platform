package com.platform.payment;

import com.platform.domain.Client;

/**
 * Abstract base for all payment methods (UC5, UC6).
 * Each subclass implements {@link #validate()} with method-specific rules.
 */
public abstract class PaymentMethod {

    protected final Client owner;
    protected final String methodId;

    protected PaymentMethod(Client owner, String methodId) {
        if (owner    == null) throw new IllegalArgumentException("Owner must not be null.");
        if (methodId == null || methodId.isBlank())
            throw new IllegalArgumentException("methodId must not be blank.");
        this.owner    = owner;
        this.methodId = methodId;
    }

    /**
     * Validates this payment method's details.
     * @return {@code true} if details are valid; {@code false} otherwise.
     */
    public abstract boolean validate();

    /** Returns a display-friendly type name (e.g., "CREDIT_CARD"). */
    public abstract String getMethodType();

    public Client getOwner()    { return owner; }
    public String getMethodId() { return methodId; }
}
