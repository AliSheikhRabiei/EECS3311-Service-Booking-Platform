package com.platform.http.handler;

import com.google.gson.JsonObject;
import com.platform.auth.SessionStore;
import com.platform.auth.UserSession;
import com.platform.http.AppContext;
import com.platform.http.BaseHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;

/**
 * Handles all authentication endpoints under /auth:
 *
 *   POST /auth/register/client      { name, email, password }
 *   POST /auth/register/consultant  { name, email, password, bio }
 *   POST /auth/login                { email, password }
 *   POST /auth/logout               (requires Authorization: Bearer <token>)
 *   GET  /auth/me                   (requires Authorization: Bearer <token>)
 */
public class AuthHandler extends BaseHandler {

    private final AppContext ctx;

    public AuthHandler(AppContext ctx) {
        super(ctx.sessionStore);
        this.ctx = ctx;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (handleCors(ex)) return;

        String path   = ex.getRequestURI().getPath();  // e.g. /auth/login
        String method = ex.getRequestMethod().toUpperCase();

        try {
            if (path.equals("/auth/login") && method.equals("POST")) {
                handleLogin(ex);
            } else if (path.equals("/auth/logout") && method.equals("POST")) {
                handleLogout(ex);
            } else if (path.equals("/auth/me") && method.equals("GET")) {
                handleMe(ex);
            } else if (path.equals("/auth/register/client") && method.equals("POST")) {
                handleRegisterClient(ex);
            } else if (path.equals("/auth/register/consultant") && method.equals("POST")) {
                handleRegisterConsultant(ex);
            } else {
                send404(ex, "Auth endpoint not found: " + path);
            }
        } catch (IllegalArgumentException e) {
            send400(ex, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            send500(ex, "Unexpected error: " + e.getMessage());
        }
    }

    // ── POST /auth/register/client ────────────────────────────────────────────

    private void handleRegisterClient(HttpExchange ex) throws IOException {
        JsonObject body = parseBody(ex);
        String name     = str(body, "name");
        String email    = str(body, "email");
        String password = str(body, "password");

        ctx.authService.registerClient(name, email, password);

        JsonObject resp = new JsonObject();
        resp.addProperty("message", "Client registered successfully. You can now log in.");
        sendCreated(ex, resp);
    }

    // ── POST /auth/register/consultant ────────────────────────────────────────

    private void handleRegisterConsultant(HttpExchange ex) throws IOException {
        JsonObject body = parseBody(ex);
        String name     = str(body, "name");
        String email    = str(body, "email");
        String password = str(body, "password");
        String bio      = str(body, "bio");

        ctx.authService.registerConsultant(name, email, password, bio);

        JsonObject resp = new JsonObject();
        resp.addProperty("message", "Consultant registered. Awaiting admin approval.");
        sendCreated(ex, resp);
    }

    // ── POST /auth/login ──────────────────────────────────────────────────────

    private void handleLogin(HttpExchange ex) throws IOException {
        JsonObject body = parseBody(ex);
        String email    = str(body, "email");
        String password = str(body, "password");

        if (email == null || password == null) {
            send400(ex, "email and password are required.");
            return;
        }

        String token = ctx.authService.login(email, password);
        UserSession session = ctx.sessionStore.getSession(token);

        JsonObject resp = new JsonObject();
        resp.addProperty("token",  token);
        resp.addProperty("userId", session.getUserId());
        resp.addProperty("role",   session.getRole());
        resp.addProperty("name",   session.getName());
        resp.addProperty("email",  session.getEmail());
        sendOk(ex, resp);
    }

    // ── POST /auth/logout ─────────────────────────────────────────────────────

    private void handleLogout(HttpExchange ex) throws IOException {
        String token = extractToken(ex);
        ctx.authService.logout(token);

        JsonObject resp = new JsonObject();
        resp.addProperty("message", "Logged out successfully.");
        sendOk(ex, resp);
    }

    // ── GET /auth/me ──────────────────────────────────────────────────────────

    private void handleMe(HttpExchange ex) throws IOException {
        UserSession session = requireAuth(ex);
        if (session == null) return;

        JsonObject resp = new JsonObject();
        resp.addProperty("userId", session.getUserId());
        resp.addProperty("role",   session.getRole());
        resp.addProperty("name",   session.getName());
        resp.addProperty("email",  session.getEmail());
        sendOk(ex, resp);
    }
}
