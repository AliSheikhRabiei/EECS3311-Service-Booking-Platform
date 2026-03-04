package com.platform.payment;

import com.platform.domain.Client;

/**
 * Bank transfer payment method.
 * Validation: account number 8–17 digits, routing number exactly 9 digits.
 */
public class BankTransferMethod extends PaymentMethod {

    private final String accountNumber;
    private final String routingNumber;

    public BankTransferMethod(Client owner, String methodId,
                               String accountNumber, String routingNumber) {
        super(owner, methodId);
        this.accountNumber = accountNumber;
        this.routingNumber = routingNumber;
    }

    @Override
    public boolean validate() {
        if (accountNumber == null || !accountNumber.matches("\\d{8,17}")) return false;
        if (routingNumber  == null || !routingNumber.matches("\\d{9}"))   return false;
        return true;
    }

    @Override public String getMethodType() { return "BANK_TRANSFER"; }

    @Override
    public String toString() {
        String masked = "****" + accountNumber.substring(Math.max(0, accountNumber.length() - 4));
        return "BankTransfer[acct=" + masked + ", routing=" + routingNumber + "]";
    }
}
