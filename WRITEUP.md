# Seat Reservation Service – Design Write-up

## 1. Atomic decision and concurrency safety

The main thing I wanted to get right in this assignment was the case where many users try to book the same seat at the same time. I did not want the application to make that decision only in memory, because that would become difficult to keep correct across concurrent requests.

I therefore made **PostgreSQL the source of truth** and used a Spring `@Transactional` transaction together with JPA's pessimistic write lock:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
```

For a booking, the service locks the requested seat rows before checking and changing their status. In simple terms, the flow is:

```text
BEGIN
  -> lock requested seat
  -> check that it is AVAILABLE
  -> create reservation
  -> change seat to CONFIRMED
COMMIT
```

If another request comes in for the same seat, it cannot make the same decision at the same time because it has to wait for that row lock. Once the first transaction commits, the second request sees the seat as `CONFIRMED` and gets a `409 Conflict`.

That is the main reason I consider the hot-seat decision race-safe: only one transaction can successfully change the seat from `AVAILABLE` to `CONFIRMED`.

### Multi-seat reservations and deadlocks

For multiple seats, I normalize and sort the seat numbers before locking them. This gives every request a consistent lock order.

For example:

```text
Request A: A3, A1 -> A1, A3
Request B: A1, A3 -> A1, A3
```

Without a consistent order, one transaction could hold A1 and wait for A3 while another holds A3 and waits for A1. Sorting the seats first avoids that lock-order inversion.

The reservation is also all-or-nothing. If one requested seat is unavailable, the transaction does not partially book the other seats.

---

## 2. Idempotency

I store the idempotency information directly in the `reservations` table. Along with the reservation, the table stores:

- `show_id`
- `user_id`
- `idempotency_key`
- `request_hash`

There is a database unique constraint on:

```text
(show_id, user_id, idempotency_key)
```

This means the same user can use the same key for another show, but cannot reuse it for a different request for the same show.

### How I handle retries

When a request comes in, the service first checks whether the same idempotency key was already used. If it was and the request is the same, the original reservation is returned instead of creating another reservation.

The database unique constraint is the final safety net when identical requests arrive concurrently. So the goal is exactly-once **business effect** for a given idempotency key. It does not mean the client can never send the request twice or that a network response can never be lost.

### Same key, different request

I also store a hash of the request data. If someone reuses the same idempotency key with a different seat selection, the hash will not match.

In that case the service returns:

```text
409 Conflict
IDEMPOTENCY_KEY_REUSED
```

The original reservation is left unchanged.

---

## 3. Holds and expiry

The seat model has three possible states:

```text
AVAILABLE
HELD
CONFIRMED
```

For this assignment, I did **not** implement a separate temporary hold flow or an automatic expiry job.

The current reservation flow is:

```text
AVAILABLE -> CONFIRMED
```

and cancellation does:

```text
CONFIRMED -> AVAILABLE
```

`HELD` is part of the state model and is supported by the show-state representation, but there is currently no timer or background process that creates or expires holds.

If I extended this into a real booking platform, I would add an `expires_at` field and a hold owner/token. Expiry would then be handled inside a transaction using the same locking approach, so an expired hold could not race with a new reservation.

---

## 4. Consistency vs availability during a partition

For reservation writes, I chose **consistency over availability**.

PostgreSQL is the source of truth for:

- seat state
- reservation state
- idempotency state
- per-user reservation count

If the application cannot reach the database, I would rather reject the reservation than make a local decision and risk selling the same seat twice from different application instances.

The readiness endpoint also checks the database:

```http
GET /health/ready
```

So the service can report that it is not ready when the database is unavailable.

For a seat reservation system, I think a temporary booking failure is safer than accepting a booking that cannot be durably reconciled.

---

## 5. Observability and what I would want to know at 2 AM

The application exposes Spring Boot Actuator and Prometheus metrics through:

```text
/actuator/prometheus
```

The current reservation counters are:

```text
reservation_success_total
reservation_conflict_total
reservation_error_total
reservation_cancelled_total
```

I also added request IDs. The service accepts `X-Request-ID` and puts the request ID into the logging context, which makes it easier to follow a request through the logs.

### What I would page for

If I were on call, the things I would care about most are:

1. **Database/readiness failures** – the application cannot reach PostgreSQL.
2. **Unexpected 5xx errors** – especially a sustained increase in reservation errors.
3. **High reservation latency** – particularly if database lock waits start increasing.
4. **Connection pool exhaustion** – connections stuck at the pool limit with requests waiting or timing out.
5. **State reconciliation failures** – any case where `available + held + confirmed` does not equal the total seat count.
6. **Repeated application restarts** – a service that keeps crashing or never becomes ready.

Today, the application provides the underlying counters and logs. In a production setup, I would add alert rules and dashboards on top of them.

---

## 6. AI usage

I used AI as a development assistant throughout the assignment, but I did not treat its output as something to blindly accept.

I used it mainly for:

- Spring Boot and JPA scaffolding
- understanding and reviewing PostgreSQL locking and transaction behavior
- drafting integration and concurrency tests
- troubleshooting Maven, Docker, and Render issues
- reviewing edge cases around reservations and idempotency
- drafting documentation such as the README and this write-up

The final implementation and design decisions were based on the assignment requirements and then checked against the actual application and database behavior.

For example, the use of PostgreSQL pessimistic locking, deterministic seat ordering, the locked per-user limit row, and the idempotency unique constraint was implemented and then validated with integration/concurrency tests rather than relying only on an AI-generated answer.

---

## 7. What I would do next

There are a few areas I would improve before calling this a production-ready service.

### Stronger authentication

The current bearer-token approach is intentionally lightweight for the assignment. In production I would replace it with JWT/OAuth2/OIDC and proper role-based authorization.

### Real hold and expiry support

I would add temporary holds with `expires_at`, a clear hold owner/token, and safe expiry handling.

### Better metrics

I would add reason-specific counters for things such as seat conflicts, per-user-limit conflicts, idempotent replays, lock waits, and database failures, along with latency histograms.

### Centralized observability

I would add centralized structured logs, distributed tracing, dashboards, and alerting rather than relying mainly on application and platform logs.

### Larger-scale performance testing

The 20,000-request test was used primarily to verify correctness. Next, I would run a larger production-sized environment and measure throughput, latency, lock contention, and connection-pool behavior as well.

### Resilience improvements

I would add rate limiting, back-pressure, and carefully chosen retry behavior for failures where retries are safe.

---

## Final thoughts

The main design decision in this assignment was to let **PostgreSQL make the final concurrency decision**. The seat row is locked inside a transaction, and the seat is only confirmed after it has been checked while holding that lock.

For multiple seats, I lock them in a deterministic order to avoid lock-order deadlocks. For idempotency, I combine the stored request hash with a database unique constraint so retries have one business effect and same-key/different-request cases are rejected.

I intentionally kept the hold/expiry flow out of the current implementation because it was not required for the implemented booking flow. I also chose consistency over availability for reservation writes, so the service does not invent booking state when the database is unavailable.
