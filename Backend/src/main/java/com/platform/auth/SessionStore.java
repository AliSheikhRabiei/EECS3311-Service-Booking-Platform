package com.platform.auth;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory session store. Tokens are random 32-byte base64 strings.
 * Expired sessions are lazily cleaned out on each lookup.
 */
public class SessionStore {

    private final Map<String, UserSession> sessions = new ConcurrentHashMap<>();
    private final SecureRandom             random   = new SecureRandom();

    /** Creates a session for the given user and returns the token. */
    public String createSession(String userId, String role, String name, String email) {
        String token = generateToken();
        sessions.put(token, new UserSession(userId, role, name, email));
        return token;
    }

    /** Returns the session for this token, or null if missing/expired. */
    public UserSession getSession(String token) {
        if (token == null) return null;
        UserSession session = sessions.get(token);
        if (session == null) return null;
        if (session.isExpired()) {
            sessions.remove(token);
            return null;
        }
        return session;
    }

    /** Invalidates a token (logout). */
    public void removeSession(String token) {
        if (token != null) sessions.remove(token);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
