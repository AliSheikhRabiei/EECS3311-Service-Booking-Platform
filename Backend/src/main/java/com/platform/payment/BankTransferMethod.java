package com.platform.payment;

import com.platform.domain.Client;

/**
 * Bank transfer payment method.
 * Newly entered methods require an 8-17 digit account number and 9-digit routing number.
 */
public class BankTransferMethod extends PaymentMethod {

    private final String accountNumber;
    private final String routingNumber;
    private final boolean storedMasked;

    public BankTransferMethod(Client owner, String methodId,
                              String accountNumber, String routingNumber) {
        this(owner, methodId, accountNumber, routingNumber, false);
    }

    private BankTransferMethod(Client owner, String methodId,
                               String accountNumber, String routingNumber,
                               boolean storedMasked) {
        super(owner, methodId);
        this.accountNumber = accountNumber;
        this.routingNumber = routingNumber;
        this.storedMasked = storedMasked;
    }

    public static BankTransferMethod fromStored(Client owner, String methodId,
                                                String maskedAccountNumber,
                                                String maskedRoutingNumber) {
        return new BankTransferMethod(owner, methodId, maskedAccountNumber, maskedRoutingNumber, true);
    }

    @Override
    public boolean validate() {
        if (storedMasked) {
            return isMaskedDigits(accountNumber) && isMaskedDigits(routingNumber);
        }
        if (accountNumber == null || !accountNumber.matches("\\d{8,17}")) return false;
        return routingNumber != null && routingNumber.matches("\\d{9}");
    }

    @Override
    public String getMethodType() {
        return "BANK_TRANSFER";
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getRoutingNumber() {
        return routingNumber;
    }

    @Override
    public String toString() {
        return "BankTransfer[acct=" + maskDigits(accountNumber) + ", routing=" + maskDigits(routingNumber) + "]";
    }

    private boolean isMaskedDigits(String value) {
        return value != null && value.matches("\\*{4,20}\\d{4}");
    }

    private String maskDigits(String value) {
        if (value == null || value.isBlank()) return "****";
        if (value.contains("*")) return value;
        if (value.length() < 4) return "****";
        return "****" + value.substring(value.length() - 4);
    }
}
