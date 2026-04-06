package com.platform.payment;

import com.platform.domain.Client;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Debit card payment method.
 * Newly entered methods require a 16-digit card number and future expiry MM/yy.
 */
public class DebitCardMethod extends PaymentMethod {

    private static final DateTimeFormatter EXPIRY_FORMAT = DateTimeFormatter.ofPattern("MM/yy");

    private final String cardNumber;
    private final String expiry;
    private final boolean storedMasked;

    public DebitCardMethod(Client owner, String methodId,
                           String cardNumber, String expiry) {
        this(owner, methodId, cardNumber, expiry, false);
    }

    private DebitCardMethod(Client owner, String methodId,
                            String cardNumber, String expiry,
                            boolean storedMasked) {
        super(owner, methodId);
        this.cardNumber = cardNumber;
        this.expiry = expiry;
        this.storedMasked = storedMasked;
    }

    public static DebitCardMethod fromStored(Client owner, String methodId,
                                             String maskedCardNumber, String expiry) {
        return new DebitCardMethod(owner, methodId, maskedCardNumber, expiry, true);
    }

    @Override
    public boolean validate() {
        if (!isValidExpiry(expiry)) return false;
        if (storedMasked) {
            return cardNumber != null && cardNumber.matches("\\*{8,16}\\d{4}");
        }
        return cardNumber != null && cardNumber.matches("\\d{16}");
    }

    @Override
    public String getMethodType() {
        return "DEBIT_CARD";
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public String getExpiry() {
        return expiry;
    }

    @Override
    public String toString() {
        return "DebitCard[" + maskCardNumber(cardNumber) + ", exp=" + expiry + "]";
    }

    private boolean isValidExpiry(String value) {
        try {
            YearMonth exp = YearMonth.parse(value, EXPIRY_FORMAT);
            return exp.isAfter(YearMonth.now());
        } catch (DateTimeParseException | NullPointerException e) {
            return false;
        }
    }

    private String maskCardNumber(String value) {
        if (value == null || value.isBlank()) return "****";
        if (value.contains("*")) return value;
        if (value.length() < 4) return "****";
        return "************" + value.substring(value.length() - 4);
    }
}
