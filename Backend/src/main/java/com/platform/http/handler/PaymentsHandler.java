package com.platform.http.handler;

import com.google.gson.JsonObject;
import com.platform.auth.UserSession;
import com.platform.db.BookingStateFactory;
import com.platform.domain.Booking;
import com.platform.domain.Client;
import com.platform.http.AppContext;
import com.platform.http.BaseHandler;
import com.platform.http.dto.Dtos;
import com.platform.payment.*;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Payment endpoints (all CLIENT only unless noted):
 *
 *   GET  /payments/methods          → list saved payment methods (CLIENT)
 *   POST /payments/methods          → add a payment method      (CLIENT)
 *   DELETE /payments/methods/{id}   → remove payment method     (CLIENT)
 *   POST /payments/pay              → process payment { bookingId, methodId } (CLIENT)
 *   GET  /payments/history          → payment history (CLIENT)
 */
public class PaymentsHandler extends BaseHandler {

    private static final String CTX = "/payments";
    private final AppContext ctx;

    public PaymentsHandler(AppContext ctx) {
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
                send404(ex, "Use /payments/methods, /payments/pay, or /payments/history");

            } else if (parts[0].equals("methods")) {
                if (parts.length == 1) {
                    if (method.equals("GET"))        listMethods(ex);
                    else if (method.equals("POST"))  addMethod(ex);
                    else send404(ex, "Not found");
                } else if (parts.length == 2 && method.equals("DELETE")) {
                    removeMethod(ex, parts[1]);
                } else {
                    send404(ex, "Not found");
                }

            } else if (parts[0].equals("pay") && method.equals("POST")) {
                processPayment(ex);

            } else if (parts[0].equals("history") && method.equals("GET")) {
                paymentHistory(ex);

            } else {
                send404(ex, "Payment endpoint not found.");
            }
        } catch (IllegalArgumentException e) {
            send400(ex, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            send500(ex, "Unexpected error: " + e.getMessage());
        }
    }

    // ── GET /payments/methods ─────────────────────────────────────────────────

    private void listMethods(HttpExchange ex) throws IOException {
        UserSession session = requireRole(ex, "CLIENT");
        if (session == null) return;

        Client client = ctx.userRepository.loadClient(session.getUserId());
        // Load from DB (source of truth), sync to in-memory service
        List<PaymentMethod> methods = ctx.paymentMethodRepository
                .findByClientId(session.getUserId(), client);
        // Keep in-memory service in sync (idempotent)
        for (PaymentMethod m : methods) {
            if (ctx.paymentMethodService.findMethod(client, m.getMethodId()) == null) {
                ctx.paymentMethodService.addMethod(client, m);
            }
        }

        List<Dtos.PaymentMethodDto> dtos = methods.stream()
                .map(Dtos.PaymentMethodDto::new)
                .collect(Collectors.toList());
        sendOk(ex, dtos);
    }

    // ── POST /payments/methods ─────────────────────────────────────────────────

    private void addMethod(HttpExchange ex) throws IOException {
        UserSession session = requireRole(ex, "CLIENT");
        if (session == null) return;

        JsonObject body = parseBody(ex);
        String type = str(body, "type");
        if (type == null) { send400(ex, "type is required (CREDIT_CARD, DEBIT_CARD, PAYPAL, BANK_TRANSFER)."); return; }

        Client client = ctx.userRepository.loadClient(session.getUserId());
        String methodId = "PM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        PaymentMethod method = buildMethod(body, type, client, methodId);
        if (method == null) {
            send400(ex, "Unknown payment type: " + type);
            return;
        }

        if (!method.validate()) {
            send400(ex, "Invalid payment details for type: " + type
                    + ". Check card number (16 digits), expiry (MM/yy), CVV (3-4 digits), etc.");
            return;
        }

        // Persist to DB then add to in-memory service
        ctx.paymentMethodRepository.save(session.getUserId(), method);
        ctx.paymentMethodService.addMethod(client, method);

        sendCreated(ex, new Dtos.PaymentMethodDto(method));
    }

    // ── DELETE /payments/methods/{id} ─────────────────────────────────────────

    private void removeMethod(HttpExchange ex, String methodId) throws IOException {
        UserSession session = requireRole(ex, "CLIENT");
        if (session == null) return;

        Client client = ctx.userRepository.loadClient(session.getUserId());
        ctx.paymentMethodRepository.delete(session.getUserId(), methodId);
        ctx.paymentMethodService.removeMethod(client, methodId);

        JsonObject resp = new JsonObject();
        resp.addProperty("message", "Payment method removed.");
        sendOk(ex, resp);
    }

    // ── POST /payments/pay ─────────────────────────────────────────────────────

    private void processPayment(HttpExchange ex) throws IOException {
        UserSession session = requireRole(ex, "CLIENT");
        if (session == null) return;

        JsonObject body   = parseBody(ex);
        String bookingId  = str(body, "bookingId");
        String methodId   = str(body, "methodId");

        if (bookingId == null || methodId == null) {
            send400(ex, "bookingId and methodId are required.");
            return;
        }

        Booking booking = ctx.bookingService.getBooking(bookingId);
        if (booking == null) { send404(ex, "Booking not found: " + bookingId); return; }
        if (!booking.getClient().getId().equals(session.getUserId())) { send403(ex); return; }

        Client client = ctx.userRepository.loadClient(session.getUserId());

        // Load from DB if not in memory
        PaymentMethod method = ctx.paymentMethodService.findMethod(client, methodId);
        if (method == null) {
            List<PaymentMethod> methods = ctx.paymentMethodRepository
                    .findByClientId(session.getUserId(), client);
            method = methods.stream()
                    .filter(m -> m.getMethodId().equals(methodId))
                    .findFirst().orElse(null);
        }
        if (method == null) { send404(ex, "Payment method not found: " + methodId); return; }

        // Run Phase 1 payment chain (validate → process → markPaid)
        PaymentTransaction tx = ctx.paymentService.processPayment(booking, method);

        // Persist state + transaction
        ctx.bookingRepository.updateStatus(bookingId,
                BookingStateFactory.toDbString(booking.getState()));
        ctx.transactionRepository.save(tx);

        sendOk(ex, new Dtos.TransactionDto(tx));
    }

    // ── GET /payments/history ──────────────────────────────────────────────────

    private void paymentHistory(HttpExchange ex) throws IOException {
        UserSession session = requireRole(ex, "CLIENT");
        if (session == null) return;

        List<Dtos.TransactionDto> dtos = ctx.transactionRepository
                .findByClientId(session.getUserId())
                .stream()
                .map(Dtos.TransactionDto::new)
                .collect(Collectors.toList());

        sendOk(ex, dtos);
    }

    // ── Payment method builder ─────────────────────────────────────────────────

    private PaymentMethod buildMethod(JsonObject b, String type, Client client, String id) {
        return switch (type.toUpperCase()) {
            case "CREDIT_CARD" -> new CreditCardMethod(client, id,
                    str(b, "cardNumber"), str(b, "expiry"), str(b, "cvv"));
            case "DEBIT_CARD" -> new DebitCardMethod(client, id,
                    str(b, "cardNumber"), str(b, "expiry"));
            case "PAYPAL" -> new PayPalMethod(client, id, str(b, "email"));
            case "BANK_TRANSFER" -> new BankTransferMethod(client, id,
                    str(b, "accountNumber"), str(b, "routingNumber"));
            default -> null;
        };
    }
}
