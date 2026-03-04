package com.platform.payment;

import com.platform.domain.Client;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Debit card payment method.
 * Same validation rules as credit card (16-digit number, future expiry MM/yy).
 * No CVV required for debit in this simulation.
 */
public class DebitCardMethod extends PaymentMethod {

    private final String cardNumber;
    private final String expiry;

    public DebitCardMethod(Client owner, String methodId,
                            String cardNumber, String expiry) {
        super(owner, methodId);
        this.cardNumber = cardNumber;
        this.expiry     = expiry;
    }

    @Override
    public boolean validate() {
        if (cardNumber == null || !cardNumber.matches("\\d{16}")) return false;
        try {
            YearMonth exp = YearMonth.parse(expiry, DateTimeFormatter.ofPattern("MM/yy"));
            if (!exp.isAfter(YearMonth.now())) return false;
        } catch (DateTimeParseException | NullPointerException e) {
            return false;
        }
        return true;
    }

    @Override
    public String getMethodType() { return "DEBIT_CARD"; }

    @Override
    public String toString() {
        return "DebitCard[****" + cardNumber.substring(12) + ", exp=" + expiry + "]";
    }
}
