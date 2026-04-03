package com.platform.http.handler;

import com.google.gson.JsonObject;
import com.platform.auth.UserSession;
import com.platform.db.SlotRepository;
import com.platform.domain.Consultant;
import com.platform.domain.Service;
import com.platform.http.AppContext;
import com.platform.http.BaseHandler;
import com.platform.http.dto.Dtos;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * GET  /services              → list all services (public)
 * GET  /services/{id}         → single service (public)
 * GET  /services/{id}/slots   → available slots for this service (public)
 * POST /services              → add service (CONSULTANT only)
 */
public class ServicesHandler extends BaseHandler {

    private static final String CTX = "/services";
    private final AppContext ctx;

    public ServicesHandler(AppContext ctx) {
        super(ctx.sessionStore);
        this.ctx = ctx;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (handleCors(ex)) return;
        String method = ex.getRequestMethod().toUpperCase();
        String[] parts = subParts(ex, CTX);  // segments after /services

        try {
            if (parts.length == 0) {
                // /services
                if (method.equals("GET"))  { listServices(ex); }
                else if (method.equals("POST")) { addService(ex); }
                else send404(ex, "Not found");

            } else if (parts.length == 1) {
                // /services/{id}
                if (method.equals("GET")) getService(ex, parts[0]);
                else send404(ex, "Not found");

            } else if (parts.length == 2 && parts[1].equals("slots")) {
                // /services/{id}/slots
                if (method.equals("GET")) getSlotsForService(ex, parts[0]);
                else send404(ex, "Not found");

            } else {
                send404(ex, "Services endpoint not found.");
            }
        } catch (IllegalArgumentException e) {
            send400(ex, e.getMessage());
        } catch (Exception e) {
            System.err.println("[ServicesHandler] Unexpected error: " + e.getMessage());
            send500(ex, "Unexpected error: " + e.getMessage());
        }
    }

    // ── GET /services ─────────────────────────────────────────────────────────

    private void listServices(HttpExchange ex) throws IOException {
        List<Dtos.ServiceDto> dtos = ctx.catalog.listAllServices().stream()
                .map(Dtos.ServiceDto::new)
                .collect(Collectors.toList());
        sendOk(ex, dtos);
    }

    // ── GET /services/{id} ────────────────────────────────────────────────────

    private void getService(HttpExchange ex, String id) throws IOException {
        Service s = ctx.catalog.findById(id);
        if (s == null) { send404(ex, "Service not found: " + id); return; }
        sendOk(ex, new Dtos.ServiceDto(s));
    }

    // ── GET /services/{id}/slots ──────────────────────────────────────────────

    private void getSlotsForService(HttpExchange ex, String serviceId) throws IOException {
        Service s = ctx.catalog.findById(serviceId);
        if (s == null) { send404(ex, "Service not found: " + serviceId); return; }

        List<Dtos.SlotDto> slots = ctx.slotRepository
                .findByConsultantId(s.getConsultant().getId())
                .stream()
                .filter(row -> row.isAvailable)
                .map(Dtos.SlotDto::new)
                .collect(Collectors.toList());

        sendOk(ex, slots);
    }

    // ── POST /services ────────────────────────────────────────────────────────

    private void addService(HttpExchange ex) throws IOException {
        UserSession session = requireRole(ex, "CONSULTANT");
        if (session == null) return;

        JsonObject body = parseBody(ex);
        String title       = str(body, "title");
        String description = str(body, "description");
        String durStr      = str(body, "durationMin");
        String priceStr    = str(body, "price");

        if (title == null || durStr == null || priceStr == null) {
            send400(ex, "title, durationMin, and price are required.");
            return;
        }

        int    durationMin;
        double price;
        try {
            durationMin = Integer.parseInt(durStr);
            price       = Double.parseDouble(priceStr);
        } catch (NumberFormatException e) {
            send400(ex, "durationMin must be an integer and price must be a number.");
            return;
        }

        Consultant consultant = ctx.userRepository.loadConsultant(session.getUserId());
        if (consultant == null) { send400(ex, "Consultant profile not found."); return; }

        String serviceId = "SVC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Service service  = new Service(serviceId, title,
                description == null ? "" : description,
                durationMin, price, consultant);

        // Persist to DB
        ctx.serviceRepository.save(service);
        // Add to in-memory catalog (so other endpoints see it immediately)
        ctx.catalog.addService(service);
        consultant.addService(service);

        sendCreated(ex, new Dtos.ServiceDto(service));
    }
}
