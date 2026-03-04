package payment;

import model.Client;

public class BankTransferMethod extends PaymentMethod {
    private String accountNumber;
    private String routingNumber;

    public BankTransferMethod(Client owner, String methodId,
                               String accountNumber, String routingNumber) {
        super(owner, methodId);
        this.accountNumber = accountNumber;
        this.routingNumber = routingNumber;
    }

    @Override
    public boolean validate() {
        // Account: 8-17 digits, routing: exactly 9 digits
        if (accountNumber == null || !accountNumber.matches("\\d{8,17}")) return false;
        if (routingNumber == null || !routingNumber.matches("\\d{9}"))    return false;
        return true;
    }

    @Override public String getMethodType() { return "BANK_TRANSFER"; }

    @Override
    public String toString() {
        return "BankTransfer[acct=****" + accountNumber.substring(Math.max(0, accountNumber.length()-4))
                + ", routing=" + routingNumber + "]";
    }
}
