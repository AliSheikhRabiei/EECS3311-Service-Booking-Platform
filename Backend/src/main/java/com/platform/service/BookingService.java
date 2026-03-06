package com.platform.service;

import com.platform.domain.Booking;
import com.platform.domain.Client;
import com.platform.domain.Consultant;
import com.platform.domain.RegistrationStatus;
import com.platform.domain.Service;
import com.platform.domain.TimeSlot;
import com.platform.notify.NotificationService;
import com.platform.payment.PaymentService;
import com.platform.policy.PolicyManager;
import com.platform.repository.BookingRepository;
import com.platform.state.CancelledState;
import com.platform.state.PaidState;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Core booking workflow coordinator (UC2–UC4, UC9–UC10).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Create bookings after validating availability and consultant approval.</li>
 *   <li>Coordinate consultant decisions (confirm / reject).</li>
 *   <li>Apply cancellation policy; trigger refund if booking was already paid.</li>
 *   <li>Enforce the "must be PAID before COMPLETED" rule.</li>
 * </ul>
 * </p>
 */
public class BookingService {

    private final BookingRepository   bookingRepository;
    private final AvailabilityService availabilityService;
    private final NotificationService notificationService;
    private final PaymentService      paymentService;
    private final PolicyManager       policyManager;


    private final AtomicInteger idCounter = new AtomicInteger(1);

    public BookingService(BookingRepository   bookingRepository,
                          AvailabilityService availabilityService,
                          NotificationService notificationService,
                          PaymentService      paymentService,
                          PolicyManager       policyManager) {
        this.bookingRepository   = bookingRepository;
        this.availabilityService = availabilityService;
        this.notificationService = notificationService;
        this.paymentService      = paymentService;
        this.policyManager       = policyManager;
    }

    // ── UC2 – Create Booking ─────────────────────────────────────────────────

    /**
     * Creates a new booking in REQUESTED state after checking:
     * <ol>
     *   <li>The consultant is APPROVED.</li>
     *   <li>The chosen slot is still available.</li>
     * </ol>
     *
     * @throws IllegalStateException if the consultant is not approved
     * @throws IllegalArgumentException if the slot is not available
     */
    public Booking createBooking(Client client, Service service, TimeSlot slot) {
        if (client  == null) throw new IllegalArgumentException("client must not be null.");
        if (service == null) throw new IllegalArgumentException("service must not be null.");
        if (slot    == null) throw new IllegalArgumentException("slot must not be null.");

        Consultant consultant = service.getConsultant();
        if (consultant.getRegistrationStatus() != RegistrationStatus.APPROVED) {
            throw new IllegalStateException(
                    "Consultant " + consultant.getName() + " is not approved (status: "
                    + consultant.getRegistrationStatus() + ").");
        }
        if (!slot.isAvailable()) {
            throw new IllegalArgumentException(
                    "The selected time slot is not available: " + slot);
        }

        String bookingId = "BK-" + idCounter.getAndIncrement();
        Booking booking = new Booking(bookingId, client, service, slot);
        slot.reserve();                            // lock the slot immediately
        bookingRepository.save(booking);

        notificationService.notify(consultant,
                "New booking request " + bookingId + " from " + client.getName()
                + " for service '" + service.getTitle() + "'.");

        System.out.println("[BookingService] Created: " + booking);
        return booking;
    }

    // ── UC9 – Confirm Booking (Consultant accepts) ───────────────────────────

    /**
     * Confirms a booking: REQUESTED → CONFIRMED.
     * Payment will then be initiated via {@link PaymentService#processPayment}.
     */
    public void confirmBooking(String bookingId) {
        Booking booking = requireBooking(bookingId);
        booking.confirm();
        bookingRepository.save(booking);

        notificationService.notify(booking.getClient(),
                "Your booking " + bookingId + " for '" + booking.getService().getTitle()
                + "' has been CONFIRMED. Please proceed with payment.");
        System.out.println("[BookingService] Confirmed: " + booking);
    }

    /**
     * Rejects a booking: REQUESTED → REJECTED.
     */
    public void rejectBooking(String bookingId, String reason) {
        Booking booking = requireBooking(bookingId);
        booking.reject(reason);
        bookingRepository.save(booking);

        notificationService.notify(booking.getClient(),
                "Your booking " + bookingId + " was REJECTED. Reason: " + reason);
        System.out.println("[BookingService] Rejected: " + booking);
    }

    // ── UC10 – Complete Booking ──────────────────────────────────────────────

    /**
     * Marks a booking as COMPLETED; only allowed if the booking is in PAID state.
     * The State pattern enforces this – any other state will throw {@link IllegalStateException}.
     */
    public void completeBooking(String bookingId) {
        Booking booking = requireBooking(bookingId);
        booking.complete();
        bookingRepository.save(booking);

        notificationService.notify(booking.getClient(),
                "Your consulting session for booking " + bookingId + " is COMPLETED. Thank you!");
        System.out.println("[BookingService] Completed: " + booking);
    }

    // ── UC3 – Cancel Booking ─────────────────────────────────────────────────

    /**
     * Cancels a booking, applying the configured {@link com.platform.policy.CancellationPolicy}.
     *
     * <ul>
     *   <li>If the policy disallows cancellation, returns {@code false}.</li>
     *   <li>If the booking was PAID, triggers {@link PaymentService#refund(Booking)}
     *       (which also forces the state to CANCELLED).</li>
     *   <li>Otherwise calls {@code booking.cancel(reason)} through the state machine.</li>
     * </ul>
     *
     * @return {@code true} if cancellation succeeded; {@code false} if the policy blocked it
     */
    public boolean cancelBooking(String bookingId, String reason) {
        Booking booking = requireBooking(bookingId);

        boolean canCancel = policyManager.getCancellationPolicy()
                .canCancel(booking, LocalDateTime.now());
        if (!canCancel) {
            System.out.println("[BookingService] Cancellation denied by policy for booking: " + bookingId);
            return false;
        }

        double fee = policyManager.getCancellationPolicy()
                .cancellationFee(booking, LocalDateTime.now());
        if (fee > 0) {
            System.out.println("[BookingService] Cancellation fee applied: $" + String.format("%.2f", fee));
        }

        boolean wasPaid = booking.getState() instanceof PaidState;
        if (wasPaid) {
            // Refund triggers state → CANCELLED internally
            paymentService.refund(booking);
        } else {
            booking.cancel(reason);
        }
        bookingRepository.save(booking);

        notificationService.notify(booking.getClient(),
                "Your booking " + bookingId + " has been CANCELLED."
                + (fee > 0 ? " Cancellation fee: $" + String.format("%.2f", fee) : ""));
        notificationService.notify(booking.getService().getConsultant(),
                "Booking " + bookingId + " was cancelled. Reason: " + reason);
        return true;
    }

    // ── UC4 – View Booking History ───────────────────────────────────────────

    public Booking getBooking(String bookingId) {
        return bookingRepository.findById(bookingId);
    }

    /** UC4 – Returns all bookings for a specific client. */
    public List<Booking> getBookingsForClient(String clientId) {
        return bookingRepository.findAll().stream()
                .filter(b -> b.getClient().getId().equals(clientId))
                .collect(Collectors.toList());
    }

    /** Returns all bookings for a specific consultant. */
    public List<Booking> getBookingsForConsultant(Consultant consultant) {
        return bookingRepository.findByConsultant(consultant);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private Booking requireBooking(String bookingId) {
        Booking b = bookingRepository.findById(bookingId);
        if (b == null) throw new IllegalArgumentException("Booking not found: " + bookingId);
        return b;
    }
}
