package payment.cor;

import model.Booking;
import payment.PaymentMethod;

public interface PaymentHandler {
    void handle(Booking booking, PaymentMethod method) throws Exception;
}
