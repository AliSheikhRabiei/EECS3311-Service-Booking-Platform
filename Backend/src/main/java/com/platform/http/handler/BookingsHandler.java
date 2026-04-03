package com.platform.http.handler;

import com.google.gson.JsonObject;
import com.platform.auth.UserSession;
import com.platform.db.SlotRepository;
import com.platform.domain.*;
import com.platform.http.AppContext;
import com.platform.http.BaseHandler;
import com.platform.http.dto.Dtos;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Full booking lifecycle (UC2-UC4, UC9, UC10)
 *
 *   GET  /bookings              -> client: own bookings; consultant: their bookings; admin: all
 *   POST /bookings              -> client: create booking  { serviceId, slotUuid }
 *   POST /bookings/{id}/confirm -> consultant: confirm
 *   POST /bookings/{id}/reject  -> consultant: reject      { reason }
 *   POST /bookings/{id}/complete-> consultant: complete
 *   POST /bookings/{id}/cancel  -> client/admin: cancel    { reason }
 *   GET  /bookings/{id}         -> get single booking
 */
public class BookingsHandler extends BaseHandler {

    private static final String CTX = "/bookings";
    private final AppContext ctx;

    public BookingsHandler(AppContext ctx) {
        super(ctx.sessionStore);
        this.ctx = ctx;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (handleCors(ex)) return;
        String method = ex.getRequestMethod().toUpperCase();
        String[] parts = subParts(ex, CTX);

        try {
            if (parts.length == 0) {
                if (method.equals("GET"))        listBookings(ex);
                else if (method.equals("POST"))  createBooking(ex);
                else send404(ex, "Not found");

            } else if (parts.length == 1) {
                if (method.equals("GET")) getBooking(ex, parts[0]);
                else send404(ex, "Not found");

            } else if (parts.length == 2) {
                String bookingId = parts[0];
                switch (parts[1]) {
                    case "confirm"  -> confirmBooking(ex, bookingId);
                    case "reject"   -> rejectBooking(ex, bookingId);
                    case "complete" -> completeBooking(ex, bookingId);
                    case "cancel"   -> cancelBooking(ex, bookingId);
                    default -> send404(ex, "Unknown booking action: " + parts[1]);
                }
            } else {
                send404(ex, "Bookings endpoint not found.");
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            send400(ex, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            send500(ex, "Unexpected error: " + e.getMessage());
        }
    }

    // -- GET /bookings --------------------------------------------------------

    private void listBookings(HttpExchange ex) throws IOException {
        UserSession session = requireAuth(ex);
        if (session == null) return;

        List<Dtos.BookingDto> dtos;

        switch (session.getRole()) {
            case "CLIENT" -> {
                dtos = ctx.bookingService.getBookingsForClient(session.getUserId())
                        .stream().map(Dtos.BookingDto::new).collect(Collectors.toList());
            }
            case "CONSULTANT" -> {
                Consultant consultant = ctx.userRepository.loadConsultant(session.getUserId());
                dtos = ctx.bookingService.getBookingsForConsultant(consultant)
                        .stream().map(Dtos.BookingDto::new).collect(Collectors.toList());
            }
            case "ADMIN" -> {
                dtos = ctx.bookingRepository.findAll()
                        .stream().map(Dtos.BookingDto::new).collect(Collectors.toList());
            }
            default -> { send403(ex); return; }
        }

        sendOk(ex, dtos);
    }

    // -- GET /bookings/{id} ---------------------------------------------------

    private void getBooking(HttpExchange ex, String bookingId) throws IOException {
        UserSession session = requireAuth(ex);
        if (session == null) return;

        Booking booking = ctx.bookingService.getBooking(bookingId);
        if (booking == null) { send404(ex, "Booking not found: " + bookingId); return; }

        if ("CLIENT".equals(session.getRole())
                && !booking.getClient().getId().equals(session.getUserId())) {
            send403(ex); return;
        }

        sendOk(ex, new Dtos.BookingDto(booking));
    }

    // -- POST /bookings -------------------------------------------------------

    private void createBooking(HttpExchange ex) throws IOException {
        UserSession session = requireRole(ex, "CLIENT");
        if (session == null) return;

        JsonObject body   = parseBody(ex);
        String serviceId  = str(body, "serviceId");
        String slotUuid   = str(body, "slotUuid");

        if (serviceId == null || slotUuid == null) {
            send400(ex, "serviceId and slotUuid are required.");
            return;
        }

        Service service = ctx.catalog.findById(serviceId);
        if (service == null) { send404(ex, "Service not found: " + serviceId); return; }

        SlotRepository.SlotRow slotRow = ctx.slotRepository.findByUuid(slotUuid);
        if (slotRow == null) { send404(ex, "Slot not found: " + slotUuid); return; }
        if (!slotRow.isAvailable) { send400(ex, "This slot is no longer available."); return; }

        Client client = ctx.userRepository.loadClient(session.getUserId());
        if (client == null) { send400(ex, "Client profile not found."); return; }

        Booking booking = ctx.bookingService.createBooking(client, service, slotRow.slot);
        ctx.bookingRepository.save(booking);
        ctx.slotRepository.updateAvailability(slotUuid, false);

        sendCreated(ex, new Dtos.BookingDto(booking));
    }

    // -- POST /bookings/{id}/confirm ------------------------------------------
    // FIX: BookingService.confirmBooking() already calls bookingRepository.save()
    // with the correct new state. Re-fetch after the call to get the updated
    // booking instead of using the stale pre-call reference.

    private void confirmBooking(HttpExchange ex, String bookingId) throws IOException {
        UserSession session = requireRole(ex, "CONSULTANT");
        if (session == null) return;

        // Validate ownership BEFORE the state change
        Booking preCheck = requireBookingForConsultant(ex, bookingId, session.getUserId());
        if (preCheck == null) return;

        // Service call: mutates its own copy and persists to DB
        ctx.bookingService.confirmBooking(bookingId);

        // Re-fetch from DB to get the updated state for the response
        Booking updated = ctx.bookingService.getBooking(bookingId);
        sendOk(ex, new Dtos.BookingDto(updated));
    }

    // -- POST /bookings/{id}/reject -------------------------------------------

    private void rejectBooking(HttpExchange ex, String bookingId) throws IOException {
        UserSession session = requireRole(ex, "CONSULTANT");
        if (session == null) return;

        JsonObject body = parseBody(ex);
        String reason = str(body, "reason");
        if (reason == null) reason = "No reason provided.";

        Booking preCheck = requireBookingForConsultant(ex, bookingId, session.getUserId());
        if (preCheck == null) return;

        ctx.bookingService.rejectBooking(bookingId, reason);

        // Release the slot back to available
        releaseSlotByBookingId(bookingId);

        Booking updated = ctx.bookingService.getBooking(bookingId);
        sendOk(ex, new Dtos.BookingDto(updated));
    }

    // -- POST /bookings/{id}/complete -----------------------------------------

    private void completeBooking(HttpExchange ex, String bookingId) throws IOException {
        UserSession session = requireRole(ex, "CONSULTANT");
        if (session == null) return;

        Booking preCheck = requireBookingForConsultant(ex, bookingId, session.getUserId());
        if (preCheck == null) return;

        ctx.bookingService.completeBooking(bookingId);

        Booking updated = ctx.bookingService.getBooking(bookingId);
        sendOk(ex, new Dtos.BookingDto(updated));
    }

    // -- POST /bookings/{id}/cancel -------------------------------------------

    private void cancelBooking(HttpExchange ex, String bookingId) throws IOException {
        UserSession session = requireAuth(ex);
        if (session == null) return;

        Booking preCheck = ctx.bookingService.getBooking(bookingId);
        if (preCheck == null) { send404(ex, "Booking not found: " + bookingId); return; }

        if ("CLIENT".equals(session.getRole())
                && !preCheck.getClient().getId().equals(session.getUserId())) {
            send403(ex); return;
        }

        JsonObject body = parseBody(ex);
        String reason = str(body, "reason");
        if (reason == null) reason = "Cancelled by user.";

        boolean cancelled = ctx.bookingService.cancelBooking(bookingId, reason);
        if (!cancelled) {
            send400(ex, "Cancellation blocked by current cancellation policy.");
            return;
        }

        // Release slot if it was reserved
        releaseSlotByBookingId(bookingId);

        Booking updated = ctx.bookingService.getBooking(bookingId);
        sendOk(ex, new Dtos.BookingDto(updated));
    }

    // -- Helpers --------------------------------------------------------------

    private Booking requireBookingForConsultant(HttpExchange ex, String bookingId, String consultantId)
            throws IOException {
        Booking booking = ctx.bookingService.getBooking(bookingId);
        if (booking == null) { send404(ex, "Booking not found: " + bookingId); return null; }
        if (!booking.getService().getConsultant().getId().equals(consultantId)) {
            send403(ex); return null;
        }
        return booking;
    }

    /**
     * Releases the slot associated with a booking back to available.
     * Re-fetches the booking from DB so slot start time is always current.
     */
    private void releaseSlotByBookingId(String bookingId) {
        Booking booking = ctx.bookingService.getBooking(bookingId);
        if (booking == null) return;
        SlotRepository.SlotRow row = ctx.slotRepository
                .findByConsultantAndStart(booking.getService().getConsultant().getId(),
                        booking.getSlot().getStart());
        if (row != null) {
            ctx.slotRepository.updateAvailability(row.id, true);
            booking.getSlot().release();
        }
    }
}
