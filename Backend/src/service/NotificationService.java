package service;

import model.User;
import policy.PolicyManager;

public class NotificationService {
    public void notify(User user, String message) {
        PolicyManager pm = PolicyManager.getInstance();
        // Check notification policy before sending
        String eventType = inferEventType(message);
        if (pm.getNotificationPolicy().shouldNotify(eventType)) {
            System.out.println("[NOTIFY → " + user.getName() + "]: " + message);
        }
    }

    private String inferEventType(String message) {
        String lower = message.toLowerCase();
        if (lower.contains("confirm"))  return "BOOKING_CONFIRMED";
        if (lower.contains("reject"))   return "BOOKING_REJECTED";
        if (lower.contains("cancel"))   return "BOOKING_CANCELLED";
        if (lower.contains("payment"))  return "PAYMENT";
        if (lower.contains("complet"))  return "BOOKING_COMPLETED";
        return "GENERAL";
    }
}
