package com.platform.domain;

import com.platform.policy.CancellationPolicy;
import com.platform.policy.NotificationPolicy;
import com.platform.policy.PolicyManager;
import com.platform.policy.PricingStrategy;
import com.platform.policy.RefundPolicy;
import com.platform.repository.ConsultantRepository;
import com.platform.service.RegistrationService;

/**
 * System administrator: approves consultant registrations (UC11) and
 * configures system-wide policies (UC12).
 * All policy mutations delegate to PolicyManager to ensure global consistency.
 */
public class Admin extends User {

    private final PolicyManager policyManager;
    public final ConsultantRepository consultantRepository;


    public Admin(String id, String name, String email) {
        super(id, name, email);
        this.policyManager = PolicyManager.getInstance();
        this.consultantRepository = new ConsultantRepository();
    }

    /**
     * UC11 – Approve or reject a consultant's registration.
     *
     * @param consultant the consultant to approve/reject (resolved by caller)
     * @param approve    true → APPROVED, false → REJECTED
     */
    public void approveConsultantRegistration(Consultant consultant, boolean approve) {
        if (consultant == null) throw new IllegalArgumentException("Consultant must not be null.");
        RegistrationStatus newStatus = approve ? RegistrationStatus.APPROVED : RegistrationStatus.REJECTED;
        consultant.setRegistrationStatus(newStatus);
        if(approve) consultantRepository.save(consultant);
        System.out.println("[Admin] " + consultant.getName() + " registration → " + newStatus);
    }

    /**
     * Convenience overload matching the class-diagram signature
     * (consultantId resolution is the caller's responsibility).
     */
    public void approveConsultantRegistration(String consultantId, boolean approve) {
        // TODO: resolve consultant from repository and call overload above
        System.out.println("[Admin] approveConsultantRegistration(id=" + consultantId
                + ", approve=" + approve + ") – resolve consultant from repository.");
        if(approve) {

        }
    }

    /** UC12 – Configure cancellation rules. */
    public void setCancellationPolicy(CancellationPolicy p) {
        policyManager.setCancellationPolicy(p);
    }

    /** UC12 – Configure refund rules. */
    public void setRefundPolicy(RefundPolicy p) {
        policyManager.setRefundPolicy(p);
    }

    /** UC12 – Configure notification settings. */
    public void setNotificationPolicy(NotificationPolicy p) {
        policyManager.setNotificationPolicy(p);
    }

    /** UC12 – Configure pricing strategy. */
    public void setPricingStrategy(PricingStrategy p) {
        policyManager.setPricingStrategy(p);
    }

    public PolicyManager getPolicyManager() { return policyManager; }
}
