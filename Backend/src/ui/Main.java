package ui;

/*
 * ============================================================
 * HOW TO RUN (from the project root, where /src lives):
 *
 *   1. Find all .java source files:
 *        find src -name "*.java" > sources.txt
 *
 *   2. Compile everything into an 'out' directory:
 *        javac -d out @sources.txt
 *
 *   3. Run:
 *        java -cp out ui.Main
 *
 * Requires Java 17+. No external libraries needed.
 * ============================================================
 */

import model.*;
import payment.*;
import policy.*;
import repo.*;
import service.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

public class Main {

    // ── Services ────────────────────────────────────────────────────────────
    private final ServiceCatalog       catalog;
    private final BookingService       bookingService;
    private final PaymentService       paymentService;
    private final PaymentMethodService paymentMethodService;
    private final AvailabilityService  availService;
    private final ConsultantRepository consultantRepo;
    private final NotificationService  notifService;
    private final PolicyManager        policyManager;

    // ── Demo data ────────────────────────────────────────────────────────────
    private Admin      admin;
    private Client     client1;
    private Consultant c1, c2;
    private Service    s1, s2;
    private TimeSlot   slot1, slot2;

    public Main() {
        policyManager        = PolicyManager.getInstance();
        notifService         = new NotificationService();
        catalog              = new ServiceCatalog();
        availService         = new AvailabilityService();
        consultantRepo       = new ConsultantRepository();
        paymentMethodService = new PaymentMethodService();
        paymentService       = new PaymentService(notifService);
        bookingService       = new BookingService(
                new BookingRepository(), availService, notifService, policyManager);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  DEMO DATA SETUP
    // ─────────────────────────────────────────────────────────────────────────
    private void setupDemoData() {
        System.out.println("=".repeat(60));
        System.out.println("  SERVICE BOOKING & CONSULTING PLATFORM — Phase 1");
        System.out.println("=".repeat(60));
        System.out.println("\n[SETUP] Initializing demo data...\n");

        // Users
        admin   = new Admin("a1", "Alice Admin", "admin@platform.com");
        client1 = new Client("u1", "Dave Client", "dave@client.com");

        c1 = new Consultant("c1", "Bob Consultant", "bob@consulting.com", "Java & Software Expert");
        c2 = new Consultant("c2", "Carol Consultant", "carol@consulting.com", "Career & Life Coach");
        c1.setRegistrationStatus(RegistrationStatus.APPROVED);
        // c2 stays PENDING

        consultantRepo.save(c1);
        consultantRepo.save(c2);

        // Services
        s1 = new Service("s1", "Java Tutoring",
                "1-on-1 Java coaching for students and professionals", 60, 100.0, c1);
        s2 = new Service("s2", "Career Coaching",
                "Resume review, mock interviews, and career planning", 45, 75.0, c1);

        catalog.addService(s1);
        catalog.addService(s2);

        // Time slots
        slot1 = new TimeSlot(
                LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0),
                LocalDateTime.now().plusDays(1).withHour(11).withMinute(0).withSecond(0).withNano(0));
        slot2 = new TimeSlot(
                LocalDateTime.now().plusDays(2).withHour(14).withMinute(0).withSecond(0).withNano(0),
                LocalDateTime.now().plusDays(2).withHour(15).withMinute(0).withSecond(0).withNano(0));

        availService.addTimeSlot(c1, slot1);
        availService.addTimeSlot(c1, slot2);

        // Saved payment method for client1
        CreditCardMethod cc = new CreditCardMethod(client1, "PM1",
                "1234567890123456", "12/27", "123");
        paymentMethodService.addMethod(client1, cc);

        System.out.println("[SETUP] Demo data ready.\n");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  AUTOMATED DEMO SCENARIO
    // ─────────────────────────────────────────────────────────────────────────
    private void runDemoScenario() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  AUTOMATED DEMO SCENARIO");
        System.out.println("=".repeat(60));

        // Step 1: List services
        System.out.println("\n[DEMO Step 1] Browse all services:");
        catalog.listAllServices().forEach(s -> System.out.println("  " + s));

        // Step 2: Client books s1 + slot1
        System.out.println("\n[DEMO Step 2] client1 requests booking for 'Java Tutoring' (slot1):");
        Booking b1 = bookingService.createBooking(client1, s1, slot1);
        System.out.println("  Created: " + b1);

        // Step 3: Consultant accepts
        System.out.println("\n[DEMO Step 3] Consultant c1 ACCEPTS booking " + b1.getBookingId() + ":");
        bookingService.consultantDecision(b1.getBookingId(), true, null);
        System.out.println("  Booking status: " + b1.getStatus());

        // Step 4: Process payment
        System.out.println("\n[DEMO Step 4] client1 pays for booking " + b1.getBookingId() + " with saved credit card:");
        PaymentMethod pm = paymentMethodService.listMethods(client1).get(0);
        PaymentTransaction tx = paymentService.processPayment(b1, pm);
        if (tx != null && tx.getStatus() == PaymentStatus.SUCCESS) {
            System.out.println("  " + tx);
            Receipt r = paymentService.getLastReceipt();
            if (r != null) System.out.println("  " + r);
        } else {
            System.out.println("  [DEMO] Payment failed (10% chance). Re-try from menu.");
        }

        // Step 5: Consultant marks complete (only if paid)
        if (b1.getStatus() == BookingStatus.PAID) {
            System.out.println("\n[DEMO Step 5] Consultant marks booking COMPLETED:");
            bookingService.completeBooking(b1.getBookingId());
            System.out.println("  Booking status: " + b1.getStatus());
        } else {
            System.out.println("\n[DEMO Step 5] Skipped (booking not in PAID state).");
        }

        // Step 6: Booking history
        System.out.println("\n[DEMO Step 6] Booking history for client1:");
        bookingService.getBookingsForClient(client1.getId())
                .forEach(b -> System.out.println("  " + b));

        System.out.println("\n" + "=".repeat(60));
        System.out.println("  Demo complete. Entering interactive menu...");
        System.out.println("=".repeat(60) + "\n");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  MAIN MENU
    // ─────────────────────────────────────────────────────────────────────────
    public void run() {
        Scanner sc = new Scanner(System.in);
        boolean running = true;

        while (running) {
            printMenu();
            System.out.print("Enter choice: ");
            String input = sc.nextLine().trim();

            switch (input) {
                case "1"  -> browseServices();
                case "2"  -> requestBooking(sc);
                case "3"  -> consultantDecision(sc);
                case "4"  -> processPayment(sc);
                case "5"  -> viewBookingHistory();
                case "6"  -> managePaymentMethods(sc);
                case "7"  -> viewPaymentHistory();
                case "8"  -> adminApproveConsultant(sc);
                case "9"  -> adminSetPolicy(sc);
                case "10" -> completeBooking(sc);
                case "0"  -> { System.out.println("Goodbye!"); running = false; }
                default   -> System.out.println("Invalid choice. Try again.");
            }
        }
        sc.close();
    }

    private void printMenu() {
        System.out.println("\n┌─────────────────────────────────────────┐");
        System.out.println("│   Service Booking & Consulting Platform  │");
        System.out.println("├─────────────────────────────────────────┤");
        System.out.println("│  1.  Browse Services                     │");
        System.out.println("│  2.  Request Booking (as client1)        │");
        System.out.println("│  3.  Consultant Accept/Reject Booking    │");
        System.out.println("│  4.  Process Payment (as client1)        │");
        System.out.println("│  5.  View Booking History (client1)      │");
        System.out.println("│  6.  Manage Payment Methods (client1)    │");
        System.out.println("│  7.  View Payment History (client1)      │");
        System.out.println("│  8.  Admin: Approve Consultant           │");
        System.out.println("│  9.  Admin: Set Cancellation Policy      │");
        System.out.println("│  10. Complete Booking (Consultant)       │");
        System.out.println("│  0.  Exit                                │");
        System.out.println("└─────────────────────────────────────────┘");
    }

    // ── UC1 ─────────────────────────────────────────────────────────────────
    private void browseServices() {
        System.out.println("\n--- All Services ---");
        List<model.Service> services = catalog.listAllServices();
        if (services.isEmpty()) { System.out.println("No services available."); return; }
        for (int i = 0; i < services.size(); i++) {
            model.Service s = services.get(i);
            System.out.printf("  [%d] %s — $%.2f, %dmin — Consultant: %s%n",
                    i + 1, s.getTitle(), s.getPrice(), s.getDurationMin(),
                    s.getConsultant().getName());
        }
    }

    // ── UC2 ─────────────────────────────────────────────────────────────────
    private void requestBooking(Scanner sc) {
        System.out.println("\n--- Request Booking (as " + client1.getName() + ") ---");
        List<model.Service> services = catalog.listAllServices();
        browseServices();
        System.out.print("Select service number: ");
        int sIdx = parseInt(sc.nextLine()) - 1;
        if (sIdx < 0 || sIdx >= services.size()) { System.out.println("Invalid selection."); return; }
        model.Service chosen = services.get(sIdx);

        List<TimeSlot> slots = availService.listAvailableSlots(chosen);
        if (slots.isEmpty()) { System.out.println("No available slots for this service."); return; }
        System.out.println("Available slots:");
        for (int i = 0; i < slots.size(); i++) {
            System.out.println("  [" + (i + 1) + "] " + slots.get(i));
        }
        System.out.print("Select slot number: ");
        int slotIdx = parseInt(sc.nextLine()) - 1;
        if (slotIdx < 0 || slotIdx >= slots.size()) { System.out.println("Invalid selection."); return; }

        try {
            Booking b = bookingService.createBooking(client1, chosen, slots.get(slotIdx));
            System.out.println("Booking created! ID: " + b.getBookingId() + " | Status: " + b.getStatus());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    // ── UC9 ─────────────────────────────────────────────────────────────────
    private void consultantDecision(Scanner sc) {
        System.out.println("\n--- Consultant Accept/Reject Booking ---");
        List<Booking> bookings = bookingService.getBookingsForConsultant(c1.getId());
        if (bookings.isEmpty()) { System.out.println("No bookings for consultant c1."); return; }
        bookings.forEach(b -> System.out.println("  " + b));
        System.out.print("Enter Booking ID: ");
        String bookingId = sc.nextLine().trim();
        System.out.print("Accept? (yes/no): ");
        boolean accept = sc.nextLine().trim().equalsIgnoreCase("yes");
        String reason = "";
        if (!accept) {
            System.out.print("Reason for rejection: ");
            reason = sc.nextLine().trim();
        }
        try {
            bookingService.consultantDecision(bookingId, accept, reason);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    // ── UC5 ─────────────────────────────────────────────────────────────────
    private void processPayment(Scanner sc) {
        System.out.println("\n--- Process Payment (as " + client1.getName() + ") ---");
        List<Booking> bookings = bookingService.getBookingsForClient(client1.getId())
                .stream()
                .filter(b -> b.getStatus() == BookingStatus.PENDING_PAYMENT)
                .collect(Collectors.toList());
        bookings.forEach(b -> System.out.println("  " + b));
        System.out.print("Enter Booking ID to pay: ");
        String bookingId = sc.nextLine().trim();
        Booking booking = bookingService.getBooking(bookingId);
        if (booking == null) { System.out.println("Booking not found."); return; }

        List<PaymentMethod> methods = paymentMethodService.listMethods(client1);
        if (methods.isEmpty()) {
            System.out.println("No saved payment methods. Add one first (option 6).");
            return;
        }
        System.out.println("Saved payment methods:");
        for (int i = 0; i < methods.size(); i++) {
            System.out.println("  [" + (i + 1) + "] " + methods.get(i));
        }
        System.out.print("Select method number: ");
        int mIdx = parseInt(sc.nextLine()) - 1;
        if (mIdx < 0 || mIdx >= methods.size()) { System.out.println("Invalid selection."); return; }

        PaymentTransaction tx = paymentService.processPayment(booking, methods.get(mIdx));
        if (tx != null && tx.getStatus() == PaymentStatus.SUCCESS) {
            System.out.println("\nPayment successful!");
            System.out.println("  " + tx);
            Receipt r = paymentService.getLastReceipt();
            if (r != null) System.out.println("  " + r);
        } else {
            System.out.println("Payment was not successful. Check payment history for details.");
        }
    }

    // ── UC4 ─────────────────────────────────────────────────────────────────
    private void viewBookingHistory() {
        System.out.println("\n--- Booking History for " + client1.getName() + " ---");
        List<Booking> bookings = bookingService.getBookingsForClient(client1.getId());
        if (bookings.isEmpty()) { System.out.println("No bookings found."); return; }
        bookings.forEach(b -> {
            System.out.println("  ID: " + b.getBookingId()
                    + " | Service: " + b.getService().getTitle()
                    + " | Status: " + b.getStatus()
                    + " | Created: " + b.getCreatedAt().toLocalDate());
        });
    }

    // ── UC6 ─────────────────────────────────────────────────────────────────
    private void managePaymentMethods(Scanner sc) {
        System.out.println("\n--- Manage Payment Methods (as " + client1.getName() + ") ---");
        System.out.println("  a) Add Credit Card");
        System.out.println("  b) Add PayPal");
        System.out.println("  c) Add Bank Transfer");
        System.out.println("  d) List methods");
        System.out.println("  e) Remove method");
        System.out.print("Choice: ");
        String choice = sc.nextLine().trim().toLowerCase();

        switch (choice) {
            case "a" -> addCreditCard(sc);
            case "b" -> addPayPal(sc);
            case "c" -> addBankTransfer(sc);
            case "d" -> {
                List<PaymentMethod> methods = paymentMethodService.listMethods(client1);
                if (methods.isEmpty()) { System.out.println("No methods saved."); }
                else methods.forEach(m -> System.out.println("  [" + m.getMethodId() + "] " + m));
            }
            case "e" -> {
                paymentMethodService.listMethods(client1)
                        .forEach(m -> System.out.println("  [" + m.getMethodId() + "] " + m));
                System.out.print("Enter method ID to remove: ");
                String id = sc.nextLine().trim();
                paymentMethodService.removeMethod(client1, id);
            }
            default -> System.out.println("Invalid choice.");
        }
    }

    private void addCreditCard(Scanner sc) {
        System.out.print("Card number (16 digits): "); String num = sc.nextLine().trim();
        System.out.print("Expiry (MM/yy): ");           String exp = sc.nextLine().trim();
        System.out.print("CVV (3-4 digits): ");         String cvv = sc.nextLine().trim();
        String id = "PM" + System.currentTimeMillis();
        CreditCardMethod cc = new CreditCardMethod(client1, id, num, exp, cvv);
        if (!cc.validate()) { System.out.println("Invalid credit card details."); return; }
        paymentMethodService.addMethod(client1, cc);
        System.out.println("Credit card added. ID: " + id);
    }

    private void addPayPal(Scanner sc) {
        System.out.print("PayPal email: "); String email = sc.nextLine().trim();
        String id = "PM" + System.currentTimeMillis();
        PayPalMethod pp = new PayPalMethod(client1, id, email);
        if (!pp.validate()) { System.out.println("Invalid PayPal email."); return; }
        paymentMethodService.addMethod(client1, pp);
        System.out.println("PayPal added. ID: " + id);
    }

    private void addBankTransfer(Scanner sc) {
        System.out.print("Account number (8-17 digits): "); String acct   = sc.nextLine().trim();
        System.out.print("Routing number (9 digits): ");    String routing = sc.nextLine().trim();
        String id = "PM" + System.currentTimeMillis();
        BankTransferMethod bt = new BankTransferMethod(client1, id, acct, routing);
        if (!bt.validate()) { System.out.println("Invalid bank transfer details."); return; }
        paymentMethodService.addMethod(client1, bt);
        System.out.println("Bank transfer added. ID: " + id);
    }

    // ── UC7 ─────────────────────────────────────────────────────────────────
    private void viewPaymentHistory() {
        System.out.println("\n--- Payment History for " + client1.getName() + " ---");
        List<PaymentTransaction> history = paymentService.getPaymentHistory(client1);
        if (history.isEmpty()) { System.out.println("No payment transactions found."); return; }
        history.forEach(tx -> System.out.println("  " + tx));
    }

    // ── UC11 ────────────────────────────────────────────────────────────────
    private void adminApproveConsultant(Scanner sc) {
        System.out.println("\n--- Admin: Approve/Reject Consultant Registration ---");
        List<Consultant> pending = consultantRepo.findByStatus(RegistrationStatus.PENDING);
        if (pending.isEmpty()) {
            System.out.println("No consultants pending registration.");
            consultantRepo.findAll()
                    .forEach(c -> System.out.println("  " + c.getName() + " — " + c.getRegistrationStatus()));
            return;
        }
        pending.forEach(c -> System.out.println("  ID: " + c.getId()
                + " | Name: " + c.getName()
                + " | Bio: " + c.getBio()));
        System.out.print("Enter Consultant ID: ");
        String id = sc.nextLine().trim();
        System.out.print("Approve? (yes/no): ");
        boolean approve = sc.nextLine().trim().equalsIgnoreCase("yes");
        admin.approveConsultantRegistration(id, approve, consultantRepo);
    }

    // ── UC12 ────────────────────────────────────────────────────────────────
    private void adminSetPolicy(Scanner sc) {
        System.out.println("\n--- Admin: Set Cancellation Policy ---");
        System.out.println("  1) Default policy (allow cancel on REQUESTED/CONFIRMED/PENDING_PAYMENT, no fee)");
        System.out.println("  2) Strict policy  (no cancellations allowed)");
        System.out.print("Choice: ");
        String choice = sc.nextLine().trim();
        switch (choice) {
            case "1" -> {
                admin.setCancellationPolicy(new DefaultCancellationPolicy());
                System.out.println("Default cancellation policy set.");
            }
            case "2" -> {
                admin.setCancellationPolicy(new StrictCancellationPolicy());
                System.out.println("Strict cancellation policy set.");
            }
            default -> System.out.println("Invalid choice.");
        }
    }

    // ── UC10 ────────────────────────────────────────────────────────────────
    private void completeBooking(Scanner sc) {
        System.out.println("\n--- Complete Booking (as Consultant c1) ---");
        List<Booking> paid = bookingService.getBookingsForConsultant(c1.getId())
                .stream()
                .filter(b -> b.getStatus() == BookingStatus.PAID)
                .collect(Collectors.toList());

        if (paid.isEmpty()) { System.out.println("No PAID bookings to complete."); return; }
        paid.forEach(b -> System.out.println("  " + b));
        System.out.print("Enter Booking ID to complete: ");
        String bookingId = sc.nextLine().trim();
        try {
            bookingService.completeBooking(bookingId);
            System.out.println("Booking " + bookingId + " marked as COMPLETED.");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    // ── Utility ─────────────────────────────────────────────────────────────
    private int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return -1; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ENTRY POINT
    // ─────────────────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        Main app = new Main();
        app.setupDemoData();
        app.runDemoScenario();
        app.run();
    }
}
