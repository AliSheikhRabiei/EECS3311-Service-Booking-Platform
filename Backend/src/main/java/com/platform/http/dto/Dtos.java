package com.platform.http.dto;

import com.platform.domain.Booking;
import com.platform.domain.Consultant;
import com.platform.domain.Service;
import com.platform.domain.TimeSlot;
import com.platform.db.PaymentTransactionRepository;
import com.platform.db.SlotRepository;
import com.platform.payment.PaymentMethod;
import com.platform.payment.PaymentTransaction;

/**
 * All API response DTOs in one file for simplicity.
 * Each DTO is a flat, Gson-safe POJO with no circular references.
 */
public class Dtos {

    // ── Service ───────────────────────────────────────────────────────────────

    public static class ServiceDto {
        public String serviceId;
        public String title;
        public String description;
        public int    durationMin;
        public double price;
        public String consultantId;
        public String consultantName;

        public ServiceDto(Service s) {
            this.serviceId      = s.getServiceId();
            this.title          = s.getTitle();
            this.description    = s.getDescription();
            this.durationMin    = s.getDurationMin();
            this.price          = s.getPrice();
            this.consultantId   = s.getConsultant().getId();
            this.consultantName = s.getConsultant().getName();
        }
    }

    public static class CancellationResultDto {
        public BookingDto booking;
        public String cancellationPolicy;
        public String refundPolicy;
        public boolean bookingWasPaid;
        public boolean refundProcessed;
        public double cancellationFee;
        public double refundAmount;
        public String message;

        public CancellationResultDto(Booking booking) {
            this.booking = new BookingDto(booking);
        }
    }

    // ── Slot ─────────────────────────────────────────────────────────────────

    public static class SlotDto {
        public String  id;           // DB UUID
        public String  consultantId;
        public String  start;
        public String  end;
        public boolean available;

        public SlotDto(SlotRepository.SlotRow row) {
            this.id           = row.id;
            this.consultantId = row.consultantId;
            this.start        = row.start.toString();
            this.end          = row.end.toString();
            this.available    = row.isAvailable;
        }
    }

    // ── Booking ───────────────────────────────────────────────────────────────

    public static class BookingDto {
        public String bookingId;
        public String clientId;
        public String clientName;
        public String serviceId;
        public String serviceTitle;
        public String consultantId;
        public String consultantName;
        public String slotStart;
        public String slotEnd;
        public String status;
        public String createdAt;

        public BookingDto(Booking b) {
            this.bookingId      = b.getBookingId();
            this.clientId       = b.getClient().getId();
            this.clientName     = b.getClient().getName();
            this.serviceId      = b.getService().getServiceId();
            this.serviceTitle   = b.getService().getTitle();
            this.consultantId   = b.getService().getConsultant().getId();
            this.consultantName = b.getService().getConsultant().getName();
            this.slotStart      = b.getSlot().getStart().toString();
            this.slotEnd        = b.getSlot().getEnd().toString();
            this.status         = b.getStateName();
            this.createdAt      = b.getCreatedAt().toString();
        }
    }

    // ── Consultant ────────────────────────────────────────────────────────────

    public static class ConsultantDto {
        public String id;
        public String name;
        public String email;
        public String bio;
        public String registrationStatus;

        public ConsultantDto(Consultant c) {
            this.id                 = c.getId();
            this.name               = c.getName();
            this.email              = c.getEmail();
            this.bio                = c.getBio();
            this.registrationStatus = c.getRegistrationStatus().name();
        }
    }

    // ── Payment method (masked) ───────────────────────────────────────────────

    public static class PaymentMethodDto {
        public String methodId;
        public String methodType;
        public String displayName;

        public PaymentMethodDto(PaymentMethod m) {
            this.methodId    = m.getMethodId();
            this.methodType  = m.getMethodType();
            this.displayName = m.toString();  // already masked (e.g. CreditCard[****3456])
        }
    }

    // ── Transaction ───────────────────────────────────────────────────────────

    public static class TransactionDto {
        public String id;
        public String bookingId;
        public double amount;
        public String status;
        public String timestamp;

        public TransactionDto(PaymentTransactionRepository.TransactionRow row) {
            this.id        = row.id;
            this.bookingId = row.bookingId;
            this.amount    = row.amount;
            this.status    = row.status.name();
            this.timestamp = row.timestamp.toString();
        }

        public TransactionDto(PaymentTransaction tx) {
            this.id        = tx.getTransactionId();
            this.bookingId = tx.getBooking().getBookingId();
            this.amount    = tx.getAmount();
            this.status    = tx.getStatus().name();
            this.timestamp = tx.getTimestamp().toString();
        }
    }
}
