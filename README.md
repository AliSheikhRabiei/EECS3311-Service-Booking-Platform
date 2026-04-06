# Service Booking & Consulting Platform — Phase 2

**Course:** EECS 3311 — Software Design | York University — Lassonde School of Engineering
**GitHub:** https://github.com/AliSheikhRabiei/EECS3311-Service-Booking-Platform

**Team:**
- Ali Sheikh Rabiei — 218475095
- Yasamin Kheirkhahan — 220067328

---

## What This Project Is

A full-stack web application that connects Clients with professional Consultants for one-on-one consulting sessions. Phase 2 extends the Phase 1 Java backend with a PostgreSQL database, a JSON REST API, a web frontend, an AI customer assistant chatbot, and Docker deployment.

---

## Quick Start — Run Everything with One Command

### Prerequisites
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) installed and running
- A `.env` file in the project root (see [Admin Login Setup](#admin-login-setup) below)

```bash
# Clone the repository
git clone https://github.com/AliSheikhRabiei/EECS3311-Service-Booking-Platform.git
cd EECS3311-Service-Booking-Platform

# Create your .env file (see section below)
cp .env.example .env
# Edit .env if you want to change the demo admin login or enable the chatbot

# Start all 3 containers
docker-compose up --build
```

When you see `[Server] Ready → http://localhost:8080`, open your browser:

| URL | What it is |
|---|---|
| **http://localhost:3000** | Frontend (use this) |
| http://localhost:8080 | Backend API directly |
| http://localhost:8080/health | API health check |

### Stop the system
```bash
docker-compose down          # Stop containers, keep database data
docker-compose down -v       # Stop containers AND delete all data (fresh start)
```

### Rebuild after code changes
```bash
docker-compose build --no-cache backend
docker-compose up
```

---

## API Key Setup

The AI Customer Assistant chatbot requires an API key, but the rest of the platform does not.

**Step 1:** Add this variable to the same project-root `.env` file:
```
OPENAI_API_KEY=sk-your-real-key-here
```

**Step 2:** Make sure `.env` is in `.gitignore` — **never commit your real key to GitHub**.

The key is read by `ChatHandler.java` via `System.getenv("OPENAI_API_KEY")`. Without it, the chatbot returns a 500 error. All other features work without the key.

---

## Admin Login Setup

The backend now reads the seeded admin login from `.env` instead of hardcoding it in Java.

Add these values to your project-root `.env` file:
```
ADMIN_EMAIL=admin@platform.com
ADMIN_PASSWORD=admin123
```

Those demo credentials are fine for grading/local testing and match `.env.example`.
Change them before any real deployment.

## How to Use the Application

### Admin Account
Created automatically on first startup using `ADMIN_EMAIL` and `ADMIN_PASSWORD` from `.env`.

If you copied the provided `.env.example` values unchanged:
- **Email:** admin@platform.com
- **Password:** admin123

### First-time Setup Flow
1. Open **http://localhost:3000**
2. Register a Consultant account (Register tab on login page)
3. Log in as Admin → approve the consultant
4. Log in as Consultant → add a service and time slots
5. Register a Client account → log in and book a session

### Accessing the AI Chatbot
1. Log in as a **Client**
2. Look for the **💬 button** in the bottom-right corner
3. Click it and ask any question about the platform

---

## Architecture Overview

```
Browser (port 3000)
    │
    ├── nginx serving static HTML/CSS/JS (Frontend container)
    │
    │  fetch("http://localhost:8080/...")
    ▼
Java HTTP Server (port 8080) — Backend container
    │
    ├── Auth layer (register, login, session tokens)
    ├── HTTP handlers (one per resource group)
    ├── Phase 1 business logic (BookingService, PaymentService, etc.)
    ├── DB repositories (JDBC → PostgreSQL)
    └── ChatHandler → hpc-ai.com API (AI chatbot)
    │
    ▼
PostgreSQL (port 5432) — Database container
```

**Three Docker containers:**
| Container | Image | Port |
|---|---|---|
| `booking_db` | postgres:16-alpine | 5432 |
| `booking_backend` | Built from Backend/Dockerfile | 8080 |
| `booking_frontend` | Built from Frontend/Dockerfile | 3000 |

---

## Design Patterns (from Phase 1, unchanged)

| Pattern | Where | What it enforces |
|---|---|---|
| **State** | `state/` package | Booking lifecycle — cannot complete without paying first |
| **Chain of Responsibility** | `payment/` package | Payment pipeline: validate → process → mark paid |
| **Strategy** | `policy/` package | Swappable cancellation, refund, notification, pricing rules |
| **Singleton** | `PolicyManager.java` | Single shared policy configuration across all services |

---

## API Reference

All responses are `application/json`. Protected endpoints need `Authorization: Bearer <token>`.

### Auth (no token required)
```
POST /auth/register/client       { name, email, password }
POST /auth/register/consultant   { name, email, password, bio }
POST /auth/login                 { email, password } → { token, userId, role, name, email }
POST /auth/logout
GET  /auth/me
```

### Public (no token required)
```
GET  /services                   → list all services
GET  /services/{id}/slots        → available slots for a service
GET  /health                     → { "status": "ok" }
```

### Client endpoints
```
GET  /bookings                   → my bookings
POST /bookings                   { serviceId, slotUuid }
POST /bookings/{id}/cancel       { reason }
GET  /payments/methods           → saved payment methods
POST /payments/methods           { type, ...fields }
PUT  /payments/methods/{id}      { type, ...fields }   ← update
DELETE /payments/methods/{id}
POST /payments/pay               { bookingId, methodId }
GET  /payments/history
POST /chat                       { message } → { reply }
```

### Consultant endpoints
```
GET  /bookings
POST /bookings/{id}/confirm
POST /bookings/{id}/reject       { reason }
POST /bookings/{id}/complete
GET  /slots
POST /slots                      { start, end }  ← auto-splits into 1-hour slots
DELETE /slots/{uuid}
POST /services                   { title, description, durationMin, price }
```

### Admin endpoints
```
GET  /admin/consultants
POST /admin/consultants/{id}/approve
POST /admin/consultants/{id}/reject
GET  /admin/policies
POST /admin/policies/cancellation  { type: DEFAULT|STRICT }
POST /admin/policies/refund        { type: DEFAULT|FULL|NONE }
```

---

## Run Without Docker (Local Development)

**Prerequisites:** Java 17+, Maven, PostgreSQL running locally.

```bash
# 1. Create the database
psql -U postgres -c "CREATE DATABASE booking_platform;"

# 2. Set environment variables (PowerShell)
$env:DB_URL      = "jdbc:postgresql://localhost:5432/booking_platform"
$env:DB_USER     = "postgres"
$env:DB_PASSWORD = "your_postgres_password"
$env:PORT        = "8080"
$env:ADMIN_EMAIL = "admin@platform.com"
$env:ADMIN_PASSWORD = "admin123"
$env:OPENAI_API_KEY = "sk-your-key"

# 3. Run the Phase 2 HTTP server
cd Backend
mvn compile exec:java -Dexec.mainClass=com.platform.http.HttpServerLauncher

# Open the frontend by just double-clicking Frontend/index.html in your browser
# or serve it with any local HTTP server
```

**Run the original Phase 1 terminal demo (still works):**
```bash
cd Backend
mvn exec:java
```

**Run tests:**
```bash
cd Backend
mvn test
```

---

## Project Structure

```
EECS3311-Service-Booking-Platform/
├── docker-compose.yml              ← START HERE (all 3 containers)
├── .env                            ← your API key (never commit this)
├── .env.example                    ← template
├── .gitignore
├── AI_CHATBOT_DOCUMENTATION.md     ← chatbot design doc
├── Phase2_ProjectGuide.md          ← complete Phase 2 reference
│
├── Backend/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/platform/
│       ├── domain/      ← Phase 1: Booking, Client, Consultant, Service, etc.
│       ├── state/       ← Phase 1: State pattern (booking lifecycle)
│       ├── payment/     ← Phase 1: Chain of Responsibility (payment pipeline)
│       ├── policy/      ← Phase 1: Strategy pattern (cancellation, refund, etc.)
│       ├── service/     ← Phase 1: BookingService, AvailabilityService, etc.
│       ├── notify/      ← Phase 1: NotificationService
│       ├── repository/  ← Phase 1: in-memory base classes (extended by db/)
│       ├── app/         ← Phase 1: Main.java terminal demo (still works)
│       ├── db/          ← Phase 2 NEW: JDBC repositories (PostgreSQL)
│       ├── auth/        ← Phase 2 NEW: login, sessions, password hashing
│       └── http/        ← Phase 2 NEW: REST API handlers
│           ├── AppContext.java
│           ├── BaseHandler.java
│           ├── HttpServerLauncher.java
│           ├── dto/Dtos.java
│           └── handler/
│               ├── AuthHandler.java
│               ├── ServicesHandler.java
│               ├── SlotsHandler.java
│               ├── BookingsHandler.java
│               ├── PaymentsHandler.java
│               ├── AdminHandler.java
│               └── ChatHandler.java   ← AI chatbot
│
└── Frontend/
    ├── Dockerfile
    ├── index.html
    ├── login.html
    ├── client.html        ← Browse, Book, Pay, AI Chat
    ├── consultant.html    ← Manage slots, accept/reject, complete
    ├── admin.html         ← Approve consultants, set policies
    ├── css/style.css
    └── js/
        ├── api.js         ← all backend calls
        └── auth.js        ← session management, shared utilities
```

---

## Team Contributions — Phase 2

**Ali Sheikh Rabiei:**
- Database layer (`db/` package): all JDBC repositories, schema design, BookingStateFactory
- Authentication (`auth/` package): PasswordUtil, SessionStore, AuthService
- HTTP server foundation: AppContext, BaseHandler, HttpServerLauncher
- Admin and consultant HTTP handlers
- Frontend basic
- AI Customer Assistant (ChatHandler, system prompt design)
- Backend Dockerfile and docker-compose configuration

**Yasamin Kheirkhahan:**
- Client and booking HTTP handlers improvements
- Payment subsystem HTTP integration improvements
- Frontend improvements (HTML/CSS/JS pages)
- Bug fixes in multiple places booking state stale reference, slot in-memory sync, SQL schema parsing
