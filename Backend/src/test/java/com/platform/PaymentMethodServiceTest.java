package com.platform;

import com.platform.domain.Client;
import com.platform.payment.*;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests PaymentMethodService: add, remove, list, update, find.
 */
@DisplayName("PaymentMethodService Tests")
class PaymentMethodServiceTest {

    private PaymentMethodService service;
    private Client               client1, client2;
    private CreditCardMethod     cc1, cc2;
    private PayPalMethod         pp;

    @BeforeEach
    void setUp() {
        service  = new PaymentMethodService();
        client1  = new Client("U1", "Alice", "alice@test.com");
        client2  = new Client("U2", "Bob",   "bob@test.com");
        cc1      = new CreditCardMethod(client1, "PM-CC1", "1234567890123456", "12/27", "123");
        cc2      = new CreditCardMethod(client1, "PM-CC2", "9876543210987654", "06/28", "456");
        pp       = new PayPalMethod(client1, "PM-PP1", "alice@paypal.com");
    }

    // ── addMethod / listMethods ───────────────────────────────────────────────

    @Test @DisplayName("addMethod: single method shows in listMethods")
    void addSingleMethod() {
        service.addMethod(client1, cc1);
        List<PaymentMethod> methods = service.listMethods(client1);
        assertEquals(1, methods.size());
        assertSame(cc1, methods.get(0));
    }

    @Test @DisplayName("addMethod: multiple methods for same client are all listed")
    void addMultipleMethods() {
        service.addMethod(client1, cc1);
        service.addMethod(client1, cc2);
        service.addMethod(client1, pp);
        assertEquals(3, service.listMethods(client1).size());
    }

    @Test @DisplayName("listMethods: different clients have independent lists")
    void listMethodsIsolatedPerClient() {
        BankTransferMethod bt = new BankTransferMethod(client2, "PM-BT1", "1234567890", "021000021");
        service.addMethod(client1, cc1);
        service.addMethod(client2, bt);

        List<PaymentMethod> c1Methods = service.listMethods(client1);
        List<PaymentMethod> c2Methods = service.listMethods(client2);

        assertEquals(1, c1Methods.size());
        assertEquals(1, c2Methods.size());
        assertSame(cc1, c1Methods.get(0));
        assertSame(bt,  c2Methods.get(0));
    }

    @Test @DisplayName("listMethods: returns empty list for client with no methods")
    void listMethodsEmptyForNewClient() {
        assertTrue(service.listMethods(client1).isEmpty());
    }

    @Test @DisplayName("listMethods: returns empty list for null client")
    void listMethodsNullClientEmpty() {
        service.addMethod(client1, cc1);
        assertTrue(service.listMethods(null).isEmpty());
    }

    @Test @DisplayName("listMethods: returned list is unmodifiable")
    void listMethodsIsUnmodifiable() {
        service.addMethod(client1, cc1);
        assertThrows(UnsupportedOperationException.class,
                () -> service.listMethods(client1).clear());
    }

    // ── removeMethod ──────────────────────────────────────────────────────────

    @Test @DisplayName("removeMethod: removes the correct method by id")
    void removeMethod() {
        service.addMethod(client1, cc1);
        service.addMethod(client1, cc2);
        service.removeMethod(client1, "PM-CC1");
        List<PaymentMethod> remaining = service.listMethods(client1);
        assertEquals(1, remaining.size());
        assertSame(cc2, remaining.get(0));
    }

    @Test @DisplayName("removeMethod: removing non-existent id does not throw")
    void removeMethodUnknownIdNoThrow() {
        service.addMethod(client1, cc1);
        assertDoesNotThrow(() -> service.removeMethod(client1, "UNKNOWN-ID"));
        assertEquals(1, service.listMethods(client1).size());
    }

    @Test @DisplayName("removeMethod: null client or id does not throw")
    void removeMethodNullSafe() {
        service.addMethod(client1, cc1);
        assertDoesNotThrow(() -> service.removeMethod(null, "PM-CC1"));
        assertDoesNotThrow(() -> service.removeMethod(client1, null));
    }

    @Test @DisplayName("removeMethod: removing all methods leaves empty list")
    void removeAllMethodsLeavesEmpty() {
        service.addMethod(client1, cc1);
        service.removeMethod(client1, "PM-CC1");
        assertTrue(service.listMethods(client1).isEmpty());
    }

    // ── updateMethod ──────────────────────────────────────────────────────────

    @Test @DisplayName("updateMethod: replaces the method with the same id")
    void updateMethod() {
        service.addMethod(client1, cc1);
        CreditCardMethod updated = new CreditCardMethod(client1, "PM-CC1",
                "4111111111111111", "01/30", "999");
        service.updateMethod(client1, updated);
        List<PaymentMethod> methods = service.listMethods(client1);
        assertEquals(1, methods.size());
        assertSame(updated, methods.get(0));
    }

    // ── findMethod ────────────────────────────────────────────────────────────

    @Test @DisplayName("findMethod: returns the method matching the id")
    void findMethod() {
        service.addMethod(client1, cc1);
        service.addMethod(client1, cc2);
        PaymentMethod found = service.findMethod(client1, "PM-CC2");
        assertSame(cc2, found);
    }

    @Test @DisplayName("findMethod: returns null when method not found")
    void findMethodNotFound() {
        service.addMethod(client1, cc1);
        assertNull(service.findMethod(client1, "NOT-EXIST"));
    }

    @Test @DisplayName("findMethod: returns null for null client")
    void findMethodNullClient() {
        service.addMethod(client1, cc1);
        assertNull(service.findMethod(null, "PM-CC1"));
    }

    // ── addMethod null guards ─────────────────────────────────────────────────

    @Test @DisplayName("addMethod: null client and method do not throw (graceful skip)")
    void addMethodNullSafe() {
        assertDoesNotThrow(() -> service.addMethod(null, cc1));
        assertDoesNotThrow(() -> service.addMethod(client1, null));
    }
}
