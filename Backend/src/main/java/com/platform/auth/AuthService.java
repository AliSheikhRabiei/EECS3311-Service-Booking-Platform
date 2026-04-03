package com.platform.auth;

import com.platform.db.UserRepository;
import com.platform.domain.RegistrationStatus;

import java.util.Optional;
import java.util.UUID;

/**
 * Handles user registration and login.
 * Works with UserRepository for DB access and SessionStore for token management.
 */
public class AuthService {

    private final UserRepository userRepository;
    private final SessionStore   sessionStore;

    public AuthService(UserRepository userRepository, SessionStore sessionStore) {
        this.userRepository = userRepository;
        this.sessionStore   = sessionStore;
    }

    // ── Register ─────────────────────────────────────────────────────────────

    /**
     * Registers a new CLIENT.
     * @throws IllegalArgumentException if the email is already in use
     */
    public void registerClient(String name, String email, String password) {
        validateRegistration(name, email, password);
        String id   = UUID.randomUUID().toString();
        String hash = PasswordUtil.hash(password);
        userRepository.saveUser(id, name, email, hash, "CLIENT");
        System.out.println("[AuthService] Registered CLIENT account.");
    }

    /**
     * Registers a new CONSULTANT (status = PENDING until Admin approves).
     * @throws IllegalArgumentException if the email is already in use
     */
    public void registerConsultant(String name, String email, String password, String bio) {
        validateRegistration(name, email, password);
        String id   = UUID.randomUUID().toString();
        String hash = PasswordUtil.hash(password);
        userRepository.saveUser(id, name, email, hash, "CONSULTANT");
        userRepository.saveConsultantProfile(id,
                bio == null ? "" : bio,
                RegistrationStatus.PENDING.name());
        System.out.println("[AuthService] Registered CONSULTANT account (PENDING).");
    }

    /**
     * Creates an ADMIN account (called from server setup, not a public endpoint).
     */
    public void createAdmin(String name, String email, String password) {
        if (userRepository.emailExists(email)) return; // already exists — skip silently
        String id   = UUID.randomUUID().toString();
        String hash = PasswordUtil.hash(password);
        userRepository.saveUser(id, name, email, hash, "ADMIN");
        System.out.println("[AuthService] Admin account ensured.");
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    /**
     * Verifies credentials and creates a session.
     * @return session token string
     * @throws IllegalArgumentException on bad credentials
     */
    public String login(String email, String password) {
        Optional<UserRepository.UserRow> rowOpt = userRepository.findByEmail(email);
        if (rowOpt.isEmpty()) {
            throw new IllegalArgumentException("Invalid email or password.");
        }
        UserRepository.UserRow row = rowOpt.get();
        if (!PasswordUtil.verify(password, row.passwordHash)) {
            throw new IllegalArgumentException("Invalid email or password.");
        }
        String token = sessionStore.createSession(row.id, row.role, row.name, row.email);
        System.out.println("[AuthService] Login succeeded for role=" + row.role);
        return token;
    }

    /** Invalidates the session for the given token. */
    public void logout(String token) {
        sessionStore.removeSession(token);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void validateRegistration(String name, String email, String password) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Name is required.");
        if (email == null || !email.contains("@"))
            throw new IllegalArgumentException("Valid email is required.");
        if (password == null || password.length() < 6)
            throw new IllegalArgumentException("Password must be at least 6 characters.");
        if (userRepository.emailExists(email))
            throw new IllegalArgumentException("Email already registered.");
    }
}
