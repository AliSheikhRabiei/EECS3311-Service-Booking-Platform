package com.platform.app;

/*
 * ============================================================
 * HOW TO RUN (Maven — recommended):
 *   mvn compile exec:java
 *     OR
 *   mvn package
 *   java -cp target/service-booking-platform-1.0.0.jar com.platform.app.Main
 *
 * HOW TO RUN (plain javac — from project root):
 *   find src/main/java -name "*.java" > sources.txt
 *   mkdir -p out
 *   javac -d out @sources.txt
 *   java -cp out com.platform.app.Main
 *
 * HOW TO TEST:
 *   mvn test
 * ============================================================
 */

import com.platform.domain.*;
import com.platform.notify.NotificationService;
import com.platform.payment.*;
import com.platform.policy.*;
import com.platform.repository.BookingRepository;
import com.platform.service.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

/**
 * Entry point.  Bootstraps the system, runs an automated happy-path demo,
 * then drops into a simple interactive menu.
 */
public class Main {

    // ── Shared services ──────────────────────────────────────────────────────
    private final PolicyManager       policyManager;
    private final NotificationService notificationService;
    private final ServiceCatalog      catalog;
    private final AvailabilityService availabilityService;
    private final BookingRepository   bookingRepository;
    private final PaymentService      paymentService;
    private final PaymentMethodService paymentMethodService;
    private final BookingService      bookingService;
    private final Admin               admin;

    // ── Demo fixtures ────────────────────────────────────────────────────────
    private Client     demoClient;
    private Consultant demoConsultant;
    private Service    demoService1, demoService2;
    private TimeSlot   demoSlot1, demoSlot2;

