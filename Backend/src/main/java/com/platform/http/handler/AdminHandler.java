package com.platform.http.handler;

import com.google.gson.JsonObject;
import com.platform.auth.UserSession;
import com.platform.domain.Consultant;
import com.platform.domain.RegistrationStatus;
import com.platform.http.AppContext;
import com.platform.http.BaseHandler;
import com.platform.http.dto.Dtos;
import com.platform.policy.*;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Admin-only endpoints (UC11, UC12):
 *
 *   GET  /admin/consultants                       → list all consultants
 *   POST /admin/consultants/{id}/approve          → approve a consultant
 *   POST /admin/consultants/{id}/reject           → reject a consultant
 *   GET  /admin/policies                          → view current policy names
 *   POST /admin/policies/cancellation             → set cancellation policy { type: DEFAULT|STRICT }
 *   POST /admin/policies/refund                   → set refund policy       { type: DEFAULT|FULL|NONE }
 */
public class AdminHandler extends BaseHandler {

    private static final String CTX = "/admin";
    private final AppContext ctx;

    public AdminHandler(AppContext ctx) {
        super(ctx.sessionStore);
        this.ctx = ctx;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (handleCors(ex)) return;
        String method = ex.getRequestMethod().toUpperCase();
        String[] parts = subParts(ex, CTX);

        try {
            UserSession session = requireRole(ex, "ADMIN");
            if (session == null) return;

            if (parts.length >= 1 && parts[0].equals("consultants")) {
                handleConsultants(ex, method, parts);

            } else if (parts.length >= 1 && parts[0].equals("policies")) {
                handlePolicies(ex, method, parts);

            } else {
                send404(ex, "Admin endpoint not found.");
            }
        } catch (IllegalArgumentException e) {
            send400(ex, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            send500(ex, "Unexpected error: " + e.getMessage());
        }
    }

    // ── /admin/consultants ────────────────────────────────────────────────────

    private void handleConsultants(HttpExchange ex, String method, String[] parts) throws IOException {

        if (parts.length == 1 && method.equals("GET")) {
            // List all consultants
            List<Dtos.ConsultantDto> dtos = ctx.userRepository.findAllConsultants()
                    .stream().map(Dtos.ConsultantDto::new).collect(Collectors.toList());
            sendOk(ex, dtos);

        } else if (parts.length == 3) {
            String consultantId = parts[1];
            String action       = parts[2];
            Consultant consultant = ctx.userRepository.loadConsultant(consultantId);
            if (consultant == null) { send404(ex, "Consultant not found: " + consultantId); return; }

            switch (action) {
                case "approve" -> {
                    consultant.setRegistrationStatus(RegistrationStatus.APPROVED);
                    ctx.userRepository.updateConsultantStatus(consultantId, "APPROVED");
                    JsonObject resp = new JsonObject();
                    resp.addProperty("message", "Consultant approved.");
                    sendOk(ex, resp);
                }
                case "reject" -> {
                    consultant.setRegistrationStatus(RegistrationStatus.REJECTED);
                    ctx.userRepository.updateConsultantStatus(consultantId, "REJECTED");
                    JsonObject resp = new JsonObject();
                    resp.addProperty("message", "Consultant rejected.");
                    sendOk(ex, resp);
                }
                default -> send404(ex, "Unknown action: " + action);
            }
        } else {
            send404(ex, "Admin consultants endpoint not found.");
        }
    }

    // ── /admin/policies ───────────────────────────────────────────────────────

    private void handlePolicies(HttpExchange ex, String method, String[] parts) throws IOException {

        if (parts.length == 1 && method.equals("GET")) {
            // View current policy names
            JsonObject resp = new JsonObject();
            resp.addProperty("cancellationPolicy",
                    ctx.policyManager.getCancellationPolicy().getClass().getSimpleName());
            resp.addProperty("refundPolicy",
                    ctx.policyManager.getRefundPolicy().getClass().getSimpleName());
            resp.addProperty("notificationPolicy",
                    ctx.policyManager.getNotificationPolicy().getClass().getSimpleName());
            resp.addProperty("pricingStrategy",
                    ctx.policyManager.getPricingStrategy().getClass().getSimpleName());
            sendOk(ex, resp);

        } else if (parts.length == 2 && parts[1].equals("cancellation") && method.equals("POST")) {
            JsonObject body = parseBody(ex);
            String type = str(body, "type");
            if (type == null) { send400(ex, "type is required: DEFAULT or STRICT"); return; }

            switch (type.toUpperCase()) {
                case "DEFAULT" -> {
                    ctx.policyManager.setCancellationPolicy(new DefaultCancellationPolicy());
                    sendOk(ex, jsonMsg("Cancellation policy set to DEFAULT."));
                }
                case "STRICT" -> {
                    ctx.policyManager.setCancellationPolicy(new CancellationPolicy() {
                        @Override public boolean canCancel(com.platform.domain.Booking b, java.time.LocalDateTime now) { return false; }
                        @Override public double cancellationFee(com.platform.domain.Booking b, java.time.LocalDateTime now) { return b.getService().getPrice(); }
                    });
                    sendOk(ex, jsonMsg("Cancellation policy set to STRICT (no cancellations)."));
                }
                default -> send400(ex, "Unknown cancellation type: " + type + ". Use DEFAULT or STRICT.");
            }

        } else if (parts.length == 2 && parts[1].equals("refund") && method.equals("POST")) {
            JsonObject body = parseBody(ex);
            String type = str(body, "type");
            if (type == null) { send400(ex, "type is required: DEFAULT, FULL, or NONE"); return; }

            switch (type.toUpperCase()) {
                case "DEFAULT" -> {
                    ctx.policyManager.setRefundPolicy(new DefaultRefundPolicy());
                    sendOk(ex, jsonMsg("Refund policy set to DEFAULT (80%)."));
                }
                case "FULL" -> {
                    ctx.policyManager.setRefundPolicy(new FullRefundPolicy());
                    sendOk(ex, jsonMsg("Refund policy set to FULL (100%)."));
                }
                case "NONE" -> {
                    ctx.policyManager.setRefundPolicy((tx, now) -> 0.0);
                    sendOk(ex, jsonMsg("Refund policy set to NONE (0%)."));
                }
                default -> send400(ex, "Unknown refund type: " + type + ". Use DEFAULT, FULL, or NONE.");
            }
        } else {
            send404(ex, "Admin policies endpoint not found.");
        }
    }

    private JsonObject jsonMsg(String msg) {
        JsonObject obj = new JsonObject();
        obj.addProperty("message", msg);
        return obj;
    }
}
