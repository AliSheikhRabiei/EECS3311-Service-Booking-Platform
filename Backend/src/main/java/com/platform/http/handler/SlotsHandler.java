package com.platform.http.handler;

import com.google.gson.JsonObject;
import com.platform.auth.UserSession;
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
import java.util.List;
import java.util.stream.Collectors;

/**
 * UC8 – Manage Availability (CONSULTANT only)
 *
 *   GET    /slots                → list all slots for the logged-in consultant
 *   POST   /slots                → add a single slot  { start, end }
 *   POST   /slots/block          → add a time block   { start, end }
 *   DELETE /slots/{slotUuid}     → remove a slot
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
                if (method.equals("GET"))  listSlots(ex);
                else if (method.equals("POST")) addSlot(ex);
                else send404(ex, "Not found");

            } else if (parts.length == 1 && parts[0].equals("block") && method.equals("POST")) {
                addBlock(ex);

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

    // ── GET /slots ────────────────────────────────────────────────────────────

    private void listSlots(HttpExchange ex) throws IOException {
        UserSession session = requireRole(ex, "CONSULTANT");
        if (session == null) return;

        List<Dtos.SlotDto> dtos = ctx.slotRepository
                .findByConsultantId(session.getUserId())
                .stream()
                .map(Dtos.SlotDto::new)
                .collect(Collectors.toList());

        sendOk(ex, dtos);
    }

    // ── POST /slots ───────────────────────────────────────────────────────────

    private void addSlot(HttpExchange ex) throws IOException {
        UserSession session = requireRole(ex, "CONSULTANT");
        if (session == null) return;

        JsonObject body = parseBody(ex);
        String startStr = str(body, "start");
        String endStr   = str(body, "end");

        if (startStr == null || endStr == null) {
            send400(ex, "start and end are required (ISO format: 2026-05-01T10:00:00).");
            return;
        }

        LocalDateTime start, end;
        try {
            start = LocalDateTime.parse(startStr);
            end   = LocalDateTime.parse(endStr);
        } catch (DateTimeParseException e) {
            send400(ex, "Invalid date format. Use ISO format: 2026-05-01T10:00:00");
            return;
        }

        Consultant consultant = ctx.userRepository.loadConsultant(session.getUserId());
        TimeSlot slot = new TimeSlot(start, end);

        // Persist to DB
        String slotUuid = ctx.slotRepository.save(session.getUserId(), slot);
        // Add to in-memory AvailabilityService
        ctx.availabilityService.addTimeSlot(consultant, slot);

        SlotRepository.SlotRow row = ctx.slotRepository.findByUuid(slotUuid);
        sendCreated(ex, new Dtos.SlotDto(row));
    }

    // ── POST /slots/block ─────────────────────────────────────────────────────

    private void addBlock(HttpExchange ex) throws IOException {
        UserSession session = requireRole(ex, "CONSULTANT");
        if (session == null) return;

        JsonObject body = parseBody(ex);
        String startStr = str(body, "start");
        String endStr   = str(body, "end");

        if (startStr == null || endStr == null) {
            send400(ex, "start and end are required.");
            return;
        }

        LocalDateTime start, end;
        try {
            start = LocalDateTime.parse(startStr);
            end   = LocalDateTime.parse(endStr);
        } catch (DateTimeParseException e) {
            send400(ex, "Invalid date format. Use ISO: 2026-05-01T10:00:00");
            return;
        }

        Consultant consultant = ctx.userRepository.loadConsultant(session.getUserId());
        int added = ctx.availabilityService.addTimeSlotBlock(consultant, start, end);

        // Persist each newly added slot to DB
        List<SlotRepository.SlotRow> allSlots = ctx.slotRepository
                .findByConsultantId(session.getUserId());
        // Save slots that don't yet have a DB UUID
        for (TimeSlot ts : ctx.availabilityService.listAllSlots(consultant)) {
            boolean alreadyInDb = allSlots.stream()
                    .anyMatch(r -> r.start.equals(ts.getStart()));
            if (!alreadyInDb) {
                ctx.slotRepository.save(session.getUserId(), ts);
            }
        }

        JsonObject resp = new JsonObject();
        resp.addProperty("slotsAdded", added);
        resp.addProperty("message", added + " one-hour slot(s) added.");
        sendCreated(ex, resp);
    }

    // ── DELETE /slots/{uuid} ──────────────────────────────────────────────────

    private void deleteSlot(HttpExchange ex, String slotUuid) throws IOException {
        UserSession session = requireRole(ex, "CONSULTANT");
        if (session == null) return;

        SlotRepository.SlotRow row = ctx.slotRepository.findByUuid(slotUuid);
        if (row == null) { send404(ex, "Slot not found: " + slotUuid); return; }
        if (!row.consultantId.equals(session.getUserId())) { send403(ex); return; }
        if (!row.isAvailable) {
            send400(ex, "Cannot remove a reserved slot (a booking exists for it).");
            return;
        }

        // Remove from DB
        ctx.slotRepository.delete(slotUuid);
        // Remove from in-memory AvailabilityService
        Consultant consultant = ctx.userRepository.loadConsultant(session.getUserId());
        ctx.availabilityService.removeTimeSlot(consultant, row.slot.getSlotId());

        JsonObject resp = new JsonObject();
        resp.addProperty("message", "Slot deleted.");
        sendOk(ex, resp);
    }
}
