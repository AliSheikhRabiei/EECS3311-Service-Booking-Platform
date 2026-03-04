package com.platform.policy;

/** Strategy: controls which event types trigger user notifications. */
public interface NotificationPolicy {
    boolean shouldNotify(String eventType);
}
