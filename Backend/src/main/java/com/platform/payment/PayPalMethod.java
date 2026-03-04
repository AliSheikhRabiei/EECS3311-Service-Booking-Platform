package com.platform.payment;

import com.platform.domain.Client;

/**
 * PayPal payment method.
 * Validation: email must contain '@' and at least one '.'.
 */
public class PayPalMethod extends PaymentMethod {

    private final String email;

    public PayPalMethod(Client owner, String methodId, String email) {
        super(owner, methodId);
        this.email = email;
    }

    @Override
    public boolean validate() {
        return email != null
                && email.contains("@")
                && email.contains(".")
                && email.indexOf('@') < email.lastIndexOf('.');
    }

    @Override public String getMethodType() { return "PAYPAL"; }
    public  String getEmail()               { return email; }

    @Override
    public String toString() { return "PayPal[" + email + "]"; }
}
