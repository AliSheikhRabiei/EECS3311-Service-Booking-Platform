package com.platform.http.handler;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.platform.http.AppContext;
import com.platform.http.BaseHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * POST /chat  { "message": "How do I book a session?" }
 *             → { "reply": "Here is how to book..." }
 *
 * Privacy rules (per project spec):
 *   - No personal user data is ever sent to the AI
 *   - No payment details, no private booking IDs
 *   - Only public platform information is included in the prompt
 *   - The AI key lives in an environment variable, never in code
 *
 * Requires a logged-in CLIENT session (Bearer token).
 * Uses Java's built-in HttpClient (Java 11+) — no new Maven dependency.
 */
public class ChatHandler extends BaseHandler {

    private static final String OPENAI_URL = "https://api.hpc-ai.com/inference/v1/chat/completions";
    private static final String MODEL      = "minimax/minimax-m2.5";

    // Read API key from environment variable — NEVER hardcode it
    private static final String API_KEY = System.getenv("OPENAI_API_KEY");

    private final HttpClient  httpClient;
    private final AppContext  ctx;

    public ChatHandler(AppContext ctx) {
        super(ctx.sessionStore);
        this.ctx        = ctx;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (handleCors(ex)) return;

        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            send404(ex, "Only POST /chat is supported.");
            return;
        }

        // Auth check — must be a logged-in user (any role)
        var session = requireAuth(ex);
        if (session == null) return;

        // Parse request
        JsonObject body = parseBody(ex);
        String userMessage = str(body, "message");
        if (userMessage == null || userMessage.isBlank()) {
            send400(ex, "message field is required.");
            return;
        }

        // Check API key is configured
        if (API_KEY == null || API_KEY.isBlank()) {
            send500(ex, "AI service is not configured. Set the OPENAI_API_KEY environment variable.");
            return;
        }

        try {
            String reply = callOpenAI(userMessage);
            JsonObject resp = new JsonObject();
            resp.addProperty("reply", reply);
            sendOk(ex, resp);
        } catch (Exception e) {
            System.err.println("[ChatHandler] OpenAI call failed: " + e.getMessage());
            send500(ex, "AI service temporarily unavailable. Please try again.");
        }
    }

    /**
     * Builds the OpenAI request.
     *
     * The system prompt contains ONLY public platform info:
     * - What the platform does
     * - How to book, pay, cancel
     * - Payment methods accepted
     * - General policies
     *
     * It explicitly instructs the AI NOT to make up user-specific info.
     */
    private String callOpenAI(String userMessage) throws Exception {
        // Build list of available services (public info only — title + price)
        StringBuilder serviceList = new StringBuilder();
        List<com.platform.domain.Service> services = ctx.catalog.listAllServices();
        if (services.isEmpty()) {
            serviceList.append("No services are listed yet.");
        } else {
            for (com.platform.domain.Service s : services) {
                serviceList.append("- ").append(s.getTitle())
                           .append(" ($").append(String.format("%.2f", s.getPrice()))
                           .append(", ").append(s.getDurationMin()).append(" min)\n");
            }
        }

        String systemPrompt = """
                You are a helpful customer assistant for the Service Booking & Consulting Platform.
                Your job is to answer questions about how the platform works. Be clear and friendly.

                === PLATFORM OVERVIEW ===
                This platform connects clients with professional consultants for one-on-one sessions.
                Services include software consulting, career advising, technical support, and more.

                === HOW BOOKING WORKS ===
                1. Browse available services on the services page.
                2. Select a service and choose an available time slot.
                3. Submit a booking request (status: REQUESTED).
                4. Wait for the consultant to accept your request (status: CONFIRMED).
                5. Pay for the session using a saved payment method (status: PAID).
                6. Attend the session. The consultant marks it complete (status: COMPLETED).

                === PAYMENT METHODS ACCEPTED ===
                - Credit Card (16-digit number, future expiry MM/yy, 3-4 digit CVV)
                - Debit Card (16-digit number, future expiry MM/yy)
                - PayPal (valid email address)
                - Bank Transfer (8-17 digit account number, 9-digit routing number)
                Note: All payments are simulated — no real money is charged.

                === CANCELLATION POLICY ===
                Clients can cancel bookings that are in REQUESTED, CONFIRMED, or PAID status.
                If a PAID booking is cancelled, a refund is issued according to the current policy (default 80%%).
                Some policies may not allow cancellations at all.

                === BOOKING STATUSES ===
                REQUESTED → Awaiting consultant approval
                CONFIRMED → Consultant accepted, awaiting payment
                PAID → Payment processed, session upcoming
                COMPLETED → Session finished
                REJECTED → Consultant declined the request
                CANCELLED → Booking was cancelled

                === CURRENTLY AVAILABLE SERVICES ===
                """ + serviceList + """

                === RULES FOR YOUR RESPONSES ===
                - Only answer questions about the platform, bookings, payments, and policies.
                - Do NOT make up or guess specific booking IDs, user names, or payment details.
                - Do NOT claim to have access to any user's personal account information.
                - If asked something you cannot answer, politely say so and suggest contacting support.
                - Keep answers concise and helpful (2-4 sentences max for simple questions).
                """;

        // Build the OpenAI JSON body
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", MODEL);
        requestBody.addProperty("max_tokens", 300);

        JsonArray messages = new JsonArray();

        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", systemPrompt);
        messages.add(systemMsg);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userMessage);
        messages.add(userMsg);

        requestBody.add("messages", messages);

        // Send to OpenAI
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OPENAI_URL))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            System.err.println("[ChatHandler] OpenAI returned " + response.statusCode() + ": " + response.body());
            throw new RuntimeException("OpenAI API error: " + response.statusCode());
        }

        // Parse response and extract the reply text
        JsonObject responseJson = new Gson().fromJson(response.body(), JsonObject.class);
        return responseJson
                .getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content").getAsString()
                .trim();
    }
}
