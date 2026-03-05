package com.platform;

import com.platform.domain.Client;
import com.platform.payment.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests every validation rule for all four payment method types.
 * CreditCardMethod, DebitCardMethod, PayPalMethod, BankTransferMethod.
 */
@DisplayName("Payment Method Validation Tests")
class PaymentMethodValidationTest {

    private final Client client = new Client("U1", "Alice", "alice@test.com");

    // ══════════════════════════════════════════════════════════════════════════
    // CreditCardMethod
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("CreditCard: valid card passes validation")
    void creditCardValid() {
        CreditCardMethod cc = new CreditCardMethod(client, "PM1",
                "1234567890123456", "12/27", "123");
        assertTrue(cc.validate());
    }

    @Test @DisplayName("CreditCard: valid 4-digit CVV passes")
    void creditCardValidFourDigitCvv() {
        CreditCardMethod cc = new CreditCardMethod(client, "PM1",
                "1234567890123456", "12/27", "1234");
        assertTrue(cc.validate());
    }

    @Test @DisplayName("CreditCard: 15-digit card number fails")
    void creditCardFifteenDigitsFails() {
        CreditCardMethod cc = new CreditCardMethod(client, "PM1",
                "123456789012345", "12/27", "123");
        assertFalse(cc.validate());
    }

    @Test @DisplayName("CreditCard: 17-digit card number fails")
    void creditCardSeventeenDigitsFails() {
        CreditCardMethod cc = new CreditCardMethod(client, "PM1",
                "12345678901234567", "12/27", "123");
        assertFalse(cc.validate());
    }

    @Test @DisplayName("CreditCard: card number with letters fails")
    void creditCardLettersFails() {
        CreditCardMethod cc = new CreditCardMethod(client, "PM1",
                "1234ABCD90123456", "12/27", "123");
        assertFalse(cc.validate());
    }

    @Test @DisplayName("CreditCard: past expiry date fails")
    void creditCardPastExpiryFails() {
        CreditCardMethod cc = new CreditCardMethod(client, "PM1",
                "1234567890123456", "01/20", "123");
        assertFalse(cc.validate());
    }

    @Test @DisplayName("CreditCard: malformed expiry MM/yyyy fails")
    void creditCardWrongExpiryFormatFails() {
        CreditCardMethod cc = new CreditCardMethod(client, "PM1",
                "1234567890123456", "12/2027", "123");
        assertFalse(cc.validate());
    }

    @Test @DisplayName("CreditCard: null expiry fails")
    void creditCardNullExpiryFails() {
        CreditCardMethod cc = new CreditCardMethod(client, "PM1",
                "1234567890123456", null, "123");
        assertFalse(cc.validate());
    }

    @Test @DisplayName("CreditCard: 2-digit CVV fails")
    void creditCardTwoDigitCvvFails() {
        CreditCardMethod cc = new CreditCardMethod(client, "PM1",
                "1234567890123456", "12/27", "12");
        assertFalse(cc.validate());
    }

    @Test @DisplayName("CreditCard: 5-digit CVV fails")
    void creditCardFiveDigitCvvFails() {
        CreditCardMethod cc = new CreditCardMethod(client, "PM1",
                "1234567890123456", "12/27", "12345");
        assertFalse(cc.validate());
    }

    @Test @DisplayName("CreditCard: null CVV fails")
    void creditCardNullCvvFails() {
        CreditCardMethod cc = new CreditCardMethod(client, "PM1",
                "1234567890123456", "12/27", null);
        assertFalse(cc.validate());
    }

    @Test @DisplayName("CreditCard: null card number fails")
    void creditCardNullNumberFails() {
        CreditCardMethod cc = new CreditCardMethod(client, "PM1",
                null, "12/27", "123");
        assertFalse(cc.validate());
    }

    @Test @DisplayName("CreditCard: getMethodType returns CREDIT_CARD")
    void creditCardMethodType() {
        CreditCardMethod cc = new CreditCardMethod(client, "PM1",
                "1234567890123456", "12/27", "123");
        assertEquals("CREDIT_CARD", cc.getMethodType());
    }

