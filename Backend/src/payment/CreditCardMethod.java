package payment;

import model.Client;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class CreditCardMethod extends PaymentMethod {
    private String cardNumber;
    private String expiry;   // MM/yy
    private String cvv;

    public CreditCardMethod(Client owner, String methodId,
                             String cardNumber, String expiry, String cvv) {
        super(owner, methodId);
        this.cardNumber = cardNumber;
        this.expiry     = expiry;
        this.cvv        = cvv;
    }

    @Override
    public boolean validate() {
        // 16-digit card number
        if (cardNumber == null || !cardNumber.matches("\\d{16}")) return false;
        // CVV 3-4 digits
        if (cvv == null || !cvv.matches("\\d{3,4}")) return false;
        // Expiry must be a future month
        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/yy");
            YearMonth exp = YearMonth.parse(expiry, fmt);
            if (!exp.isAfter(YearMonth.now())) return false;
        } catch (DateTimeParseException e) {
            return false;
        }
        return true;
    }

    @Override public String getMethodType() { return "CREDIT_CARD"; }

    public String getCardNumber() { return "**** **** **** " + cardNumber.substring(12); }
    public String getExpiry()     { return expiry; }

    @Override
    public String toString() {
        return "CreditCard[" + getCardNumber() + ", exp=" + expiry + "]";
    }
}
