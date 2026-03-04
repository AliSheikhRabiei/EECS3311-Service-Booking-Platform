package payment;

import model.Client;

public abstract class PaymentMethod {
    protected Client owner;
    protected String methodId;

    public PaymentMethod(Client owner, String methodId) {
        this.owner    = owner;
        this.methodId = methodId;
    }

    public abstract boolean validate();
    public abstract String  getMethodType();

    public Client getOwner()    { return owner; }
    public String getMethodId() { return methodId; }
}