    @Test @DisplayName("CreditCard: getMethodId and getOwner are correct")
    void creditCardGetters() {
        CreditCardMethod cc = new CreditCardMethod(client, "PM-CC1",
                "1234567890123456", "12/27", "123");
        assertEquals("PM-CC1", cc.getMethodId());
        assertSame(client,     cc.getOwner());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DebitCardMethod
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("DebitCard: valid card passes")
    void debitCardValid() {
        DebitCardMethod dc = new DebitCardMethod(client, "PM2",
                "9876543210987654", "11/26");
        assertTrue(dc.validate());
    }

    @Test @DisplayName("DebitCard: 15-digit number fails")
    void debitCardShortNumberFails() {
        DebitCardMethod dc = new DebitCardMethod(client, "PM2",
                "987654321098765", "11/26");
        assertFalse(dc.validate());
    }

    @Test @DisplayName("DebitCard: past expiry fails")
    void debitCardPastExpiryFails() {
        DebitCardMethod dc = new DebitCardMethod(client, "PM2",
                "9876543210987654", "01/19");
        assertFalse(dc.validate());
    }

    @Test @DisplayName("DebitCard: null number fails")
    void debitCardNullNumberFails() {
        DebitCardMethod dc = new DebitCardMethod(client, "PM2",
                null, "11/26");
        assertFalse(dc.validate());
    }

    @Test @DisplayName("DebitCard: getMethodType returns DEBIT_CARD")
    void debitCardMethodType() {
        DebitCardMethod dc = new DebitCardMethod(client, "PM2",
                "9876543210987654", "11/26");
        assertEquals("DEBIT_CARD", dc.getMethodType());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PayPalMethod
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("PayPal: valid email passes")
    void paypalValid() {
        PayPalMethod pp = new PayPalMethod(client, "PM3", "user@example.com");
        assertTrue(pp.validate());
    }

    @Test @DisplayName("PayPal: email with subdomain passes")
    void paypalSubdomainValid() {
        PayPalMethod pp = new PayPalMethod(client, "PM3", "user@mail.example.com");
        assertTrue(pp.validate());
    }

    @Test @DisplayName("PayPal: email without @ fails")
    void paypalNoAtFails() {
        PayPalMethod pp = new PayPalMethod(client, "PM3", "userexample.com");
        assertFalse(pp.validate());
    }

    @Test @DisplayName("PayPal: email without . fails")
    void paypalNoDotFails() {
        PayPalMethod pp = new PayPalMethod(client, "PM3", "user@examplecom");
        assertFalse(pp.validate());
    }

    @Test @DisplayName("PayPal: dot before @ fails (invalid order)")
    void paypalDotBeforeAtFails() {
        PayPalMethod pp = new PayPalMethod(client, "PM3", "user.name@examplecom");
        assertFalse(pp.validate(), "Dot must come after @ to be valid");
    }

    @Test @DisplayName("PayPal: null email fails")
    void paypalNullEmailFails() {
        PayPalMethod pp = new PayPalMethod(client, "PM3", null);
        assertFalse(pp.validate());
    }

    @Test @DisplayName("PayPal: getEmail returns the email")
    void paypalGetEmail() {
        PayPalMethod pp = new PayPalMethod(client, "PM3", "user@example.com");
        assertEquals("user@example.com", pp.getEmail());
    }

    @Test @DisplayName("PayPal: getMethodType returns PAYPAL")
    void paypalMethodType() {
        PayPalMethod pp = new PayPalMethod(client, "PM3", "user@example.com");
        assertEquals("PAYPAL", pp.getMethodType());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BankTransferMethod
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("BankTransfer: valid 10-digit account and 9-digit routing passes")
    void bankTransferValid() {
        BankTransferMethod bt = new BankTransferMethod(client, "PM4",
                "1234567890", "021000021");
        assertTrue(bt.validate());
    }

    @Test @DisplayName("BankTransfer: 8-digit account (minimum) passes")
    void bankTransferMinAccountLength() {
        BankTransferMethod bt = new BankTransferMethod(client, "PM4",
                "12345678", "021000021");
        assertTrue(bt.validate());
    }

    @Test @DisplayName("BankTransfer: 17-digit account (maximum) passes")
    void bankTransferMaxAccountLength() {
        BankTransferMethod bt = new BankTransferMethod(client, "PM4",
                "12345678901234567", "021000021");
        assertTrue(bt.validate());
    }

    @Test @DisplayName("BankTransfer: 7-digit account fails (too short)")
    void bankTransferAccountTooShortFails() {
        BankTransferMethod bt = new BankTransferMethod(client, "PM4",
                "1234567", "021000021");
        assertFalse(bt.validate());
    }

    @Test @DisplayName("BankTransfer: 18-digit account fails (too long)")
    void bankTransferAccountTooLongFails() {
        BankTransferMethod bt = new BankTransferMethod(client, "PM4",
                "123456789012345678", "021000021");
        assertFalse(bt.validate());
    }

    @Test @DisplayName("BankTransfer: 8-digit routing number fails (must be 9)")
    void bankTransferRoutingTooShortFails() {
        BankTransferMethod bt = new BankTransferMethod(client, "PM4",
                "1234567890", "02100002");
        assertFalse(bt.validate());
    }

    @Test @DisplayName("BankTransfer: 10-digit routing number fails (must be 9)")
    void bankTransferRoutingTooLongFails() {
        BankTransferMethod bt = new BankTransferMethod(client, "PM4",
                "1234567890", "0210000210");
        assertFalse(bt.validate());
    }

    @Test @DisplayName("BankTransfer: letters in account number fail")
    void bankTransferLettersInAccountFail() {
        BankTransferMethod bt = new BankTransferMethod(client, "PM4",
                "12345ABCDE", "021000021");
        assertFalse(bt.validate());
    }

    @Test @DisplayName("BankTransfer: null account number fails")
    void bankTransferNullAccountFails() {
        BankTransferMethod bt = new BankTransferMethod(client, "PM4",
                null, "021000021");
        assertFalse(bt.validate());
    }

    @Test @DisplayName("BankTransfer: null routing number fails")
    void bankTransferNullRoutingFails() {
        BankTransferMethod bt = new BankTransferMethod(client, "PM4",
                "1234567890", null);
        assertFalse(bt.validate());
    }

    @Test @DisplayName("BankTransfer: getMethodType returns BANK_TRANSFER")
    void bankTransferMethodType() {
        BankTransferMethod bt = new BankTransferMethod(client, "PM4",
                "1234567890", "021000021");
        assertEquals("BANK_TRANSFER", bt.getMethodType());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PaymentMethod abstract — constructor guard
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("PaymentMethod: null owner throws")
    void paymentMethodNullOwnerThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new CreditCardMethod(null, "PM1", "1234567890123456", "12/27", "123"));
    }

    @Test @DisplayName("PaymentMethod: blank methodId throws")
    void paymentMethodBlankIdThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new CreditCardMethod(client, "", "1234567890123456", "12/27", "123"));
        assertThrows(IllegalArgumentException.class,
                () -> new CreditCardMethod(client, null, "1234567890123456", "12/27", "123"));
    }
}
