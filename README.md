# EECS3311-Service-Booking-Platform

## Team Members
- Salik Hassan
- Yasamin Kheirkhahan
- Ali Sheikh Rabiei

## Project Description
Booking platform
# Service Booking & Consulting Platform — Phase 1


**Course:** EECS 3311 — Software Design  
**University:** York University, Lassonde School of Engineering  
**Language:** Java 17 | Maven | JUnit 5  


---


## Project Overview


A backend system that connects Clients with Consultants offering professional
consulting services. Implements four Gang-of-Four design patterns:
State, Chain of Responsibility, Strategy, and Singleton.


**Key lifecycle rule:** A booking CANNOT be marked COMPLETED unless payment
has been successfully processed (PAID state).


---


## How to Run


### IntelliJ IDEA (recommended)
1. Open IntelliJ → Open → select this folder
2. Click Trust Project
3. Wait for Maven to import (~30 seconds)
4. Open src/main/java/com/platform/app/Main.java
5. Click the green triangle next to main()


### Maven CLI
```bash
mvn compile exec:java   # run the app
mvn test                # run JUnit 5 tests
```


### Plain javac
```bash
find src/main/java -name '*.java' > sources.txt
mkdir -p out
javac -d out @sources.txt
java -cp out com.platform.app.Main
```


---


## Package Structure


```
com.platform
├── app/          Entry point (Main.java) — wires services, runs demo
├── domain/       Core entities: User, Client, Consultant, Admin, Booking, Service, TimeSlot
├── state/        State pattern — BookingState interface + 7 concrete states
├── service/      Business logic — BookingService, ServiceCatalog, AvailabilityService
├── payment/      Payment subsystem — CoR chain, method types, PaymentService
├── policy/       Strategy pattern — 4 policy interfaces + defaults + PolicyManager
├── repository/   In-memory BookingRepository
└── notify/       NotificationService
```


---


## Design Patterns


| Pattern | Where | Why |
|---------|-------|-----|
| **State** | com.platform.state | Enforces booking lifecycle. complete() only works from PAID. |
| **Chain of Responsibility** | com.platform.payment | Validate → Process → MarkPaid pipeline. |
| **Strategy** | com.platform.policy | Admin can swap cancellation, refund, pricing, notification rules. |
| **Singleton** | PolicyManager | One shared policy configuration across all services. |


---


## Use Cases Implemented


| Actor | Use Cases |
|-------|-----------|
| Client | UC1 Browse, UC2 Request Booking, UC3 Cancel, UC4 History, UC5 Payment, UC6 Methods, UC7 Payment History |
| Consultant | UC8 Availability, UC9 Accept/Reject, UC10 Complete |
| Admin | UC11 Approve Registration, UC12 Define Policies |



## GitHub Repository


https://github.com/AliSheikhRabiei/EECS3311-Service-Booking-Platform
