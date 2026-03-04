package com.platform.policy;

/**
 * <strong>Singleton</strong> that holds system-wide configurable policies (UC12).
 *
 * <p>The Admin class delegates all policy changes here so that every part of
 * the system reads from the same authoritative source.</p>
 *
 * <p>Thread-safety note: for Phase 1 (single-threaded demo) lazy init is fine.
 * Use {@code volatile} + double-checked locking if multi-threading is required.</p>
 */
public class PolicyManager {

    private static PolicyManager instance;

    private CancellationPolicy cancellationPolicy;
    private RefundPolicy       refundPolicy;
    private NotificationPolicy notificationPolicy;
    private PricingStrategy    pricingStrategy;

    /** Private constructor – sets sensible defaults. */
    private PolicyManager() {
        this.cancellationPolicy = new DefaultCancellationPolicy();
        this.refundPolicy       = new DefaultRefundPolicy();
        this.notificationPolicy = new DefaultNotificationPolicy();
        this.pricingStrategy    = new DefaultPricingStrategy();
    }

    /** Returns the single shared instance, creating it on first call. */
    public static PolicyManager getInstance() {
        if (instance == null) {
            instance = new PolicyManager();
        }
        return instance;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public CancellationPolicy getCancellationPolicy() { return cancellationPolicy; }
    public RefundPolicy       getRefundPolicy()       { return refundPolicy; }
    public NotificationPolicy getNotificationPolicy() { return notificationPolicy; }
    public PricingStrategy    getPricingStrategy()    { return pricingStrategy; }

    // ── Setters (called by Admin via UC12) ───────────────────────────────────

    public void setCancellationPolicy(CancellationPolicy p) {
        if (p == null) throw new IllegalArgumentException("CancellationPolicy must not be null.");
        this.cancellationPolicy = p;
    }

    public void setRefundPolicy(RefundPolicy p) {
        if (p == null) throw new IllegalArgumentException("RefundPolicy must not be null.");
        this.refundPolicy = p;
    }

    public void setNotificationPolicy(NotificationPolicy p) {
        if (p == null) throw new IllegalArgumentException("NotificationPolicy must not be null.");
        this.notificationPolicy = p;
    }

    public void setPricingStrategy(PricingStrategy p) {
        if (p == null) throw new IllegalArgumentException("PricingStrategy must not be null.");
        this.pricingStrategy = p;
    }

    /** Resets to defaults – useful for testing. */
    public static void resetInstance() {
        instance = null;
    }
}
