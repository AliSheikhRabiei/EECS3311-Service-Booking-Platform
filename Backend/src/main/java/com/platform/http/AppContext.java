package com.platform.http;

import com.platform.auth.AuthService;
import com.platform.auth.SessionStore;
import com.platform.db.*;
import com.platform.domain.Consultant;
import com.platform.domain.Service;
import com.platform.notify.NotificationService;
import com.platform.payment.PaymentMethodService;
import com.platform.payment.PaymentService;
import com.platform.policy.PolicyManager;
import com.platform.repository.BookingRepository;
import com.platform.service.AvailabilityService;
import com.platform.service.BookingService;
import com.platform.service.ServiceCatalog;

/**
 * Single wiring class for the entire Phase 2 backend.
 *
 * On construction:
 *  1. Connects to Postgres and runs schema.sql
 *  2. Creates all DB repositories
 *  3. Creates all Phase 1 services (pointing at DB repos)
 *  4. Reloads existing DB data back into the in-memory service layer
 *  5. Ensures the default admin account exists
 *
 * Every HTTP handler receives an AppContext instance and calls through to
 * the services it needs — no handler instantiates anything itself.
 */
public class AppContext {

    // ── DB repositories ──────────────────────────────────────────────────────
    public final UserRepository               userRepository;
    public final ServiceRepository            serviceRepository;
    public final SlotRepository               slotRepository;
    public final DbBookingRepository          bookingRepository;
    public final DbConsultantRepository       consultantRepository;
    public final PaymentMethodRepository      paymentMethodRepository;
    public final PaymentTransactionRepository transactionRepository;

    // ── Auth ─────────────────────────────────────────────────────────────────
    public final SessionStore sessionStore;
    public final AuthService  authService;

    // ── Phase 1 services (unchanged logic) ───────────────────────────────────
    public final PolicyManager         policyManager;
    public final NotificationService   notificationService;
    public final ServiceCatalog        catalog;
    public final AvailabilityService   availabilityService;
    public final PaymentService        paymentService;
    public final PaymentMethodService  paymentMethodService;
    public final BookingService        bookingService;

    public AppContext() {
        // ── 1. DB schema ──────────────────────────────────────────────────────
        Database.initialize();

        // ── 2. DB repositories ────────────────────────────────────────────────
        userRepository          = new UserRepository();
        serviceRepository       = new ServiceRepository(userRepository);
        slotRepository          = new SlotRepository();
        bookingRepository       = new DbBookingRepository(userRepository, serviceRepository, slotRepository);
        consultantRepository    = new DbConsultantRepository(userRepository);
        paymentMethodRepository = new PaymentMethodRepository();
        transactionRepository   = new PaymentTransactionRepository();

        // ── 3. Auth ───────────────────────────────────────────────────────────
        sessionStore = new SessionStore();
        authService  = new AuthService(userRepository, sessionStore);

        // ── 4. Phase 1 services ───────────────────────────────────────────────
        policyManager       = PolicyManager.getInstance();
        notificationService = new NotificationService();
        catalog             = new ServiceCatalog();
        availabilityService = new AvailabilityService();
        paymentService      = new PaymentService(policyManager, notificationService);
        paymentMethodService = new PaymentMethodService();
        bookingService      = new BookingService(
                bookingRepository, availabilityService,
                notificationService, paymentService, policyManager);

        // ── 5. Reload DB data into in-memory services ─────────────────────────
        reloadFromDatabase();

        // ── 6. Ensure default admin ───────────────────────────────────────────
        String adminEmail    = System.getenv().getOrDefault("ADMIN_EMAIL",    "admin@platform.com");
        String adminPassword = System.getenv().getOrDefault("ADMIN_PASSWORD", "admin123");
        authService.createAdmin("Platform Admin", adminEmail, adminPassword);
    }

    /**
     * Loads existing DB rows back into the in-memory AvailabilityService and ServiceCatalog.
     * This is what makes the Phase 1 business logic work correctly on restarts.
     */
    private void reloadFromDatabase() {
        // Reload services into catalog
        for (Service s : serviceRepository.findAll()) {
            if (catalog.findById(s.getServiceId()) == null) {
                catalog.addService(s);
                s.getConsultant().addService(s);
            }
        }

        // Reload time slots into AvailabilityService
        for (SlotRepository.SlotRow row : slotRepository.findAll()) {
            Consultant consultant = userRepository.loadConsultant(row.consultantId);
            if (consultant != null) {
                availabilityService.addTimeSlot(consultant, row.slot);
            }
        }

        System.out.println("[AppContext] Reloaded "
                + catalog.listAllServices().size() + " service(s) from DB.");
    }
}
