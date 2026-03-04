package com.platform.domain;

import com.platform.payment.PaymentTransaction;
import com.platform.state.BookingState;
import com.platform.state.RequestedState;

import java.time.LocalDateTime;

/**
 * Central domain object representing a booking request and its full lifecycle.
 *
 * <p>Uses the <strong>State pattern</strong>: every public action is delegated to
 * the current {@link BookingState}, which enforces which transitions are legal.
 * Invalid transitions throw {@link IllegalStateException} via
 * {@code AbstractBookingState}'s default-deny behaviour.</p>
 *
 * <p>Lifecycle (happy path):
 * REQUESTED → CONFIRMED → PENDING_PAYMENT → PAID → COMPLETED</p>
 */
public class Booking {

    private final String          bookingId;
    private final Client          client;
    private final Service         service;
    private final TimeSlot        slot;
    private final LocalDateTime   createdAt;
    private BookingState          state;

    public Booking(String bookingId, Client client, Service service, TimeSlot slot) {
        if (bookingId == null || bookingId.isBlank()) throw new IllegalArgumentException("bookingId must not be blank.");
        if (client  == null) throw new IllegalArgumentException("client must not be null.");
        if (service == null) throw new IllegalArgumentException("service must not be null.");
        if (slot    == null) throw new IllegalArgumentException("slot must not be null.");
        this.bookingId = bookingId;
        this.client    = client;
        this.service   = service;
        this.slot      = slot;
        this.createdAt = LocalDateTime.now();
        this.state     = new RequestedState();   // initial state
    }

    // ── Public lifecycle actions (all delegate to current state) ─────────────

    /** Initialises the booking as REQUESTED (idempotent; state is set in constructor). */
    public void request() {
        // Booking is already in RequestedState from construction.
        // This method exists for explicit lifecycle documentation and potential future hooks.
        System.out.println("[Booking] " + bookingId + " is in state: " + getStateName());
    }

    /** Consultant accepts → CONFIRMED (then service layer moves to PENDING_PAYMENT). */
    public void confirm() { state.confirm(this); }

    /** Consultant rejects → REJECTED. */
    public void reject(String reason) { state.reject(this, reason); }

    /** Client or system cancels → CANCELLED. */
    public void cancel(String reason) { state.cancel(this, reason); }

    /** Called by MarkPaidHandler on successful payment → PAID. */
    public void paymentSuccessful(PaymentTransaction tx) { state.paymentSuccessful(this, tx); }

    /** Consultant marks session done → COMPLETED (only allowed from PAID). */
    public void complete() { state.complete(this); }

    // ── State maintenance (public so com.platform.state and payment packages can call it) ─

    public void setState(BookingState newState) {
        if (newState == null) throw new IllegalArgumentException("State must not be null.");
        this.state = newState;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String        getBookingId() { return bookingId; }
    public Client        getClient()    { return client; }
    public Service       getService()   { return service; }
    public TimeSlot      getSlot()      { return slot; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public BookingState  getState()     { return state; }

    /** Human-readable status name derived from the current state class. */
    public String getStateName() {
        return state.getClass().getSimpleName().replace("State", "").toUpperCase();
    }

    @Override
    public String toString() {
        return String.format("Booking[id=%s, client=%s, service='%s', status=%s]",
                bookingId, client.getName(), service.getTitle(), getStateName());
    }
}
