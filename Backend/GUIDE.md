# Service Booking Platform — Phase 2 Run & Test Guide

## Quick Start

### Option A — Docker (recommended)

```bash
# From the Backend/ folder:
docker-compose up --build
```

That single command:
1. Builds the fat JAR inside a Maven container
2. Starts PostgreSQL 16
3. Starts the backend (waits for Postgres healthcheck)
4. Runs schema.sql automatically
5. Creates the default admin account

**Backend ready at:** `http://localhost:8080`

```bash
docker-compose down        # stop, keep DB data
docker-compose down -v     # stop + wipe DB (fresh start)
```

---

### Option B — Run locally (Java 17 + PostgreSQL installed)

```sql
-- In psql or pgAdmin:
CREATE DATABASE booking_platform;
```

```bash
# Mac/Linux
export DB_URL=jdbc:postgresql://localhost:5432/booking_platform
export DB_USER=postgres
export DB_PASSWORD=postgres

# Windows PowerShell
$env:DB_URL="jdbc:postgresql://localhost:5432/booking_platform"
$env:DB_USER="postgres"
$env:DB_PASSWORD="postgres"
```

```bash
mvn compile exec:java -Dexec.mainClass=com.platform.http.HttpServerLauncher
```

Original Phase 1 terminal demo still works unchanged:
```bash
mvn compile exec:java
```

---

## API Endpoints — curl Examples

> **Windows PowerShell:** Use `Invoke-RestMethod` (see section at the bottom) or `curl.exe`.

---

### Health Check
```bash
curl http://localhost:8080/health
```

---

### Auth

```bash
# Register client
curl -X POST http://localhost:8080/auth/register/client \
  -H "Content-Type: application/json" \
  -d '{"name":"Dave Client","email":"dave@example.com","password":"pass123"}'

# Register consultant
curl -X POST http://localhost:8080/auth/register/consultant \
  -H "Content-Type: application/json" \
  -d '{"name":"Bob Consultant","email":"bob@example.com","password":"pass123","bio":"Java expert"}'

# Login — copy the token from the response
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@platform.com","password":"admin123"}'

# Who am I?
curl http://localhost:8080/auth/me \
  -H "Authorization: Bearer <TOKEN>"

# Logout
curl -X POST http://localhost:8080/auth/logout \
  -H "Authorization: Bearer <TOKEN>"
```

---

### Services (UC1)

```bash
# List all services — no token needed
curl http://localhost:8080/services

# Get one service
curl http://localhost:8080/services/<serviceId>

# Get available slots for a service — no token needed
curl http://localhost:8080/services/<serviceId>/slots

# Add a service (consultant only)
curl -X POST http://localhost:8080/services \
  -H "Authorization: Bearer <CONSULTANT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"title":"Java Tutoring","description":"One-on-one coaching","durationMin":60,"price":100.0}'
```

---

### Slots / Availability (UC8) — consultant only

```bash
# List my slots
curl http://localhost:8080/slots \
  -H "Authorization: Bearer <CONSULTANT_TOKEN>"

# Add one slot
curl -X POST http://localhost:8080/slots \
  -H "Authorization: Bearer <CONSULTANT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"start":"2026-06-01T10:00:00","end":"2026-06-01T11:00:00"}'

# Add a block (auto-split into 1-hour slots)
curl -X POST http://localhost:8080/slots/block \
  -H "Authorization: Bearer <CONSULTANT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"start":"2026-06-02T09:00:00","end":"2026-06-02T13:00:00"}'

# Delete a slot (must not be reserved)
curl -X DELETE http://localhost:8080/slots/<slotUuid> \
  -H "Authorization: Bearer <CONSULTANT_TOKEN>"
```

---

### Bookings (UC2, UC3, UC4, UC9, UC10)

