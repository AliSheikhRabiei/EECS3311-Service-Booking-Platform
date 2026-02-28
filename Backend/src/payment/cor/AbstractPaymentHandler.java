package payment.cor;

import model.Booking;
import payment.PaymentMethod;

public abstract class AbstractPaymentHandler implements PaymentHandler {
    protected PaymentHandler next;

    public void setNext(PaymentHandler next) {
        this.next = next;
    }

    protected void handleNext(Booking booking, PaymentMethod method) throws Exception {
        if (next != null) {
            next.handle(booking, method);
        }
    }
}
