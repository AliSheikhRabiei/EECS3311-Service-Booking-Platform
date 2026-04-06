package com.platform.http.handler;

import com.google.gson.JsonObject;
import com.platform.auth.UserSession;
import com.platform.domain.RegistrationStatus;
import com.platform.db.SlotRepository;
import com.platform.domain.Consultant;
import com.platform.domain.TimeSlot;
import com.platform.http.AppContext;
import com.platform.http.BaseHandler;
import com.platform.http.dto.Dtos;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * UC8 - Manage Availability (CONSULTANT only)
 *
 *   GET    /slots              - list all slots for the logged-in consultant
 *   POST   /slots              - add slot(s) { start, end }
 *                               If the range is exactly 1 hour → creates 1 slot.
 *                               If the range is > 1 hour → auto-splits into 1-hour slots.
 *                               This matches the original Phase 1 behaviour.
 *   DELETE /slots/{slotUuid}  - remove a slot
 */
public class SlotsHandler extends BaseHandler {

    private static final String CTX = "/slots";
    private final AppContext ctx;

    public SlotsHandler(AppContext ctx) {
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
                if (method.equals("GET"))        listSlots(ex);
                else if (method.equals("POST"))  addSlots(ex);
                else send404(ex, "Not found");

            } else if (parts.length == 1 && method.equals("DELETE")) {
                deleteSlot(ex, parts[0]);

            } else {
                send404(ex, "Slots endpoint not found.");
            }
        } catch (IllegalArgumentException e) {
            send400(ex, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            send500(ex, "Unexpected error: " + e.getMessage());
        }
    }

    // -- GET /slots -----------------------------------------------------------

    private void listSlots(HttpExchange ex) throws IOException {
        Consultant consultant = requireApprovedConsultant(ex);
        if (consultant == null) return;

        List<Dtos.SlotDto> dtos = ctx.slotRepository
                .findByConsultantId(consultant.getId())
                .stream()
                .map(Dtos.SlotDto::new)
                .collect(Collectors.toList());

        sendOk(ex, dtos);
    }

    // -- POST /slots ----------------------------------------------------------
    // Always auto-splits into 1-hour slots (same as Phase 1 addTimeSlotBlock).
    // A 1-hour range creates exactly 1 slot; a 6-hour range creates 6 slots.

    private void addSlots(HttpExchange ex) throws IOException {
        Consultant consultant = requireApprovedConsultant(ex);
        if (consultant == null) return;

        JsonObject body = parseBody(ex);
        String startStr = str(body, "start");
        String endStr   = str(body, "end");

        if (startStr == null || endStr == null) {
            send400(ex, "start and end are required (ISO format: 2026-06-01T10:00:00).");
            return;
        }

        LocalDateTime start, end;
        try {
            start = LocalDateTime.parse(startStr);
            end   = LocalDateTime.parse(endStr);
        } catch (DateTimeParseException e) {
            send400(ex, "Invalid date format. Use ISO format: 2026-06-01T10:00:00");
            return;
        }

        if (!end.isAfter(start)) {
            send400(ex, "end must be after start.");
            return;
        }

        long totalHours = ChronoUnit.HOURS.between(start, end);
        if (totalHours < 1) {
            send400(ex, "Slots must be at least 1 hour long.");
            return;
        }

        // Phase 1 logic: splits any range into 1-hour slots automatically
        int added = ctx.availabilityService.addTimeSlotBlock(consultant, start, end);

        // Persist each new slot to DB.
        // Fix: load all existing start times into a Set ONCE before the loop
        // instead of re-querying the DB on every iteration (N+1 query problem).
        Set<LocalDateTime> existingStartTimes = ctx.slotRepository
                .findByConsultantId(consultant.getId())
                .stream()
                .map(r -> r.start)
                .collect(Collectors.toCollection(HashSet::new));

        for (TimeSlot ts : ctx.availabilityService.listAllSlots(consultant)) {
            if (!existingStartTimes.contains(ts.getStart())) {
                ctx.slotRepository.save(consultant.getId(), ts);
                existingStartTimes.add(ts.getStart()); // keep Set current without re-querying DB
            }
        }

        JsonObject resp = new JsonObject();
        resp.addProperty("slotsAdded", added);
        resp.addProperty("message", added + " one-hour slot(s) added successfully.");
        sendCreated(ex, resp);
    }

    // -- DELETE /slots/{uuid} -------------------------------------------------

    private void deleteSlot(HttpExchange ex, String slotUuid) throws IOException {
        Consultant consultant = requireApprovedConsultant(ex);
        if (consultant == null) return;

        SlotRepository.SlotRow row = ctx.slotRepository.findByUuid(slotUuid);
        if (row == null) { send404(ex, "Slot not found: " + slotUuid); return; }
        if (!row.consultantId.equals(consultant.getId())) { send403(ex); return; }
        if (!row.isAvailable) {
            send400(ex, "Cannot remove a reserved slot (a booking exists for it).");
            return;
        }

        ctx.slotRepository.delete(slotUuid);
        ctx.availabilityService.removeTimeSlot(consultant, row.slot.getSlotId());

        JsonObject resp = new JsonObject();
        resp.addProperty("message", "Slot deleted.");
        sendOk(ex, resp);
    }

    private Consultant requireApprovedConsultant(HttpExchange ex) throws IOException {
        UserSession session = requireRole(ex, "CONSULTANT");
        if (session == null) return null;

        Consultant consultant = ctx.userRepository.loadConsultant(session.getUserId());
        if (consultant == null) {
            send400(ex, "Consultant profile not found.");
            return null;
        }
        if (consultant.getRegistrationStatus() != RegistrationStatus.APPROVED) {
            sendError(ex, 403, consultantApprovalMessage(consultant));
            return null;
        }
        return consultant;
    }

    private String consultantApprovalMessage(Consultant consultant) {
        return consultant.getRegistrationStatus() == RegistrationStatus.REJECTED
                ? "Consultant registration was rejected by an admin."
                : "Consultant account is awaiting admin approval.";
    }
}
