-- =============================================================
-- Service Booking Platform - Phase 2 Database Schema
-- Idempotent: safe to run on every server startup
-- =============================================================

CREATE TABLE IF NOT EXISTS users (
    id            VARCHAR(36)  PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    email         VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL   -- ADMIN, CLIENT, CONSULTANT
);

CREATE TABLE IF NOT EXISTS consultants (
    user_id             VARCHAR(36) PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    bio                 TEXT        NOT NULL DEFAULT '',
    registration_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
);

CREATE TABLE IF NOT EXISTS services (
    id            VARCHAR(36)       PRIMARY KEY,
    title         VARCHAR(255)      NOT NULL,
    description   TEXT              NOT NULL DEFAULT '',
    duration_min  INT               NOT NULL,
    price         DOUBLE PRECISION  NOT NULL,
    consultant_id VARCHAR(36)       NOT NULL REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS time_slots (
    id            VARCHAR(36)  PRIMARY KEY,
    consultant_id VARCHAR(36)  NOT NULL REFERENCES users(id),
    start_time    TIMESTAMP    NOT NULL,
    end_time      TIMESTAMP    NOT NULL,
    is_available  BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS bookings (
    id         VARCHAR(36) PRIMARY KEY,
    client_id  VARCHAR(36) NOT NULL REFERENCES users(id),
    service_id VARCHAR(36) NOT NULL REFERENCES services(id),
    slot_id    VARCHAR(36) NOT NULL REFERENCES time_slots(id),
    status     VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    created_at TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS payment_methods (
    id          VARCHAR(36)  PRIMARY KEY,
    client_id   VARCHAR(36)  NOT NULL REFERENCES users(id),
    method_type VARCHAR(30)  NOT NULL,
    details     TEXT         NOT NULL   -- JSON: masked stored fields only; CVV is never retained
);

CREATE TABLE IF NOT EXISTS payment_transactions (
    id         VARCHAR(36)      PRIMARY KEY,
    booking_id VARCHAR(36)      NOT NULL REFERENCES bookings(id),
    amount     DOUBLE PRECISION NOT NULL,
    status     VARCHAR(20)      NOT NULL,
    timestamp  TIMESTAMP        NOT NULL DEFAULT NOW()
);
