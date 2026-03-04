package com.platform.notify;

import com.platform.domain.User;
import com.platform.policy.PolicyManager;

/**
 * Simulated notification service (UC5, UC9, UC10).
 * Checks {@link com.platform.policy.NotificationPolicy} before printing.
 */
public class NotificationService {

    public void notify(User user, String message) {
        if (user == null || message == null) return;
        boolean shouldSend = PolicyManager.getInstance()
                .getNotificationPolicy()
                .shouldNotify(inferEventType(message));
        if (shouldSend) {
            System.out.println("[NOTIFY → " + user.getName() + "]: " + message);
        }
    }

    private String inferEventType(String message) {
        String lower = message.toLowerCase();
        if (lower.contains("confirmed"))  return "BOOKING_CONFIRMED";
        if (lower.contains("rejected"))   return "BOOKING_REJECTED";
        if (lower.contains("cancelled"))  return "BOOKING_CANCELLED";
        if (lower.contains("payment") || lower.contains("receipt")) return "PAYMENT";
        if (lower.contains("completed"))  return "BOOKING_COMPLETED";
        if (lower.contains("refund"))     return "REFUND";
        return "GENERAL";
    }
}
