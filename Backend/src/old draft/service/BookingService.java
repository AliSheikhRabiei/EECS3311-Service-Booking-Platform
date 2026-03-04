package service;

import model.*;
import payment.PaymentTransaction;
import policy.PolicyManager;
import repo.BookingRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class BookingService {
    private final BookingRepository   bookingRepo;
    private final AvailabilityService availService;
    private final NotificationService notifService;
    private final PolicyManager       policyManager;

    private int bookingCounter = 1;

    public BookingService(BookingRepository bookingRepo,
                          AvailabilityService availService,
                          NotificationService notifService,
                          PolicyManager policyManager) {
        this.bookingRepo   = bookingRepo;
        this.availService  = availService;
        this.notifService  = notifService;
        this.policyManager = policyManager;
    }

    /** UC2 — Create a booking */
    public Booking createBooking(Client client, Service service, TimeSlot slot) {
        if (!slot.isAvailable()) {
            throw new IllegalArgumentException("Time slot is not available: " + slot);
        }
        // Check consultant is approved
        if (service.getConsultant().getRegistrationStatus() != RegistrationStatus.APPROVED) {
            throw new IllegalStateException("Consultant is not approved: "
                    + service.getConsultant().getName());
        }

        String bookingId = "B" + (bookingCounter++);
        Booking booking = new Booking(bookingId, client, service, slot);
        // Reserve slot immediately to prevent double-booking
        slot.reserve();
        bookingRepo.save(booking);

        notifService.notify(service.getConsultant(),
                "New booking request " + bookingId + " from " + client.getName()
                        + " for service: " + service.getTitle());
        System.out.println("[BookingService] Booking created: " + bookingId);
        return booking;
    }

    /** UC9 — Consultant accepts or rejects */
    public void consultantDecision(String bookingId, boolean accept, String reason) {
        Booking booking = getBooking(bookingId);
        if (booking == null) throw new IllegalArgumentException("Booking not found: " + bookingId);

        if (accept) {
            booking.confirm();
            notifService.notify(booking.getClient(),
                    "Your booking " + bookingId + " for '" + booking.getService().getTitle()
                            + "' has been CONFIRMED. Please proceed with payment.");
        } else {
            booking.reject(reason);
            booking.getSlot().release();
            notifService.notify(booking.getClient(),
                    "Your booking " + bookingId + " was REJECTED. Reason: " + reason);
        }
        bookingRepo.save(booking);
    }

    /** UC3 — Cancel booking */
    public boolean cancelBooking(String bookingId, String reason) {
        Booking booking = getBooking(bookingId);
        if (booking == null) throw new IllegalArgumentException("Booking not found: " + bookingId);

        boolean canCancel = policyManager.getCancellationPolicy()
                .canCancel(booking, LocalDateTime.now());
        if (!canCancel) {
            System.out.println("[BookingService] Cancellation not allowed by policy for booking: " + bookingId);
            return false;
        }

        double fee = policyManager.getCancellationPolicy()
                .cancellationFee(booking, LocalDateTime.now());
        booking.cancel(reason);
        bookingRepo.save(booking);

        notifService.notify(booking.getClient(),
                "Your booking " + bookingId + " has been CANCELLED. Fee: $" + fee);
        notifService.notify(booking.getService().getConsultant(),
                "Booking " + bookingId + " was cancelled. Reason: " + reason);
        return true;
    }

    /** UC10 — Complete booking */
    public void completeBooking(String bookingId) {
        Booking booking = getBooking(bookingId);
        if (booking == null) throw new IllegalArgumentException("Booking not found: " + bookingId);

        booking.complete();
        bookingRepo.save(booking);

        notifService.notify(booking.getClient(),
                "Your booking " + bookingId + " has been marked COMPLETED. Thank you!");
    }

    /** UC4 — Booking history for client */
    public List<Booking> getBookingsForClient(String clientId) {
        return bookingRepo.findAll().stream()
                .filter(b -> b.getClient().getId().equals(clientId))
                .collect(Collectors.toList());
    }

    public List<Booking> getBookingsForConsultant(String consultantId) {
        return bookingRepo.findAll().stream()
                .filter(b -> b.getService().getConsultant().getId().equals(consultantId))
                .collect(Collectors.toList());
    }

    public Booking getBooking(String id) {
        return bookingRepo.findById(id);
    }
}
