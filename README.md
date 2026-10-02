# Seat Reservation Service

A concurrency-safe seat reservation REST API built using Java, Spring Boot and PostgreSQL.

The service is designed to handle concurrent reservation requests while maintaining:

- No double booking
- Atomic multi-seat reservations
- Idempotent retries
- Per-user reservation limits
- Safe cancellation
- Database-backed concurrency control
- Seat-state reconciliation
- Health/readiness monitoring
- Prometheus metrics
- Structured request/correlation logging
- Dockerized deployment
- Automated concurrency testing

---

## 1. Technology Stack

- Java 25
- Spring Boot 4.1.1
- Spring Web MVC
- Spring Data JPA
- Hibernate
- PostgreSQL 18
- Flyway
- Maven
- Docker
- Docker Compose
- JUnit 5
- Micrometer
- Prometheus
- Render

---

## 2. Repository

GitHub repository:

https://github.com/AmanGadadare/seat-reservation-service

---

## 3. Live Deployment

The application is deployed on Render.

### Base URL

https://seat-reservation-service-jbn4.onrender.com

### Live endpoints

Liveness:

https://seat-reservation-service-jbn4.onrender.com/health/live

Readiness:

https://seat-reservation-service-jbn4.onrender.com/health/ready

Prometheus:

https://seat-reservation-service-jbn4.onrender.com/actuator/prometheus

Create Show:

POST

https://seat-reservation-service-jbn4.onrender.com/shows

Get Show:

GET

https://seat-reservation-service-jbn4.onrender.com/shows/{showId}

Reserve Seats:

POST

https://seat-reservation-service-jbn4.onrender.com/shows/{showId}/reserve

Cancel Reservation:

POST

https://seat-reservation-service-jbn4.onrender.com/reservations/{reservationId}/cancel

---

# 4. Architecture

```text
                         Client
                           |
                           v
                  Spring Boot REST API
                           |
             +-------------+-------------+
             |                           |
             v                           v
       Reservation Service          Show Service
             |                           |
             +-------------+-------------+
                           |
                           v
                      PostgreSQL
                           |
          +----------------+----------------+
          |                |                |
          v                v                v
        shows            seats        reservations
                                             |
                                             v
                                    reservation_seats
                                             |
                                             v
     
                                    show_user_limits


# 20K Concurrent Hot-Seat Burst Test

This document explains exactly how to run the 20,000-request concurrency test for the Seat Reservation Service.

The test simulates a high-demand/on-sale scenario where **20,000 different users try to reserve the same seat at the same time**.

---

## 1. What This Test Verifies

The test verifies that:

- 20,000 reservation requests can be sent concurrently.
- All requests target the same hot seat.
- Exactly one request successfully reserves the seat.
- All remaining requests receive `409 Conflict`.
- No request receives `5xx`.
- No client/network errors occur.
- The final database state is consistent.
- Seat reconciliation is correct.

Expected result:

```text
20,000 total requests

HTTP 201 -> 1
HTTP 409 -> 19,999
Other    -> 0
Errors   -> 0

20K Concurrent Hot-Seat Burst Test
This guide explains how to run the 20,000 concurrent reservation test.
Prerequisites
Make sure the following are installed:
Java 25
Docker Desktop
Git
Check Java:
```bash
java -version
```
Check Docker:
```bash
docker --version
```
Check Docker Compose:
```bash
docker compose version
```
1. Clone the Repository
```bash
git clone https://github.com/AmanGadadare/seat-reservation-service.git
cd seat-reservation-service
```
2. Configure Database Password
   Create a `.env` file in the project root:
```text
DB_PASSWORD=your_postgres_password
```
Do not commit the `.env` file.
3. Start the Application
   Open Terminal 1 and run:
```bash
docker compose up --build
```
Keep this terminal running.
The application will start on:
```text
http://localhost:8080
```
4. Check Application Readiness
   Open Terminal 2 in the project root.
   Run:
```bash
curl.exe http://localhost:8080/health/ready
```
Expected response:
```json
{"status":"UP","database":"UP"}
```
Only continue when the database status is `UP`.
5. Run the Automated Tests
   Optional but recommended:
```bash
mvnw.cmd test
```
Expected result:
```text
BUILD SUCCESS
```
6. Run the 20K Burst Test
   From the project root, run:
```bash
java scripts/BurstTest.java
```
You do not need to manually create a show or reserve a seat.
The script automatically:
Checks application health.
Creates a fresh show with one seat.
Starts 20,000 reservation requests.
Uses Java 25 virtual threads.
Limits maximum in-flight requests to 500.
Sends all requests against the same hot seat.
Waits for all responses.
Checks the final show state.
Verifies seat reconciliation.
Prints `PASS` or failure information.
7. Expected Result
   A successful run should show:
```text
Total requests:       20000
HTTP 201:             1
HTTP 409:             19999
Other HTTP statuses:  0
Client exceptions:    0
```
Final state:
```text
Total seats:          1
Available seats:      0
Held seats:           0
Confirmed seats:      1
```
And:
```text
PASS
```
The important concurrency guarantee is:
```text
20,000 requests
       |
       v
    1 hot seat
       |
       +----> 1 request -> HTTP 201
       |
       +----> 19,999 requests -> HTTP 409
```
8. Reconciliation Check
   The final state must satisfy:
```text
Available + Held + Confirmed = Total
```
For the hot-seat test:
```text
0 + 0 + 1 = 1
```
9. If the Test Fails
   Check application logs:
```bash
docker compose logs -f app
```
Check database logs:
```bash
docker compose logs -f db
```
Check container status:
```bash
docker compose ps
```
Check readiness again:
```bash
curl.exe http://localhost:8080/health/ready
```
10. Restart the Test Environment
    Stop the containers:
```bash
docker compose down
```
Start again:
```bash
docker compose up --build
```
Then check:
```bash
curl.exe http://localhost:8080/health/ready
```
And run:
```bash
java scripts/BurstTest.java
```
Quick Run
If the repository is already cloned and `.env` is configured:
Terminal 1
```bash
docker compose up --build
```
Terminal 2
```bash
curl.exe http://localhost:8080/health/ready
```
Then:
```bash
mvnw.cmd test
```
Then:
```bash
java scripts/BurstTest.java
```
Test File
The burst test source code is located at:
```text
scripts/BurstTest.java
```
Run it from the project root using:
```bash
java scripts/BurstTest.java
```