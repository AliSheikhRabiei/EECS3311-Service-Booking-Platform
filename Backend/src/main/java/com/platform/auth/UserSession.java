package com.platform.auth;

/**
 * Represents a logged-in user session.
 * Stored in SessionStore keyed by the token string.
 */
public class UserSession {

    private final String userId;
    private final String role;   // ADMIN, CLIENT, CONSULTANT
    private final String name;
    private final String email;
    private final long   createdAt;

    public UserSession(String userId, String role, String name, String email) {
        this.userId    = userId;
        this.role      = role;
        this.name      = name;
        this.email     = email;
        this.createdAt = System.currentTimeMillis();
    }

    public String getUserId()  { return userId; }
    public String getRole()    { return role; }
    public String getName()    { return name; }
    public String getEmail()   { return email; }
    public long   getCreatedAt() { return createdAt; }

    /** Sessions expire after 8 hours. */
    public boolean isExpired() {
        return System.currentTimeMillis() - createdAt > 8L * 60 * 60 * 1000;
    }
}
