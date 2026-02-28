package policy;

public class PolicyManager {
    private static PolicyManager instance;

    private CancellationPolicy cancellationPolicy = new DefaultCancellationPolicy();
    private RefundPolicy       refundPolicy       = new DefaultRefundPolicy();
    private NotificationPolicy notificationPolicy = new DefaultNotificationPolicy();
    private PricingStrategy    pricingStrategy    = new DefaultPricingStrategy();

    private PolicyManager() {}

    public static PolicyManager getInstance() {
        if (instance == null) {
            instance = new PolicyManager();
        }
        return instance;
    }

    public CancellationPolicy getCancellationPolicy() { return cancellationPolicy; }
    public RefundPolicy       getRefundPolicy()       { return refundPolicy; }
    public NotificationPolicy getNotificationPolicy() { return notificationPolicy; }
    public PricingStrategy    getPricingStrategy()    { return pricingStrategy; }

    public void setCancellationPolicy(CancellationPolicy p) { this.cancellationPolicy = p; }
    public void setRefundPolicy(RefundPolicy p)             { this.refundPolicy = p; }
    public void setNotificationPolicy(NotificationPolicy p) { this.notificationPolicy = p; }
    public void setPricingStrategy(PricingStrategy p)       { this.pricingStrategy = p; }
}
