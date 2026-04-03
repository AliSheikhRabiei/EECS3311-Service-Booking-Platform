package com.platform.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.platform.auth.SessionStore;
import com.platform.auth.UserSession;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Shared base for all HTTP handlers.
 * Provides: CORS headers, JSON send helpers, request body reading, auth token extraction.
 */
public abstract class BaseHandler implements HttpHandler {

    protected static final Gson GSON = new GsonBuilder()
            .serializeNulls()
            .create();

    protected final SessionStore sessionStore;

    protected BaseHandler(SessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    // ── CORS preflight ───────────────────────────────────────────────────────

    /**
     * Adds CORS headers and handles OPTIONS preflight.
     * Returns true if the exchange was a preflight (caller should return immediately).
     */
    protected boolean handleCors(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin",  "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return true;
        }
        return false;
    }

    // ── Response helpers ─────────────────────────────────────────────────────

    protected void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    protected void sendOk(HttpExchange ex, Object body) throws IOException {
        sendJson(ex, 200, body);
    }

    protected void sendCreated(HttpExchange ex, Object body) throws IOException {
        sendJson(ex, 201, body);
    }

    protected void sendError(HttpExchange ex, int status, String message) throws IOException {
        JsonObject err = new JsonObject();
        err.addProperty("error", message);
        sendJson(ex, status, err);
    }

    protected void send400(HttpExchange ex, String msg) throws IOException { sendError(ex, 400, msg); }
    protected void send401(HttpExchange ex)              throws IOException { sendError(ex, 401, "Unauthorised. Please log in."); }
    protected void send403(HttpExchange ex)              throws IOException { sendError(ex, 403, "Forbidden. You do not have permission."); }
    protected void send404(HttpExchange ex, String msg)  throws IOException { sendError(ex, 404, msg); }
    protected void send500(HttpExchange ex, String msg)  throws IOException { sendError(ex, 500, msg); }

    // ── Request helpers ──────────────────────────────────────────────────────

    /** Reads the entire request body as a UTF-8 string. */
    protected String readBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Parses the request body as JSON into a JsonObject. Returns null if empty or invalid. */
    protected JsonObject parseBody(HttpExchange ex) throws IOException {
        String body = readBody(ex).trim();
        if (body.isEmpty()) return new JsonObject();
        try {
            return GSON.fromJson(body, JsonObject.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** Extracts a string from a JsonObject field, or null if missing. */
    protected String str(JsonObject obj, String field) {
        if (obj == null || !obj.has(field) || obj.get(field).isJsonNull()) return null;
        return obj.get(field).getAsString().trim();
    }

    // ── URL helpers ──────────────────────────────────────────────────────────

    /**
     * Returns path segments after the context root.
     * e.g. context=/bookings, path=/bookings/BK-1/confirm → ["BK-1", "confirm"]
     */
    protected String[] subParts(HttpExchange ex, String contextPath) {
        String full = ex.getRequestURI().getPath();
        String after = full.substring(contextPath.length());
        if (after.startsWith("/")) after = after.substring(1);
        if (after.isEmpty()) return new String[0];
        return after.split("/");
    }

    // ── Auth helpers ─────────────────────────────────────────────────────────

    /** Extracts the Bearer token from the Authorization header, or null. */
    protected String extractToken(HttpExchange ex) {
        String header = ex.getRequestHeaders().getFirst("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }

    /** Returns the session for the request, or null if not authenticated. */
    protected UserSession getSession(HttpExchange ex) {
        return sessionStore.getSession(extractToken(ex));
    }

    /** Returns session or sends 401 and returns null. */
    protected UserSession requireAuth(HttpExchange ex) throws IOException {
        UserSession s = getSession(ex);
        if (s == null) { send401(ex); return null; }
        return s;
    }

    /** Returns session only if the role matches; otherwise sends 401/403 and returns null. */
    protected UserSession requireRole(HttpExchange ex, String role) throws IOException {
        UserSession s = requireAuth(ex);
        if (s == null) return null;
        if (!role.equalsIgnoreCase(s.getRole())) { send403(ex); return null; }
        return s;
    }
}
