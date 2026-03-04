package model;

import policy.*;
import repo.ConsultantRepository;

public class Admin extends User {
    private PolicyManager policyManager;

    public Admin(String id, String name, String email) {
        super(id, name, email);
        this.policyManager = PolicyManager.getInstance();
    }

    public void approveConsultantRegistration(String consultantId, boolean approve,
                                               ConsultantRepository consultantRepo) {
        Consultant c = consultantRepo.findById(consultantId);
        if (c == null) {
            System.out.println("[Admin] Consultant not found: " + consultantId);
            return;
        }
        c.setRegistrationStatus(approve ? RegistrationStatus.APPROVED : RegistrationStatus.REJECTED);
        System.out.println("[Admin] Consultant " + c.getName() + " registration: "
                + c.getRegistrationStatus());
    }

    public void setCancellationPolicy(CancellationPolicy p) {
        policyManager.setCancellationPolicy(p);
        System.out.println("[Admin] Cancellation policy updated.");
    }

    public void setRefundPolicy(RefundPolicy p) {
        policyManager.setRefundPolicy(p);
        System.out.println("[Admin] Refund policy updated.");
    }

    public void setNotificationPolicy(NotificationPolicy p) {
        policyManager.setNotificationPolicy(p);
        System.out.println("[Admin] Notification policy updated.");
    }

    public void setPricingStrategy(PricingStrategy p) {
        policyManager.setPricingStrategy(p);
        System.out.println("[Admin] Pricing strategy updated.");
    }

    public PolicyManager getPolicyManager() { return policyManager; }
}
