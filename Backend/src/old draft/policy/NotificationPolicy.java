package policy;

public interface NotificationPolicy {
    boolean shouldNotify(String eventType);
}
