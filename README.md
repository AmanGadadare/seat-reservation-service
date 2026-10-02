# Seat Reservation Service

A concurrent seat reservation service built with Java, Spring Boot, PostgreSQL, JPA, Flyway, Docker, and Prometheus metrics.

The service is designed to handle high-contention reservations safely, including concurrent requests for the same seat, idempotent retries, per-user reservation limits, cancellation, and multi-seat atomicity.

## Tech Stack

- Java 25
- Spring Boot
- Spring Data JPA
- PostgreSQL
- Flyway
- Maven
- Docker / Docker Compose
- Micrometer + Prometheus
- JUnit
- Java Virtual Threads for burst testing

---

## Features

- Create shows with configurable seats and price
- Reserve one or multiple seats
- PostgreSQL row-level locking for concurrency control
- No double-selling under concurrent requests
- All-or-nothing multi-seat reservation
- Per-user reservation limit
- Idempotency key support
- Same-key/different-request protection
- Reservation cancellation
- Cancelled seats become available again
- Per-seat show state
- Liveness and readiness health endpoints
- Prometheus metrics
- Structured request logging
- Request correlation using `X-Request-ID`
- 20,000-request concurrent burst test

---

## Architecture

PostgreSQL is the source of truth for reservation state.

Reservation flow:

1. Authenticate the request using the simplified bearer-token mechanism.
2. Normalize and sort requested seat numbers.
3. Check the idempotency key.
4. Lock the per-user/show limit row.
5. Lock requested seat rows using PostgreSQL pessimistic write locking.
6. Verify that every requested seat is available.
7. Verify the user's reservation limit.
8. Create the reservation.
9. Mark all requested seats as `CONFIRMED`.
10. Increment the user's reserved-seat count.
11. Commit the transaction.

The entire reservation operation is executed inside a database transaction.

---

## Authentication

This assignment uses a deliberately simplified authentication mechanism.

### Admin

Use:

```text
Authorization: Bearer admin