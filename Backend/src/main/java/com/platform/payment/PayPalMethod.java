package com.platform.payment;

import com.platform.domain.Client;

/**
 * PayPal payment method.
 * Newly entered methods require a valid email-like value.
 */
public class PayPalMethod extends PaymentMethod {

    private final String email;
    private final boolean storedMasked;

    public PayPalMethod(Client owner, String methodId, String email) {
        this(owner, methodId, email, false);
    }

    private PayPalMethod(Client owner, String methodId, String email, boolean storedMasked) {
        super(owner, methodId);
        this.email = email;
        this.storedMasked = storedMasked;
    }

    public static PayPalMethod fromStored(Client owner, String methodId, String maskedEmail) {
        return new PayPalMethod(owner, methodId, maskedEmail, true);
    }

    @Override
    public boolean validate() {
        if (storedMasked) {
            return email != null
                    && email.contains("@")
                    && email.contains("*")
                    && email.indexOf('@') > 0
                    && email.lastIndexOf('.') > email.indexOf('@');
        }
        return email != null
                && email.contains("@")
                && email.contains(".")
                && email.indexOf('@') < email.lastIndexOf('.');
    }

    @Override
    public String getMethodType() {
        return "PAYPAL";
    }

    public String getEmail() {
        return email;
    }

    @Override
    public String toString() {
        return "PayPal[" + maskEmail(email) + "]";
    }

    private String maskEmail(String value) {
        if (value == null || value.isBlank()) return "***@***";
        if (value.contains("*")) return value;

        int at = value.indexOf('@');
        if (at <= 0) return "***@***";

        String local = value.substring(0, at);
        String domain = value.substring(at + 1);
        String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return visible + "***@" + domain;
    }
}
