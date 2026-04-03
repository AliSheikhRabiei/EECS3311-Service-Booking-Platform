package com.platform.http;

import com.platform.http.handler.*;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class HttpServerLauncher {

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        System.out.println("[Server] Starting Phase 2 backend on port " + port + " ...");

        AppContext ctx = new AppContext();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Auth
        AuthHandler authHandler = new AuthHandler(ctx);
        server.createContext("/auth/login",                 authHandler);
        server.createContext("/auth/logout",                authHandler);
        server.createContext("/auth/me",                    authHandler);
        server.createContext("/auth/register/client",       authHandler);
        server.createContext("/auth/register/consultant",   authHandler);

        // Services (UC1)
        server.createContext("/services", new ServicesHandler(ctx));

        // Slots / Availability (UC8)
        server.createContext("/slots", new SlotsHandler(ctx));

        // Bookings (UC2, UC3, UC4, UC9, UC10)
        server.createContext("/bookings", new BookingsHandler(ctx));

        // Payments (UC5, UC6, UC7)
        server.createContext("/payments", new PaymentsHandler(ctx));

        // Admin (UC11, UC12)
        server.createContext("/admin", new AdminHandler(ctx));

        // AI Customer Assistant
        server.createContext("/chat", new ChatHandler(ctx));

        // Health check
        server.createContext("/health", exchange -> {
            byte[] resp = "{\"status\":\"ok\"}".getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.getResponseBody().close();
        });

        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();

        System.out.println("[Server] Ready → http://localhost:" + port);
        System.out.println("[Server] Health → http://localhost:" + port + "/health");
        System.out.println("[Server] Chat   → http://localhost:" + port + "/chat");
    }
}