    public Main() {
        policyManager        = PolicyManager.getInstance();
        notificationService  = new NotificationService();
        catalog              = new ServiceCatalog();
        availabilityService  = new AvailabilityService();
        bookingRepository    = new BookingRepository();
        paymentService       = new PaymentService(policyManager, notificationService);
        paymentMethodService = new PaymentMethodService();
        bookingService       = new BookingService(
                bookingRepository, availabilityService,
                notificationService, paymentService, policyManager);
        admin = new Admin("A1", "Alice Admin", "admin@platform.com");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  DEMO DATA
    // ─────────────────────────────────────────────────────────────────────────

    private void setupDemoData() {
        separator("INITIALISING DEMO DATA");

        // Consultant
        demoConsultant = new Consultant("C1", "Bob Consultant", "bob@example.com", "Java & Systems Expert");
        demoConsultant.setRegistrationStatus(RegistrationStatus.APPROVED);

        // Pending consultant (for UC11 demo)
        Consultant pendingConsultant = new Consultant("C2", "Carol Pending", "carol@example.com", "Career Coach");
        // stays PENDING

        // Services
        demoService1 = new Service("S1", "Java Tutoring",
                "One-on-one Java coaching for students and developers", 60, 100.0, demoConsultant);
        demoService2 = new Service("S2", "Career Coaching",
                "Resume review, mock interviews and career planning",  45,  75.0, demoConsultant);

        demoConsultant.addService(demoService1);
        demoConsultant.addService(demoService2);
        catalog.addService(demoService1);
        catalog.addService(demoService2);

        // Time slots (tomorrow and day after)
        demoSlot1 = new TimeSlot(
                LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0),
                LocalDateTime.now().plusDays(1).withHour(11).withMinute(0).withSecond(0).withNano(0));
        demoSlot2 = new TimeSlot(
                LocalDateTime.now().plusDays(2).withHour(14).withMinute(0).withSecond(0).withNano(0),
                LocalDateTime.now().plusDays(2).withHour(15).withMinute(0).withSecond(0).withNano(0));

        availabilityService.addTimeSlot(demoConsultant, demoSlot1);
        availabilityService.addTimeSlot(demoConsultant, demoSlot2);

        // Client
        demoClient = new Client("U1", "Dave Client", "dave@example.com");

        // Pre-saved credit card for demo
        CreditCardMethod cc = new CreditCardMethod(
                demoClient, "PM1", "1234567890123456", "12/27", "123");
        paymentMethodService.addMethod(demoClient, cc);

        System.out.println("\nDemo data ready.");
        System.out.println("  Admin   : " + admin);
        System.out.println("  Client  : " + demoClient);
        System.out.println("  Consult : " + demoConsultant + " [" + demoConsultant.getRegistrationStatus() + "]");
        System.out.println("  Services: " + catalog.listAllServices().size());
        System.out.println("  Slots   : " + availabilityService.listAllSlots(demoConsultant).size());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  AUTOMATED HAPPY-PATH DEMO
    // ─────────────────────────────────────────────────────────────────────────

    private void runHappyPathDemo() {
        separator("AUTOMATED HAPPY-PATH DEMO");

        // Step 1: Browse services
        System.out.println("\n[Step 1] UC1 – Browse services:");
        catalog.listAllServices().forEach(s -> System.out.println("  " + s));

        // Step 2: Request booking
        System.out.println("\n[Step 2] UC2 – Client requests a booking:");
        Booking b1 = bookingService.createBooking(demoClient, demoService1, demoSlot1);
        System.out.println("  Status after creation: " + b1.getStateName());

        // Step 3: Consultant confirms
        System.out.println("\n[Step 3] UC9 – Consultant accepts booking:");
        bookingService.confirmBooking(b1.getBookingId());
        System.out.println("  Status after confirm: " + b1.getStateName());

        // Step 4: Process payment
        System.out.println("\n[Step 4] UC5 – Client pays (credit card):");
        PaymentMethod pm = paymentMethodService.listMethods(demoClient).get(0);
        PaymentTransaction tx = paymentService.processPayment(b1, pm);
        System.out.println("  Transaction: " + tx);

        // Handle the (unlikely) random payment failure in the demo
        int retries = 0;
        while (tx.getStatus() != PaymentStatus.SUCCESS && retries < 3) {
            System.out.println("  [Demo] Retrying payment (random failure simulation)...");
            // Re-create the slot to simulate a fresh attempt
            demoSlot1.release();
            b1 = bookingService.createBooking(demoClient, demoService1, demoSlot1);
            bookingService.confirmBooking(b1.getBookingId());
            tx = paymentService.processPayment(b1, pm);
            retries++;
        }

        if (tx.getStatus() == PaymentStatus.SUCCESS) {
            System.out.println("  Receipt  : " + paymentService.getLastReceipt());
            System.out.println("  Status after payment: " + b1.getStateName());

            // Step 5: Complete booking
            System.out.println("\n[Step 5] UC10 – Consultant marks booking completed:");
            bookingService.completeBooking(b1.getBookingId());
            System.out.println("  Final status: " + b1.getStateName());
        } else {
            System.out.println("  [Demo] Payment failed after " + retries + " retries; skipping complete step.");
        }

        // Step 6: View booking history
        System.out.println("\n[Step 6] UC4 – Booking history for " + demoClient.getName() + ":");
        bookingService.getBookingsForClient(demoClient.getId())
                .forEach(b -> System.out.println("  " + b));

        // Step 7: View payment history
        System.out.println("\n[Step 7] UC7 – Payment history for " + demoClient.getName() + ":");
        paymentService.getPaymentHistory(demoClient)
                .forEach(t -> System.out.println("  " + t));

        // Step 8: Admin approves a pending consultant (UC11)
        System.out.println("\n[Step 8] UC11 – Admin approves consultant registration:");
        Consultant pendingC = new Consultant("C2", "Carol Pending", "carol@example.com", "Career Coach");
        admin.approveConsultantRegistration(pendingC, true);
        System.out.println("  New status: " + pendingC.getRegistrationStatus());

        separator("DEMO COMPLETE — entering interactive menu");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  INTERACTIVE MENU
    // ─────────────────────────────────────────────────────────────────────────

    private void runMenu() {
        Scanner sc = new Scanner(System.in);
        boolean running = true;

        while (running) {
            printMenu();
            System.out.print("Choice: ");
            String input = sc.nextLine().trim();

            switch (input) {
                case "1"  -> menuBrowseServices();
                case "2"  -> menuRequestBooking(sc);
                case "3"  -> menuConsultantDecision(sc);
                case "4"  -> menuProcessPayment(sc);
                case "5"  -> menuViewBookingHistory();
                case "6"  -> menuManagePaymentMethods(sc);
                case "7"  -> menuViewPaymentHistory();
                case "8"  -> menuAdminApprove(sc);
                case "9"  -> menuAdminPolicy(sc);
                case "10" -> menuCompleteBooking(sc);
                case "0"  -> { System.out.println("Goodbye!"); running = false; }
                default   -> System.out.println("  Invalid choice.");
            }
        }
        sc.close();
    }

    private void printMenu() {
        System.out.println("""

                ┌─────────────────────────────────────────────┐
                │   Service Booking & Consulting Platform     │
                ├─────────────────────────────────────────────┤
                │  1.  Browse Services (UC1)                  │
                │  2.  Request Booking  (UC2)                 │
                │  3.  Consultant Accept/Reject (UC9)         │
                │  4.  Process Payment  (UC5)                 │
                │  5.  View Booking History (UC4)             │
                │  6.  Manage Payment Methods (UC6)           │
                │  7.  View Payment History (UC7)             │
                │  8.  Admin: Approve Consultant (UC11)       │
                │  9.  Admin: Set Cancellation Policy (UC12)  │
                │  10. Complete Booking (UC10)                │
                │  0.  Exit                                   │
                └─────────────────────────────────────────────┘""");
    }

    // ── Menu handlers ─────────────────────────────────────────────────────────

    private void menuBrowseServices() {
        System.out.println("\n── Services ──────────────────────────────────");
        List<Service> services = catalog.listAllServices();
        if (services.isEmpty()) { System.out.println("  No services."); return; }
        for (int i = 0; i < services.size(); i++) {
            Service s = services.get(i);
            System.out.printf("  [%d] %-20s $%6.2f  %dmin  by %s%n",
                    i + 1, s.getTitle(), s.getPrice(), s.getDurationMin(), s.getConsultant().getName());
        }
    }

    private void menuRequestBooking(Scanner sc) {
        System.out.println("\n── Request Booking ───────────────────────────");
        List<Service> services = catalog.listAllServices();
        if (services.isEmpty()) { System.out.println("  No services available."); return; }
        menuBrowseServices();
        int sIdx = promptInt(sc, "Select service number", 1, services.size()) - 1;
        Service chosen = services.get(sIdx);

        List<TimeSlot> slots = availabilityService.listAvailableSlots(chosen);
        if (slots.isEmpty()) { System.out.println("  No available slots for this service."); return; }
        System.out.println("  Available slots:");
        for (int i = 0; i < slots.size(); i++) {
            System.out.println("    [" + (i + 1) + "] " + slots.get(i));
        }
        int slotIdx = promptInt(sc, "Select slot number", 1, slots.size()) - 1;
        try {
            Booking b = bookingService.createBooking(demoClient, chosen, slots.get(slotIdx));
            System.out.println("  Booking created: " + b.getBookingId() + " | Status: " + b.getStateName());
        } catch (Exception e) {
            System.out.println("  Error: " + e.getMessage());
        }
    }

    private void menuConsultantDecision(Scanner sc) {
        System.out.println("\n── Consultant Accept/Reject ──────────────────");
        List<Booking> pending = bookingService.getBookingsForConsultant(demoConsultant)
                .stream().filter(b -> b.getState().getClass().getSimpleName().equals("RequestedState"))
                .collect(Collectors.toList());
        if (pending.isEmpty()) { System.out.println("  No pending booking requests."); return; }
        pending.forEach(b -> System.out.println("  " + b));
        System.out.print("  Enter Booking ID: ");
        String bid = sc.nextLine().trim();
        System.out.print("  Accept? (yes/no): ");
        boolean accept = sc.nextLine().trim().equalsIgnoreCase("yes");
        try {
            if (accept) {
                bookingService.confirmBooking(bid);
            } else {
                System.out.print("  Reason: ");
                String reason = sc.nextLine().trim();
                bookingService.rejectBooking(bid, reason);
            }
        } catch (Exception e) {
            System.out.println("  Error: " + e.getMessage());
        }
    }

    private void menuProcessPayment(Scanner sc) {
        System.out.println("\n── Process Payment ───────────────────────────");
        List<Booking> confirmed = bookingService.getBookingsForClient(demoClient.getId())
                .stream().filter(b -> b.getState().getClass().getSimpleName().equals("ConfirmedState"))
                .collect(Collectors.toList());
        if (confirmed.isEmpty()) { System.out.println("  No confirmed bookings awaiting payment."); return; }
        confirmed.forEach(b -> System.out.println("  " + b));
        System.out.print("  Enter Booking ID: ");
        String bid = sc.nextLine().trim();
        Booking booking = bookingService.getBooking(bid);
        if (booking == null) { System.out.println("  Booking not found."); return; }

        List<PaymentMethod> methods = paymentMethodService.listMethods(demoClient);
        if (methods.isEmpty()) { System.out.println("  No saved payment methods. Add one first (option 6)."); return; }
        System.out.println("  Saved methods:");
        for (int i = 0; i < methods.size(); i++) {
            System.out.println("    [" + (i + 1) + "] " + methods.get(i));
        }
        int mIdx = promptInt(sc, "  Select method", 1, methods.size()) - 1;
        PaymentTransaction tx = paymentService.processPayment(booking, methods.get(mIdx));
        System.out.println("  Result: " + tx);
        if (tx.getStatus() == PaymentStatus.SUCCESS) {
            System.out.println("  Receipt: " + paymentService.getLastReceipt());
        }
    }

    private void menuViewBookingHistory() {
        System.out.println("\n── Booking History for " + demoClient.getName() + " ────────────");
        List<Booking> bookings = bookingService.getBookingsForClient(demoClient.getId());
        if (bookings.isEmpty()) { System.out.println("  No bookings."); return; }
        bookings.forEach(b -> System.out.printf("  %-6s  %-20s  %s%n",
                b.getBookingId(), b.getService().getTitle(), b.getStateName()));
    }

    private void menuManagePaymentMethods(Scanner sc) {
        System.out.println("\n── Manage Payment Methods ────────────────────");
        System.out.println("  a) Add Credit Card");
        System.out.println("  b) Add PayPal");
        System.out.println("  c) Add Bank Transfer");
        System.out.println("  d) List methods");
        System.out.println("  e) Remove method");
        System.out.print("  Choice: ");
        switch (sc.nextLine().trim().toLowerCase()) {
            case "a" -> addCreditCard(sc);
            case "b" -> addPayPal(sc);
            case "c" -> addBankTransfer(sc);
            case "d" -> paymentMethodService.listMethods(demoClient)
                    .forEach(m -> System.out.println("  [" + m.getMethodId() + "] " + m));
            case "e" -> {
                System.out.print("  Method ID to remove: ");
                paymentMethodService.removeMethod(demoClient, sc.nextLine().trim());
            }
            default -> System.out.println("  Invalid choice.");
        }
    }

    private void addCreditCard(Scanner sc) {
        System.out.print("  Card number (16 digits): "); String num = sc.nextLine().trim();
        System.out.print("  Expiry (MM/yy): ");           String exp = sc.nextLine().trim();
        System.out.print("  CVV (3-4 digits): ");         String cvv = sc.nextLine().trim();
        String id = "PM-" + System.currentTimeMillis();
        CreditCardMethod cc = new CreditCardMethod(demoClient, id, num, exp, cvv);
        if (!cc.validate()) { System.out.println("  Invalid credit card details."); return; }
        paymentMethodService.addMethod(demoClient, cc);
    }

    private void addPayPal(Scanner sc) {
        System.out.print("  PayPal email: ");
        String email = sc.nextLine().trim();
        String id = "PM-" + System.currentTimeMillis();
        PayPalMethod pp = new PayPalMethod(demoClient, id, email);
        if (!pp.validate()) { System.out.println("  Invalid PayPal email."); return; }
        paymentMethodService.addMethod(demoClient, pp);
    }

    private void addBankTransfer(Scanner sc) {
        System.out.print("  Account number (8-17 digits): "); String acct = sc.nextLine().trim();
        System.out.print("  Routing number (9 digits): ");    String rout = sc.nextLine().trim();
        String id = "PM-" + System.currentTimeMillis();
        BankTransferMethod bt = new BankTransferMethod(demoClient, id, acct, rout);
        if (!bt.validate()) { System.out.println("  Invalid bank transfer details."); return; }
        paymentMethodService.addMethod(demoClient, bt);
    }

    private void menuViewPaymentHistory() {
        System.out.println("\n── Payment History for " + demoClient.getName() + " ─────────────");
        List<PaymentTransaction> history = paymentService.getPaymentHistory(demoClient);
        if (history.isEmpty()) { System.out.println("  No transactions."); return; }
        history.forEach(t -> System.out.println("  " + t));
    }

    private void menuAdminApprove(Scanner sc) {
        System.out.println("\n── Admin: Approve Consultant ─────────────────");
        System.out.print("  Consultant name: ");  String name  = sc.nextLine().trim();
        System.out.print("  Consultant id: ");    String cid   = sc.nextLine().trim();
        System.out.print("  Approve? (yes/no): "); boolean ok  = sc.nextLine().trim().equalsIgnoreCase("yes");
        // In production: look up consultant from a ConsultantRepository
        Consultant c = new Consultant(cid, name, name.toLowerCase() + "@example.com", "");
        admin.approveConsultantRegistration(c, ok);
    }

    private void menuAdminPolicy(Scanner sc) {
        System.out.println("\n── Admin: Cancellation Policy ────────────────");
        System.out.println("  1) Default (allow cancel before PAID, no fee)");
        System.out.println("  2) No cancellations allowed");
        System.out.print("  Choice: ");
        switch (sc.nextLine().trim()) {
            case "1" -> admin.setCancellationPolicy(new DefaultCancellationPolicy());
            case "2" -> admin.setCancellationPolicy(new CancellationPolicy() {
                @Override public boolean canCancel(com.platform.domain.Booking b, java.time.LocalDateTime now) { return false; }
                @Override public double cancellationFee(com.platform.domain.Booking b, java.time.LocalDateTime now) { return b.getService().getPrice(); }
            });
            default -> System.out.println("  Invalid.");
        }
        System.out.println("  Policy updated.");
    }

    private void menuCompleteBooking(Scanner sc) {
        System.out.println("\n── Complete Booking ──────────────────────────");
        List<Booking> paid = bookingService.getBookingsForConsultant(demoConsultant)
                .stream().filter(b -> b.getState() instanceof com.platform.state.PaidState)
                .collect(Collectors.toList());
        if (paid.isEmpty()) { System.out.println("  No PAID bookings to complete."); return; }
        paid.forEach(b -> System.out.println("  " + b));
        System.out.print("  Enter Booking ID: ");
        String bid = sc.nextLine().trim();
        try {
            bookingService.completeBooking(bid);
        } catch (Exception e) {
            System.out.println("  Error: " + e.getMessage());
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private int promptInt(Scanner sc, String prompt, int min, int max) {
        while (true) {
            System.out.print("  " + prompt + " [" + min + "-" + max + "]: ");
            try {
                int v = Integer.parseInt(sc.nextLine().trim());
                if (v >= min && v <= max) return v;
            } catch (NumberFormatException ignored) {}
            System.out.println("  Please enter a number between " + min + " and " + max + ".");
        }
    }

    private void separator(String title) {
        System.out.println("\n" + "=".repeat(55));
        System.out.println("  " + title);
        System.out.println("=".repeat(55));
    }

    // ── Entry Point ───────────────────────────────────────────────────────────

    public static void main(String[] args) {
        Main app = new Main();
        app.setupDemoData();
        app.runHappyPathDemo();
        app.runMenu();
    }
}
