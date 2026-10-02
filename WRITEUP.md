# Seat Reservation Service - Technical Writeup

## 1. Atomic Decision and Concurrency Control

PostgreSQL is the source of truth for reservation state.

A reservation is executed inside a single database transaction.

The reservation flow is:

1. Validate and normalize the requested seat numbers.
2. Sort the seat numbers deterministically.
3. Check the idempotency key.
4. Create or lock the per-user/show limit row.
5. Lock the requested seat rows using PostgreSQL pessimistic write locking.
6. Verify that all requested seats are available.
7. Verify the user's reservation limit.
8. Create the reservation.
9. Mark all requested seats as `CONFIRMED`.
10. Increment the user's reserved-seat count.
11. Commit the transaction.

The important locking mechanism is PostgreSQL row-level locking through JPA `PESSIMISTIC_WRITE`.

For a hot seat, concurrent requests cannot update the same seat row simultaneously.

For example:

```text
Request A ──┐
Request B ──┤
Request C ──┼──> PostgreSQL seat row
Request D ──┘