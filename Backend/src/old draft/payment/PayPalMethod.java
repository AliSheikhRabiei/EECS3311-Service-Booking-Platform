package payment;

import model.Client;

public class PayPalMethod extends PaymentMethod {
    private String email;

    public PayPalMethod(Client owner, String methodId, String email) {
        super(owner, methodId);
        this.email = email;
    }

    @Override
    public boolean validate() {
        return email != null && email.contains("@") && email.contains(".");
    }

    @Override public String getMethodType() { return "PAYPAL"; }
    public String getEmail() { return email; }

    @Override
    public String toString() { return "PayPal[" + email + "]"; }
}
