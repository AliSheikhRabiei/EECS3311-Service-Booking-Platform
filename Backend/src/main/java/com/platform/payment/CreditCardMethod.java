package com.platform.payment;

import com.platform.domain.Client;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Credit card payment method.
 * Validation rules for newly entered methods: 16-digit card number, future expiry (MM/yy),
 * and 3-4 digit CVV.
 */
public class CreditCardMethod extends PaymentMethod {

    private static final DateTimeFormatter EXPIRY_FORMAT = DateTimeFormatter.ofPattern("MM/yy");

    private final String cardNumber;
    private final String expiry;
    private final String cvv;
    private final boolean storedMasked;

    public CreditCardMethod(Client owner, String methodId,
                            String cardNumber, String expiry, String cvv) {
        this(owner, methodId, cardNumber, expiry, cvv, false);
    }

    private CreditCardMethod(Client owner, String methodId,
                             String cardNumber, String expiry, String cvv,
                             boolean storedMasked) {
        super(owner, methodId);
        this.cardNumber = cardNumber;
        this.expiry = expiry;
        this.cvv = cvv;
        this.storedMasked = storedMasked;
    }

    public static CreditCardMethod fromStored(Client owner, String methodId,
                                              String maskedCardNumber, String expiry) {
        return new CreditCardMethod(owner, methodId, maskedCardNumber, expiry, null, true);
    }

    @Override
    public boolean validate() {
        if (!isValidExpiry(expiry)) return false;

        if (storedMasked) {
            return isMaskedCardNumber(cardNumber);
        }

        if (cardNumber == null || !cardNumber.matches("\\d{16}")) return false;
        return cvv != null && cvv.matches("\\d{3,4}");
    }

    @Override
    public String getMethodType() {
        return "CREDIT_CARD";
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public String getExpiry() {
        return expiry;
    }

    public String getCvv() {
        return cvv;
    }

    @Override
    public String toString() {
        return "CreditCard[" + maskCardNumber(cardNumber) + ", exp=" + expiry + "]";
    }

    private boolean isValidExpiry(String value) {
        try {
            YearMonth exp = YearMonth.parse(value, EXPIRY_FORMAT);
            return exp.isAfter(YearMonth.now());
        } catch (DateTimeParseException | NullPointerException e) {
            return false;
        }
    }

    private boolean isMaskedCardNumber(String value) {
        return value != null && value.matches("\\*{8,16}\\d{4}");
    }

    private String maskCardNumber(String value) {
        if (value == null || value.isBlank()) return "****";
        if (value.contains("*")) return value;
        if (value.length() < 4) return "****";
        return "************" + value.substring(value.length() - 4);
    }
}
