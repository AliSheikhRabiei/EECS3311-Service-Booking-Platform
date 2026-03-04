package com.platform.domain;

/**
 * Abstract base class for all system users.
 * Centralises shared identity fields to avoid duplication.
 */
public abstract class User {

    private final String id;
    private final String name;
    private final String email;

    protected User(String id, String name, String email) {
        if (id == null || id.isBlank())    throw new IllegalArgumentException("User id must not be blank.");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("User name must not be blank.");
        if (email == null || email.isBlank()) throw new IllegalArgumentException("User email must not be blank.");
        this.id    = id;
        this.name  = name;
        this.email = email;
    }

    public String getId()    { return id; }
    public String getName()  { return name; }
    public String getEmail() { return email; }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[id=" + id + ", name=" + name + "]";
    }
}
