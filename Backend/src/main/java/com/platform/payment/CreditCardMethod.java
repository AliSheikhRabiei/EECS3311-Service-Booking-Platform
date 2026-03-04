package com.platform.payment;

import com.platform.domain.Client;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Credit card payment method.
 * Validation rules: 16-digit card number, future expiry (MM/yy), 3-4 digit CVV.
 */
public class CreditCardMethod extends PaymentMethod {

    private final String cardNumber;
    private final String expiry;   // expected format: MM/yy
    private final String cvv;

    public CreditCardMethod(Client owner, String methodId,
                             String cardNumber, String expiry, String cvv) {
        super(owner, methodId);
        this.cardNumber = cardNumber;
        this.expiry     = expiry;
        this.cvv        = cvv;
    }

    @Override
    public boolean validate() {
        // Rule 1: card number must be exactly 16 digits
        if (cardNumber == null || !cardNumber.matches("\\d{16}")) return false;

        // Rule 2: CVV must be 3 or 4 digits
        if (cvv == null || !cvv.matches("\\d{3,4}")) return false;

        // Rule 3: expiry must be a valid MM/yy date in the future
        try {
            YearMonth exp = YearMonth.parse(expiry, DateTimeFormatter.ofPattern("MM/yy"));
            if (!exp.isAfter(YearMonth.now())) return false;
        } catch (DateTimeParseException | NullPointerException e) {
            return false;
        }

        return true;
    }

    @Override
    public String getMethodType() { return "CREDIT_CARD"; }

    @Override
    public String toString() {
        return "CreditCard[****" + cardNumber.substring(12) + ", exp=" + expiry + "]";
    }
}
