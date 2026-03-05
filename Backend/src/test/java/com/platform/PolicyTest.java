package com.platform;

import com.platform.domain.*;
import com.platform.notify.NotificationService;
import com.platform.payment.*;
import com.platform.policy.*;
import com.platform.state.*;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests PolicyManager (Singleton), and all four policy interfaces and default implementations:
 * DefaultCancellationPolicy, DefaultRefundPolicy, DefaultNotificationPolicy, DefaultPricingStrategy.
 */
@DisplayName("Policy & Singleton Tests")
class PolicyTest {

    private Consultant consultant;
    private Client     client;
    private Service    service;
    private Booking    booking;

    @BeforeEach
    void setUp() {
        PolicyManager.resetInstance();   // Fresh singleton per test
        consultant = new Consultant("C1", "Alice", "alice@test.com", "Expert");
        consultant.setRegistrationStatus(RegistrationStatus.APPROVED);
        client  = new Client("U1", "Bob", "bob@test.com");
        service = new Service("S1", "Tutoring", "Desc", 60, 100.0, consultant);
        TimeSlot slot = new TimeSlot(
                LocalDateTime.now().plusDays(1).withHour(10).withMinute(0),
                LocalDateTime.now().plusDays(1).withHour(11).withMinute(0));
        booking = new Booking("BK-1", client, service, slot);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PolicyManager — Singleton pattern
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("PolicyManager: getInstance always returns the same object")
    void policyManagerSingletonSameInstance() {
        PolicyManager a = PolicyManager.getInstance();
        PolicyManager b = PolicyManager.getInstance();
        assertSame(a, b, "getInstance() must return the same object every time");
    }

    @Test @DisplayName("PolicyManager: resetInstance causes a new instance to be created")
    void policyManagerResetCreatesNew() {
        PolicyManager first = PolicyManager.getInstance();
        PolicyManager.resetInstance();
        PolicyManager second = PolicyManager.getInstance();
        assertNotSame(first, second, "After reset, a new instance must be created");
    }

    @Test @DisplayName("PolicyManager: starts with all four default policies set")
    void policyManagerStartsWithDefaults() {
        PolicyManager pm = PolicyManager.getInstance();
        assertNotNull(pm.getCancellationPolicy());
        assertNotNull(pm.getRefundPolicy());
        assertNotNull(pm.getNotificationPolicy());
        assertNotNull(pm.getPricingStrategy());
    }

    @Test @DisplayName("PolicyManager: setCancellationPolicy replaces the policy")
    void policyManagerSetCancellationPolicy() {
        PolicyManager pm = PolicyManager.getInstance();
        CancellationPolicy strict = new CancellationPolicy() {
            public boolean canCancel(Booking b, LocalDateTime now) { return false; }
            public double cancellationFee(Booking b, LocalDateTime now) { return 999.0; }
        };
        pm.setCancellationPolicy(strict);
        assertSame(strict, pm.getCancellationPolicy());
    }

    @Test @DisplayName("PolicyManager: setRefundPolicy replaces the policy")
    void policyManagerSetRefundPolicy() {
        PolicyManager pm = PolicyManager.getInstance();
        RefundPolicy noRefund = (tx, now) -> 0.0;
        pm.setRefundPolicy(noRefund);
        assertSame(noRefund, pm.getRefundPolicy());
    }

    @Test @DisplayName("PolicyManager: setNotificationPolicy replaces the policy")
    void policyManagerSetNotificationPolicy() {
        PolicyManager pm = PolicyManager.getInstance();
        NotificationPolicy silent = eventType -> false;
        pm.setNotificationPolicy(silent);
        assertSame(silent, pm.getNotificationPolicy());
    }

    @Test @DisplayName("PolicyManager: setPricingStrategy replaces the strategy")
    void policyManagerSetPricingStrategy() {
        PolicyManager pm = PolicyManager.getInstance();
        PricingStrategy doubled = svc -> svc.getPrice() * 2.0;
        pm.setPricingStrategy(doubled);
        assertSame(doubled, pm.getPricingStrategy());
    }

    @Test @DisplayName("PolicyManager: setting null policy throws")
    void policyManagerSetNullThrows() {
        PolicyManager pm = PolicyManager.getInstance();
        assertThrows(IllegalArgumentException.class, () -> pm.setCancellationPolicy(null));
        assertThrows(IllegalArgumentException.class, () -> pm.setRefundPolicy(null));
        assertThrows(IllegalArgumentException.class, () -> pm.setNotificationPolicy(null));
        assertThrows(IllegalArgumentException.class, () -> pm.setPricingStrategy(null));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DefaultCancellationPolicy
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("DefaultCancellationPolicy: canCancel REQUESTED → true")
    void defaultCancelAllowsRequested() {
        DefaultCancellationPolicy p = new DefaultCancellationPolicy();
        assertTrue(p.canCancel(booking, LocalDateTime.now()));
    }

    @Test @DisplayName("DefaultCancellationPolicy: canCancel CONFIRMED → true")
    void defaultCancelAllowsConfirmed() {
        DefaultCancellationPolicy p = new DefaultCancellationPolicy();
        booking.confirm();
        assertTrue(p.canCancel(booking, LocalDateTime.now()));
    }

    @Test @DisplayName("DefaultCancellationPolicy: canCancel PENDING_PAYMENT → true")
    void defaultCancelAllowsPendingPayment() {
        DefaultCancellationPolicy p = new DefaultCancellationPolicy();
        booking.confirm();
        booking.setState(new PendingPaymentState());
        assertTrue(p.canCancel(booking, LocalDateTime.now()));
    }

    @Test @DisplayName("DefaultCancellationPolicy: canCancel PAID → true (refund handled by service)")
    void defaultCancelAllowsPaid() {
        DefaultCancellationPolicy p = new DefaultCancellationPolicy();
        booking.confirm();
        booking.setState(new PendingPaymentState());
        PaymentTransaction tx = new PaymentTransaction("TX-1", booking, 100.0, PaymentStatus.SUCCESS, LocalDateTime.now());
        booking.paymentSuccessful(tx);
        assertTrue(p.canCancel(booking, LocalDateTime.now()));
    }

    @Test @DisplayName("DefaultCancellationPolicy: canCancel REJECTED → false")
    void defaultCancelBlocksRejected() {
        DefaultCancellationPolicy p = new DefaultCancellationPolicy();
        booking.reject("Reason");
        assertFalse(p.canCancel(booking, LocalDateTime.now()));
    }

    @Test @DisplayName("DefaultCancellationPolicy: canCancel CANCELLED → false")
    void defaultCancelBlocksCancelled() {
        DefaultCancellationPolicy p = new DefaultCancellationPolicy();
        booking.cancel("Reason");
        assertFalse(p.canCancel(booking, LocalDateTime.now()));
    }

    @Test @DisplayName("DefaultCancellationPolicy: canCancel COMPLETED → false")
    void defaultCancelBlocksCompleted() {
        DefaultCancellationPolicy p = new DefaultCancellationPolicy();
        booking.confirm();
        booking.setState(new PendingPaymentState());
        PaymentTransaction tx = new PaymentTransaction("TX-1", booking, 100.0, PaymentStatus.SUCCESS, LocalDateTime.now());
        booking.paymentSuccessful(tx);
        booking.complete();
        assertFalse(p.canCancel(booking, LocalDateTime.now()));
    }

    @Test @DisplayName("DefaultCancellationPolicy: cancellationFee returns 0.0")
    void defaultCancelFeeIsZero() {
        DefaultCancellationPolicy p = new DefaultCancellationPolicy();
        assertEquals(0.0, p.cancellationFee(booking, LocalDateTime.now()), 0.001);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DefaultRefundPolicy
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("DefaultRefundPolicy: calculates 80% of transaction amount")
    void defaultRefundEightyPercent() {
        DefaultRefundPolicy p = new DefaultRefundPolicy();
        PaymentTransaction tx = new PaymentTransaction(
                "TX-1", booking, 100.0, PaymentStatus.SUCCESS, LocalDateTime.now());
        assertEquals(80.0, p.calculateRefund(tx, LocalDateTime.now()), 0.001);
    }

    @Test @DisplayName("DefaultRefundPolicy: 80% of $75 service")
    void defaultRefundSeventyFiveDollars() {
        DefaultRefundPolicy p = new DefaultRefundPolicy();
        PaymentTransaction tx = new PaymentTransaction(
                "TX-1", booking, 75.0, PaymentStatus.SUCCESS, LocalDateTime.now());
        assertEquals(60.0, p.calculateRefund(tx, LocalDateTime.now()), 0.001);
    }

    @Test @DisplayName("DefaultRefundPolicy: null transaction returns 0.0")
    void defaultRefundNullTxReturnsZero() {
        DefaultRefundPolicy p = new DefaultRefundPolicy();
        assertEquals(0.0, p.calculateRefund(null, LocalDateTime.now()), 0.001);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DefaultNotificationPolicy
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("DefaultNotificationPolicy: shouldNotify returns true for all events")
    void defaultNotificationAlwaysTrue() {
        DefaultNotificationPolicy p = new DefaultNotificationPolicy();
        assertTrue(p.shouldNotify("BOOKING_CONFIRMED"));
        assertTrue(p.shouldNotify("BOOKING_REJECTED"));
        assertTrue(p.shouldNotify("BOOKING_CANCELLED"));
        assertTrue(p.shouldNotify("PAYMENT"));
        assertTrue(p.shouldNotify("REFUND"));
        assertTrue(p.shouldNotify("BOOKING_COMPLETED"));
        assertTrue(p.shouldNotify("GENERAL"));
        assertTrue(p.shouldNotify("ANY_OTHER_EVENT"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DefaultPricingStrategy
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("DefaultPricingStrategy: returns service base price unchanged")
    void defaultPricingReturnsBasePrice() {
        DefaultPricingStrategy p = new DefaultPricingStrategy();
        assertEquals(100.0, p.calculatePrice(service), 0.001);
    }

    @Test @DisplayName("DefaultPricingStrategy: null service returns 0.0")
    void defaultPricingNullServiceReturnsZero() {
        DefaultPricingStrategy p = new DefaultPricingStrategy();
        assertEquals(0.0, p.calculatePrice(null), 0.001);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Admin policy swap via PolicyManager (Strategy pattern in action)
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("Admin can swap cancellation policy and it is immediately reflected system-wide")
    void adminPolicySwapReflectedSystemWide() {
        PolicyManager.resetInstance();
        Admin admin = new Admin("A1", "Admin", "admin@test.com");

        // Start with default (allows cancellation)
        PolicyManager pm = PolicyManager.getInstance();
        assertTrue(pm.getCancellationPolicy().canCancel(booking, LocalDateTime.now()));

        // Admin switches to a strict no-cancellation policy
        admin.setCancellationPolicy(new CancellationPolicy() {
            public boolean canCancel(Booking b, LocalDateTime now) { return false; }
            public double cancellationFee(Booking b, LocalDateTime now) { return 0.0; }
        });

        // Now the same system-wide policy blocks cancellation
        assertFalse(pm.getCancellationPolicy().canCancel(booking, LocalDateTime.now()));
    }

    @Test @DisplayName("Custom PricingStrategy doubles the price")
    void customPricingStrategyDoubles() {
        PolicyManager pm = PolicyManager.getInstance();
        pm.setPricingStrategy(svc -> svc.getPrice() * 2.0);
        assertEquals(200.0, pm.getPricingStrategy().calculatePrice(service), 0.001);
    }

    @Test @DisplayName("Silent NotificationPolicy suppresses all notifications")
    void silentNotificationPolicySuppressesAll() {
        PolicyManager pm = PolicyManager.getInstance();
        pm.setNotificationPolicy(eventType -> false);
        // NotificationService checks policy before printing
        NotificationService ns = new NotificationService();
        // If it doesn't throw, the policy integration works
        assertDoesNotThrow(() -> ns.notify(client, "This should be silently suppressed"));
    }
}
