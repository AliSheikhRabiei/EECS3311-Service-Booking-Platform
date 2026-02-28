package payment.cor;

import model.Booking;
import payment.PaymentMethod;

public class ValidateMethodHandler extends AbstractPaymentHandler {

    @Override
    public void handle(Booking booking, PaymentMethod method) throws Exception {
        System.out.println("[CoR] Validating payment method: " + method.getMethodType());
        if (!method.validate()) {
            throw new IllegalArgumentException(
                    "Invalid payment method details for: " + method.getMethodType()
                    + " (id=" + method.getMethodId() + ")");
        }
        System.out.println("[CoR] Payment method is valid.");
        handleNext(booking, method);
    }
}