```bash
# Create booking (client) — needs serviceId + slotUuid
curl -X POST http://localhost:8080/bookings \
  -H "Authorization: Bearer <CLIENT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"serviceId":"<serviceId>","slotUuid":"<slotUuid>"}'

# List bookings (client=own, consultant=theirs, admin=all)
curl http://localhost:8080/bookings \
  -H "Authorization: Bearer <TOKEN>"

# Get one booking
curl http://localhost:8080/bookings/<bookingId> \
  -H "Authorization: Bearer <TOKEN>"

# Consultant: confirm
curl -X POST http://localhost:8080/bookings/<bookingId>/confirm \
  -H "Authorization: Bearer <CONSULTANT_TOKEN>"

# Consultant: reject
curl -X POST http://localhost:8080/bookings/<bookingId>/reject \
  -H "Authorization: Bearer <CONSULTANT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"reason":"Schedule conflict"}'

# Consultant: complete (booking must be PAID)
curl -X POST http://localhost:8080/bookings/<bookingId>/complete \
  -H "Authorization: Bearer <CONSULTANT_TOKEN>"

# Client: cancel
curl -X POST http://localhost:8080/bookings/<bookingId>/cancel \
  -H "Authorization: Bearer <CLIENT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"reason":"Change of plans"}'
```

---

### Payments (UC5, UC6, UC7)

```bash
# Add payment method
curl -X POST http://localhost:8080/payments/methods \
  -H "Authorization: Bearer <CLIENT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"type":"CREDIT_CARD","cardNumber":"1234567890123456","expiry":"12/27","cvv":"123"}'

# Other types:
# PayPal:        {"type":"PAYPAL","email":"dave@paypal.com"}
# Bank transfer: {"type":"BANK_TRANSFER","accountNumber":"12345678","routingNumber":"123456789"}
# Debit card:    {"type":"DEBIT_CARD","cardNumber":"9876543210987654","expiry":"08/28"}

# List methods
curl http://localhost:8080/payments/methods \
  -H "Authorization: Bearer <CLIENT_TOKEN>"

# Remove a method
curl -X DELETE http://localhost:8080/payments/methods/<methodId> \
  -H "Authorization: Bearer <CLIENT_TOKEN>"

# Process payment (booking must be CONFIRMED)
curl -X POST http://localhost:8080/payments/pay \
  -H "Authorization: Bearer <CLIENT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"bookingId":"<bookingId>","methodId":"<methodId>"}'

# Payment history
curl http://localhost:8080/payments/history \
  -H "Authorization: Bearer <CLIENT_TOKEN>"
```

> **Note:** Payment has a 10% random failure rate (Phase 1 design). If you get status FAILED,
> just call POST /payments/pay again.

---

### Admin (UC11, UC12)

```bash
# List all consultants
curl http://localhost:8080/admin/consultants \
  -H "Authorization: Bearer <ADMIN_TOKEN>"

# Approve a consultant
curl -X POST http://localhost:8080/admin/consultants/<userId>/approve \
  -H "Authorization: Bearer <ADMIN_TOKEN>"

# Reject a consultant
curl -X POST http://localhost:8080/admin/consultants/<userId>/reject \
  -H "Authorization: Bearer <ADMIN_TOKEN>"

# View current policies
curl http://localhost:8080/admin/policies \
  -H "Authorization: Bearer <ADMIN_TOKEN>"

# Change cancellation policy (DEFAULT or STRICT)
curl -X POST http://localhost:8080/admin/policies/cancellation \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"type":"STRICT"}'

# Change refund policy (DEFAULT=80%, FULL=100%, NONE=0%)
curl -X POST http://localhost:8080/admin/policies/refund \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"type":"FULL"}'
```

---

## Full Happy Path (end-to-end sequence)

