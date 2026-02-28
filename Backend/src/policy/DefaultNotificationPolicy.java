package policy;

/** Notifies on all event types. */
public class DefaultNotificationPolicy implements NotificationPolicy {
    @Override
    public boolean shouldNotify(String eventType) {
        return true;
    }
}
