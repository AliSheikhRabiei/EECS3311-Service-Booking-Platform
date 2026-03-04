package com.platform.policy;

/**
 * Default notification policy: notify on every event type.
 * TODO: extend with event-type filtering (e.g., suppress low-priority events).
 */
public class DefaultNotificationPolicy implements NotificationPolicy {

    @Override
    public boolean shouldNotify(String eventType) {
        // Always notify by default; policy can be tightened by Admin.
        return true;
    }
}