```bash
# Step 1 — Register
curl -s -X POST http://localhost:8080/auth/register/consultant \
  -H "Content-Type: application/json" \
  -d '{"name":"Bob","email":"bob@example.com","password":"pass123","bio":"Java expert"}'

curl -s -X POST http://localhost:8080/auth/register/client \
  -H "Content-Type: application/json" \
  -d '{"name":"Dave","email":"dave@example.com","password":"pass123"}'

# Step 2 — Login, save tokens
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@platform.com","password":"admin123"}' | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

BOB_TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"bob@example.com","password":"pass123"}' | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

DAVE_TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"dave@example.com","password":"pass123"}' | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

# Step 3 — Admin: get Bob's userId then approve him
curl -s http://localhost:8080/admin/consultants -H "Authorization: Bearer $ADMIN_TOKEN"
# Copy Bob's "id" from the response

BOB_ID="<paste-bobs-id-here>"
curl -X POST http://localhost:8080/admin/consultants/$BOB_ID/approve \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# Step 4 — Bob: add service
SERVICE_ID=$(curl -s -X POST http://localhost:8080/services \
  -H "Authorization: Bearer $BOB_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Java Tutoring","description":"Expert coaching","durationMin":60,"price":100.0}' \
  | grep -o '"serviceId":"[^"]*"' | cut -d'"' -f4)

# Step 5 — Bob: add a slot
SLOT_UUID=$(curl -s -X POST http://localhost:8080/slots \
  -H "Authorization: Bearer $BOB_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"start":"2026-06-01T10:00:00","end":"2026-06-01T11:00:00"}' \
  | grep -o '"id":"[^"]*"' | cut -d'"' -f4)

# Step 6 — Dave: add payment method
METHOD_ID=$(curl -s -X POST http://localhost:8080/payments/methods \
  -H "Authorization: Bearer $DAVE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"type":"CREDIT_CARD","cardNumber":"1234567890123456","expiry":"12/27","cvv":"123"}' \
  | grep -o '"methodId":"[^"]*"' | cut -d'"' -f4)

# Step 7 — Dave: book
BOOKING_ID=$(curl -s -X POST http://localhost:8080/bookings \
  -H "Authorization: Bearer $DAVE_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"serviceId\":\"$SERVICE_ID\",\"slotUuid\":\"$SLOT_UUID\"}" \
  | grep -o '"bookingId":"[^"]*"' | cut -d'"' -f4)

# Step 8 — Bob: confirm
curl -X POST http://localhost:8080/bookings/$BOOKING_ID/confirm \
  -H "Authorization: Bearer $BOB_TOKEN"

# Step 9 — Dave: pay (retry if FAILED — 10% random failure rate)
curl -X POST http://localhost:8080/payments/pay \
  -H "Authorization: Bearer $DAVE_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"bookingId\":\"$BOOKING_ID\",\"methodId\":\"$METHOD_ID\"}"

# Step 10 — Bob: complete
curl -X POST http://localhost:8080/bookings/$BOOKING_ID/complete \
  -H "Authorization: Bearer $BOB_TOKEN"

# Done! Check final booking status:
curl http://localhost:8080/bookings/$BOOKING_ID -H "Authorization: Bearer $DAVE_TOKEN"
```

---

## Windows PowerShell

```powershell
# Login
$resp  = Invoke-RestMethod -Method Post `
           -Uri "http://localhost:8080/auth/login" `
           -ContentType "application/json" `
           -Body '{"email":"admin@platform.com","password":"admin123"}'
$TOKEN = $resp.token

# Authenticated GET
Invoke-RestMethod -Uri "http://localhost:8080/services" `
  -Headers @{ Authorization = "Bearer $TOKEN" }

# Authenticated POST
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/bookings/BK-XXXXX/confirm" `
  -Headers @{ Authorization = "Bearer $TOKEN" }
```

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| `Connection refused` | Backend not started — check `docker-compose up` output |
| `401 Unauthorised` | Token missing or expired (8 h TTL) — log in again |
| `403 Forbidden` | Wrong role for this endpoint (e.g. client calling /admin) |
| `400 Bad Request` | Read the `error` field in the response body |
| Payment status `FAILED` | 10% random failure by design — just retry POST /payments/pay |
| `Consultant is not approved` | Admin must approve via POST /admin/consultants/{id}/approve |
| DB errors on Docker startup | Wait a few more seconds — Postgres healthcheck retries 10× |
| `docker-compose` not found | Try `docker compose` (no hyphen) for Docker Desktop v2+ |
