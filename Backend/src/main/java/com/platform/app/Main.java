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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

/**
 * Entry point.  Bootstraps the system, runs an automated happy-path demo,
 * then drops into a simple interactive menu.
 */
public class Main {

    // ── Shared services ──────────────────────────────────────────────────────
    private final PolicyManager policyManager;
    private final NotificationService notificationService;
    private final ServiceCatalog catalog;
    private final AvailabilityService availabilityService;
    private final BookingRepository bookingRepository;
    private final PaymentService paymentService;
    private final PaymentMethodService paymentMethodService;
    private final BookingService bookingService;
    private final Admin admin;

    // ── Demo fixtures ────────────────────────────────────────────────────────
    private Client demoClient;
    private Consultant demoConsultant;
    private Service demoService1, demoService2;
    private TimeSlot demoSlot1, demoSlot2;

    public Main() {
        policyManager = PolicyManager.getInstance();
        notificationService = new NotificationService();
        catalog = new ServiceCatalog();
        availabilityService = new AvailabilityService();
        bookingRepository = new BookingRepository();
        paymentService = new PaymentService(policyManager, notificationService);
        paymentMethodService = new PaymentMethodService();
        bookingService = new BookingService(
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
                "Resume review, mock interviews and career planning", 45, 75.0, demoConsultant);

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

    /*private void runMenu() {
        Scanner sc = new Scanner(System.in);
        boolean running = true;

        while (running) {
            printMenu();
            System.out.print("Choice: ");
            String input = sc.nextLine().trim();

            switch (input) {
                case "1" -> menuBrowseServices();
                case "2" -> menuRequestBooking(sc);
                case "3" -> menuConsultantDecision(sc);
                case "4" -> menuProcessPayment(sc);
                case "5" -> menuViewBookingHistory();
                case "6" -> menuManagePaymentMethods(sc);
                case "7" -> menuViewPaymentHistory();
                case "8" -> menuAdminApprove(sc);
                case "9" -> menuAdminPolicy(sc);
                case "10" -> menuCompleteBooking(sc);
                case "11" -> menuCancelBooking(sc);
                case "12" -> menuManageAvailability(sc);
                case "0" -> {
                    System.out.println("Goodbye!");
                    running = false;
                }
                default -> System.out.println("  Invalid choice.");
            }
        }
        sc.close();
    }*/

    private void runMenu() {
        Scanner sc = new Scanner(System.in);

        boolean running = true;

        while (running) {
            System.out.println("Choose user type 1.Admin 2.Consultant 3.Client 0.Exit");

            switch (sc.nextLine().trim()) {
                case "1" -> runAdminMenu(sc);
                case "2" -> runConsulMenu(sc);
                case "3" -> runClientMenu(sc);
                case "0" -> {
                    System.out.println("Goodbye!");
                    running = false;

                }
                default -> System.out.println("Invalid choice");

            }
        }
    }

    private void runAdminMenu(Scanner sc) {


        boolean running = true;

        while (running) {
            printAdminMenu();
            System.out.print("Choice: ");
            String input = sc.nextLine().trim();

            switch (input) {

                case "8" -> menuAdminApprove(sc);
                case "9" -> menuAdminPolicy(sc);
                case "0" -> {
                    runMenu();
                   running = false;
                }
                default -> System.out.println("  Invalid choice.");
            }
        }
        sc.close();
    }
    private void printAdminMenu() {

        System.out.println("""

                ┌─────────────────────────────────────────────┐
                │   Service Booking & Consulting Platform     │
                ├─────────────────────────────────────────────┤
                │  8.  Admin: Approve Consultant (UC11)       │
                │  9.  Admin: Set Cancellation Policy (UC12)  │
                │  0.  Exit                                   │
                └─────────────────────────────────────────────┘""");
    }



    private void runConsulMenu(Scanner sc) {


        boolean running = true;

        while (running) {
            printConsulMenu();
            System.out.print("Choice: ");
            String input = sc.nextLine().trim();

            switch (input) {
                case "3" -> menuConsultantDecision(sc);
                case "10" -> menuCompleteBooking(sc);
                case "12" -> menuManageAvailability(sc);
                case "0" -> {
                   runMenu();
                  running = false;
                 return;
                }
                default -> System.out.println("  Invalid choice.");
            }
        }
        sc.close();

    }
    private void printConsulMenu() {
        System.out.println("""

                ┌─────────────────────────────────────────────┐
                │   Service Booking & Consulting Platform     │
                ├─────────────────────────────────────────────┤
                │  3.  Consultant Accept/Reject (UC9)         │
                │  10. Complete Booking (UC10)                │
                │  12. Manage Availability / Slots (UC8)      │
                │  0.  Exit                                   │
                └─────────────────────────────────────────────┘""");
    }

    private void runClientMenu(Scanner sc) {
        boolean running = true;

        while (running) {
            printClientMenu();
            System.out.print("Choice: ");
            String input = sc.nextLine().trim();

            switch (input) {
                case "1" -> menuBrowseServices();
                case "2" -> menuRequestBooking(sc);
                case "4" -> menuProcessPayment(sc);
                case "5" -> menuViewBookingHistory();
                case "6" -> menuManagePaymentMethods(sc);
                case "7" -> menuViewPaymentHistory();
                case "11" -> menuCancelBooking(sc);
                case "0" -> {
                   runMenu();
                   running = false;
                   return;
                }
                default -> System.out.println("  Invalid choice.");
            }
        }
        sc.close();
    }
    private void printClientMenu() {
        System.out.println("""

                ┌─────────────────────────────────────────────┐
                │   Service Booking & Consulting Platform     │
                ├─────────────────────────────────────────────┤
                │  1.  Browse Services (UC1)                  │
                │  2.  Request Booking  (UC2)                 │
                │  4.  Process Payment  (UC5)                 │
                │  5.  View Booking History (UC4)             │
                │  6.  Manage Payment Methods (UC6)           │
                │  7.  View Payment History (UC7)             │
                │  11. Cancel Booking (UC3)                   │
                │  0.  Exit                                   │
                └─────────────────────────────────────────────┘""");
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
                │  11. Cancel Booking (UC3)                   │
                │  12. Manage Availability / Slots (UC8)      │
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
        System.out.println("\n── Admin: Configure Policies (UC12) ──────────");
        System.out.println("  -- Cancellation Policy --");
        System.out.println("  1) Default  (allow cancel on REQUESTED/CONFIRMED/PENDING_PAYMENT/PAID, no fee)");
        System.out.println("  2) Strict   (no cancellations allowed at all)");
        System.out.println();
        System.out.println("  -- Refund Policy --");
        System.out.println("  3) Default  (80% refund on paid bookings)");
        System.out.println("  4) Full     (100% refund on paid bookings)");
        System.out.println("  5) No Refund (0% — client forfeits payment on cancellation)");
        System.out.print("  Choice: ");
        switch (sc.nextLine().trim()) {
            case "1" -> {
                admin.setCancellationPolicy(new DefaultCancellationPolicy());
                System.out.println("  ✓ Cancellation policy → Default (cancel allowed, no fee).");
            }
            case "2" -> {
                admin.setCancellationPolicy(new CancellationPolicy() {
                    @Override public boolean canCancel(com.platform.domain.Booking b, java.time.LocalDateTime now) { return false; }
                    @Override public double cancellationFee(com.platform.domain.Booking b, java.time.LocalDateTime now) { return b.getService().getPrice(); }
                });
                System.out.println("  ✓ Cancellation policy → Strict (no cancellations).");
            }
            case "3" -> {
                admin.setRefundPolicy(new DefaultRefundPolicy());
                System.out.println("  ✓ Refund policy → Default (80% refund).");
            }
            case "4" -> {
                admin.setRefundPolicy(new FullRefundPolicy());
                System.out.println("  ✓ Refund policy → Full (100% refund).");
            }
            case "5" -> {
                admin.setRefundPolicy((tx, now) -> 0.0);
                System.out.println("  ✓ Refund policy → No Refund (0% — client forfeits payment).");
            }
            default -> System.out.println("  Invalid choice.");
        }
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

    private void menuCancelBooking(Scanner sc) {
        System.out.println("\n── Cancel Booking (UC3) ──────────────────────");

        // Show all active (non-terminal) bookings for the demo client
        List<Booking> active = bookingService.getBookingsForClient(demoClient.getId())
                .stream()
                .filter(b -> {
                    String s = b.getState().getClass().getSimpleName();
                    return s.equals("RequestedState") || s.equals("ConfirmedState")
                            || s.equals("PendingPaymentState") || s.equals("PaidState");
                })
                .collect(Collectors.toList());

        if (active.isEmpty()) {
            System.out.println("  No active bookings to cancel.");
            return;
        }

        System.out.println("  Active bookings:");
        active.forEach(b -> System.out.printf("  %-8s  %-20s  %s%n",
                b.getBookingId(), b.getService().getTitle(), b.getStateName()));

        System.out.print("  Enter Booking ID to cancel: ");
        String bid = sc.nextLine().trim();
        Booking booking = bookingService.getBooking(bid);
        if (booking == null) {
            System.out.println("  Booking not found.");
            return;
        }

        // Warn user if booking is PAID — they will receive a refund
        boolean isPaid = booking.getState().getClass().getSimpleName().equals("PaidState");
        if (isPaid) {
            System.out.println("  ⚠  This booking is PAID. Cancelling will trigger a refund (80% of amount).");
        }

        System.out.print("  Reason for cancellation: ");
        String reason = sc.nextLine().trim();
        if (reason.isBlank()) reason = "Client requested cancellation";

        boolean cancelled = bookingService.cancelBooking(bid, reason);
        if (cancelled) {
            System.out.println("  ✓ Booking " + bid + " has been CANCELLED.");
            System.out.println("  ✓ Time slot has been freed.");
            if (isPaid) {
                // Show the refund transaction that was just created
                paymentService.getPaymentHistory(demoClient).stream()
                        .filter(t -> t.getStatus() == PaymentStatus.REFUNDED)
                        .reduce((a, b) -> b)   // last refund = the one just created
                        .ifPresent(t -> System.out.printf(
                                "  ✓ Refund issued: $%.2f  (tx: %s)%n",
                                t.getAmount(), t.getTransactionId()));
            }
        } else {
            System.out.println("  ✗ Cancellation blocked by current cancellation policy.");
            System.out.println("    (Booking state: " + booking.getStateName() + ")");
        }
    }

    private void menuManageAvailability(Scanner sc) {
        System.out.println("\n── Manage Availability / Time Slots (UC8) ────");
        System.out.println("  Consultant: " + demoConsultant.getName());

        List<TimeSlot> all = availabilityService.listAllSlots(demoConsultant);
        System.out.println("  Current slots (" + all.size() + "):");
        if (all.isEmpty()) {
            System.out.println("    (none)");
        } else {
            for (int i = 0; i < all.size(); i++) {
                TimeSlot s = all.get(i);
                System.out.printf("    [%d] %s  —  %s%n",
                        i + 1, s.getSlotId(), s.isAvailable() ? "AVAILABLE" : "RESERVED");
            }
        }

        System.out.println();
        System.out.println("  a) Add a single 1-hour slot  (enter start datetime)");
        System.out.println("  b) Add a time block          (start datetime + number of hours → auto-split into 1-hour slots)");
        System.out.println("  c) Remove a slot");
        System.out.print("  Choice: ");
        String choice = sc.nextLine().trim().toLowerCase();

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        switch (choice) {
            case "a" -> {
                System.out.println("  Format: yyyy-MM-dd HH:mm  (e.g. 2026-04-01 09:00)");
                System.out.print("  Start datetime: ");
                String raw = sc.nextLine().trim();
                try {
                    LocalDateTime start = LocalDateTime.parse(raw, fmt);
                    LocalDateTime end   = start.plusHours(1);
                    TimeSlot slot = new TimeSlot(start, end);
                    availabilityService.addTimeSlot(demoConsultant, slot);
                    System.out.println("  ✓ 1-hour slot added: " + slot.getSlotId());
                } catch (DateTimeParseException e) {
                    System.out.println("  ✗ Invalid format. Use yyyy-MM-dd HH:mm  e.g. 2026-04-01 09:00");
                } catch (IllegalArgumentException e) {
                    System.out.println("  ✗ Error: " + e.getMessage());
                }
            }
            case "b" -> {
                System.out.println("  Format: yyyy-MM-dd HH:mm  (e.g. 2026-04-01 09:00)");
                System.out.print("  Start datetime: ");
                String rawStart = sc.nextLine().trim();
                System.out.print("  Number of hours for this availability window (e.g. 10): ");
                String rawHours = sc.nextLine().trim();
                try {
                    LocalDateTime start = LocalDateTime.parse(rawStart, fmt);
                    int hours = Integer.parseInt(rawHours);
                    if (hours < 1) { System.out.println("  ✗ Hours must be at least 1."); return; }
                    LocalDateTime end = start.plusHours(hours);
                    int added = availabilityService.addTimeSlotBlock(demoConsultant, start, end);
                    System.out.println("  ✓ Block split into " + added + " one-hour slot(s).");
                    // Print the newly added slots
                    availabilityService.listAllSlots(demoConsultant).stream()
                            .filter(TimeSlot::isAvailable)
                            .skip(Math.max(0, availabilityService.listAllSlots(demoConsultant).size() - added))
                            .forEach(s -> System.out.println("    → " + s.getSlotId()));
                } catch (DateTimeParseException e) {
                    System.out.println("  ✗ Invalid datetime format. Use yyyy-MM-dd HH:mm");
                } catch (NumberFormatException e) {
                    System.out.println("  ✗ Invalid number of hours.");
                } catch (IllegalArgumentException e) {
                    System.out.println("  ✗ Error: " + e.getMessage());
                }
            }
            case "c" -> {
                if (all.isEmpty()) { System.out.println("  No slots to remove."); return; }
                System.out.print("  Enter slot number to remove: ");
                try {
                    int idx = Integer.parseInt(sc.nextLine().trim()) - 1;
                    if (idx < 0 || idx >= all.size()) { System.out.println("  Invalid number."); return; }
                    TimeSlot toRemove = all.get(idx);
                    if (!toRemove.isAvailable()) {
                        System.out.println("  ✗ Cannot remove a RESERVED slot (a booking exists for it).");
                        return;
                    }
                    availabilityService.removeTimeSlot(demoConsultant, toRemove.getSlotId());
                    System.out.println("  ✓ Slot removed: " + toRemove.getSlotId());
                } catch (NumberFormatException e) {
                    System.out.println("  ✗ Invalid number.");
                }
            }
            default -> System.out.println("  Invalid choice.");
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
