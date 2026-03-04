package model;

import model.state.BookingState;
import model.state.RequestedState;
import payment.PaymentTransaction;

import java.time.LocalDateTime;

public class Booking {
    private String bookingId;
    private Client client;
    private Service service;
    private TimeSlot slot;
    private BookingStatus status;
    private LocalDateTime createdAt;
    private BookingState state;

    public Booking(String bookingId, Client client, Service service, TimeSlot slot) {
        this.bookingId = bookingId;
        this.client    = client;
        this.service   = service;
        this.slot      = slot;
        this.status    = BookingStatus.REQUESTED;
        this.createdAt = LocalDateTime.now();
        this.state     = new RequestedState();
    }

    // ── Public actions delegating to State ──────────────────────────────────
    public void confirm()                           { state.confirm(this); }
    public void reject(String reason)               { state.reject(this, reason); }
    public void cancel(String reason)               { state.cancel(this, reason); }
    public void paymentSuccessful(PaymentTransaction tx) { state.paymentSuccessful(this, tx); }
    public void complete()                          { state.complete(this); }

    // ── State maintenance (public so model.state subpackage can access) ─────
    public void setState(BookingState s)    { this.state  = s; }
    public void setStatus(BookingStatus st) { this.status = st; }

    // ── Getters ─────────────────────────────────────────────────────────────
    public String        getBookingId() { return bookingId; }
    public Client        getClient()    { return client; }
    public Service       getService()   { return service; }
    public TimeSlot      getSlot()      { return slot; }
    public BookingStatus getStatus()    { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public BookingState  getState()     { return state; }

    @Override
    public String toString() {
        return String.format("Booking[id=%s, client=%s, service=%s, status=%s, slot=%s]",
                bookingId, client.getName(), service.getTitle(), status, slot);
    }
}
