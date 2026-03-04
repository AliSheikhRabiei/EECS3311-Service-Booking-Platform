package payment;

import model.Client;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class DebitCardMethod extends PaymentMethod {
    private String cardNumber;
    private String expiry;
    private String cvv;

    public DebitCardMethod(Client owner, String methodId,
                            String cardNumber, String expiry, String cvv) {
        super(owner, methodId);
        this.cardNumber = cardNumber;
        this.expiry     = expiry;
        this.cvv        = cvv;
    }

    @Override
    public boolean validate() {
        if (cardNumber == null || !cardNumber.matches("\\d{16}")) return false;
        if (cvv == null || !cvv.matches("\\d{3,4}")) return false;
        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/yy");
            YearMonth exp = YearMonth.parse(expiry, fmt);
            if (!exp.isAfter(YearMonth.now())) return false;
        } catch (DateTimeParseException e) {
            return false;
        }
        return true;
    }

    @Override public String getMethodType() { return "DEBIT_CARD"; }

    @Override
    public String toString() {
        return "DebitCard[**** **** **** " + cardNumber.substring(12) + ", exp=" + expiry + "]";
    }
}
