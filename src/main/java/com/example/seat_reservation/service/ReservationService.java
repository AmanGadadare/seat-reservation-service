package com.example.seat_reservation.service;

import com.example.seat_reservation.dto.ReservationResponse;
import com.example.seat_reservation.dto.ReserveRequest;
import com.example.seat_reservation.entity.Reservation;
import com.example.seat_reservation.entity.ReservationSeat;
import com.example.seat_reservation.entity.ReservationStatus;
import com.example.seat_reservation.entity.Seat;
import com.example.seat_reservation.entity.SeatStatus;
import com.example.seat_reservation.entity.Show;
import com.example.seat_reservation.entity.ShowUserLimit;
import com.example.seat_reservation.exception.BookingConflictException;
import com.example.seat_reservation.metrics.ReservationMetrics;
import com.example.seat_reservation.repository.ReservationRepository;
import com.example.seat_reservation.repository.ReservationSeatRepository;
import com.example.seat_reservation.repository.SeatRepository;
import com.example.seat_reservation.repository.ShowRepository;
import com.example.seat_reservation.repository.ShowUserLimitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class ReservationService {

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final ShowUserLimitRepository showUserLimitRepository;
    private final ReservationMetrics reservationMetrics;

    public ReservationService(
            ShowRepository showRepository,
            SeatRepository seatRepository,
            ReservationRepository reservationRepository,
            ReservationSeatRepository reservationSeatRepository,
            ShowUserLimitRepository showUserLimitRepository,
            ReservationMetrics reservationMetrics
    ) {
        this.showRepository = showRepository;
        this.seatRepository = seatRepository;
        this.reservationRepository = reservationRepository;
        this.reservationSeatRepository = reservationSeatRepository;
        this.showUserLimitRepository = showUserLimitRepository;
        this.reservationMetrics = reservationMetrics;
    }

    @Transactional
    public ReservationResponse reserve(
            UUID showId,
            String userId,
            ReserveRequest request
    ) {

        try {

            List<String> requestedSeats =
                    normalizeSeatNumbers(request.getSeats());

            String requestHash =
                    calculateRequestHash(requestedSeats);

            /*
             * Fast idempotency check.
             */
            var existingReservation =
                    reservationRepository
                            .findByShowIdAndUserIdAndIdempotencyKey(
                                    showId,
                                    userId,
                                    request.getIdempotencyKey()
                            );

            if (existingReservation.isPresent()) {

                Reservation existing =
                        existingReservation.get();

                if (!existing.getRequestHash().equals(requestHash)) {

                    throw new BookingConflictException(
                            "IDEMPOTENCY_KEY_REUSED",
                            "Idempotency key was already used with a different request"
                    );
                }

                reservationMetrics.recordSuccess();

                return buildResponse(existing);
            }

            /*
             * Find the show.
             */
            Show show =
                    showRepository.findById(showId)
                            .orElseThrow(() ->
                                    new BookingConflictException(
                                            "SHOW_NOT_FOUND",
                                            "Show not found"
                                    )
                            );

            /*
             * Create the per-user limit row if it does not already exist.
             *
             * This operation is safe under concurrent requests because
             * the database primary key prevents duplicate rows.
             */
            showUserLimitRepository.insertIfAbsent(
                    showId,
                    userId
            );

            /*
             * Lock the user/show limit row.
             *
             * This serializes concurrent reservations made by the
             * same user for the same show.
             */
            ShowUserLimit userLimit =
                    showUserLimitRepository
                            .findByShowIdAndUserId(
                                    showId,
                                    userId
                            )
                            .orElseThrow(() ->
                                    new IllegalStateException(
                                            "Unable to create user reservation limit row"
                                    )
                            );

            /*
             * Check idempotency again after acquiring the user lock.
             *
             * This handles two identical requests arriving concurrently.
             */
            existingReservation =
                    reservationRepository
                            .findByShowIdAndUserIdAndIdempotencyKey(
                                    showId,
                                    userId,
                                    request.getIdempotencyKey()
                            );

            if (existingReservation.isPresent()) {

                Reservation existing =
                        existingReservation.get();

                if (!existing.getRequestHash().equals(requestHash)) {

                    throw new BookingConflictException(
                            "IDEMPOTENCY_KEY_REUSED",
                            "Idempotency key was already used with a different request"
                    );
                }

                reservationMetrics.recordSuccess();

                return buildResponse(existing);
            }

            /*
             * Always lock seats in deterministic seat-number order.
             *
             * This reduces deadlock risk when multiple requests
             * attempt to reserve multiple seats concurrently.
             */
            List<Seat> seats =
                    seatRepository
                            .findByShowIdAndSeatNumberInOrderBySeatNumber(
                                    showId,
                                    requestedSeats
                            );

            if (seats.size() != requestedSeats.size()) {

                throw new BookingConflictException(
                        "SEAT_NOT_FOUND",
                        "One or more requested seats do not exist"
                );
            }

            /*
             * All-or-nothing behaviour:
             * every requested seat must currently be AVAILABLE.
             */
            for (Seat seat : seats) {

                if (seat.getStatus() != SeatStatus.AVAILABLE) {

                    throw new BookingConflictException(
                            "SEAT_UNAVAILABLE",
                            "One or more requested seats are not available"
                    );
                }
            }

            /*
             * Per-user limit check while the user-limit row is locked.
             */
            int requestedSeatCount =
                    seats.size();

            if (userLimit.getReservedSeats()
                    + requestedSeatCount
                    > show.getPerUserLimit()) {

                throw new BookingConflictException(
                        "USER_LIMIT_EXCEEDED",
                        "User reservation limit exceeded"
                );
            }

            long amountPaise =
                    show.getPricePaise()
                            * requestedSeatCount;

            /*
             * Create reservation.
             */
            Reservation reservation =
                    new Reservation();

            reservation.setShowId(showId);
            reservation.setUserId(userId);
            reservation.setAmountPaise(amountPaise);
            reservation.setStatus(
                    ReservationStatus.CONFIRMED.name()
            );
            reservation.setIdempotencyKey(
                    request.getIdempotencyKey()
            );
            reservation.setRequestHash(requestHash);

            reservation =
                    reservationRepository.save(reservation);

            /*
             * Confirm every seat and associate it
             * with this reservation.
             */
            for (Seat seat : seats) {

                seat.setStatus(
                        SeatStatus.CONFIRMED
                );

                seat.setReservationId(
                        reservation.getId()
                );

                seatRepository.save(seat);

                ReservationSeat reservationSeat =
                        new ReservationSeat();

                reservationSeat.setId(
                        new com.example.seat_reservation.entity.ReservationSeatId(
                                reservation.getId(),
                                seat.getId()
                        )
                );

                reservationSeatRepository.save(
                        reservationSeat
                );
            }

            /*
             * Increase the user's reserved-seat count.
             */
            userLimit.setReservedSeats(
                    userLimit.getReservedSeats()
                            + requestedSeatCount
            );

            showUserLimitRepository.save(
                    userLimit
            );

            reservationMetrics.recordSuccess();

            return buildResponse(reservation);

        } catch (BookingConflictException e) {

            reservationMetrics.recordConflict();

            throw e;

        } catch (RuntimeException e) {

            reservationMetrics.recordError();

            throw e;
        }
    }

    @Transactional
    public ReservationResponse cancel(
            UUID reservationId,
            String userId
    ) {

        try {

            /*
             * Step 1:
             * Lock the reservation row.
             *
             * This ensures only one cancellation/operation can
             * modify this reservation at a time.
             */
            Reservation reservation =
                    reservationRepository
                            .findById(reservationId)
                            .orElseThrow(() ->
                                    new BookingConflictException(
                                            "RESERVATION_NOT_FOUND",
                                            "Reservation not found"
                                    )
                            );

            /*
             * Step 2:
             * Verify ownership.
             *
             * The user identity comes from the Authorization token,
             * not from the request body.
             */
            if (!reservation.getUserId().equals(userId)) {

                throw new BookingConflictException(
                        "RESERVATION_NOT_OWNER",
                        "Only the reservation owner can cancel it"
                );
            }

            /*
             * Cancellation is only allowed for a confirmed reservation.
             */
            if (!ReservationStatus.CONFIRMED.name()
                    .equals(reservation.getStatus())) {

                throw new BookingConflictException(
                        "RESERVATION_NOT_ACTIVE",
                        "Reservation is already cancelled"
                );
            }

            UUID showId =
                    reservation.getShowId();

            /*
             * Step 3:
             * Lock the user's reservation-limit row.
             *
             * This uses the same lock ordering as reservation creation:
             *
             *     user limit -> seats
             *
             * This is important for avoiding lock-order deadlocks.
             */
            showUserLimitRepository.insertIfAbsent(
                    showId,
                    userId
            );

            ShowUserLimit userLimit =
                    showUserLimitRepository
                            .findByShowIdAndUserId(
                                    showId,
                                    userId
                            )
                            .orElseThrow(() ->
                                    new IllegalStateException(
                                            "User reservation limit row not found"
                                    )
                            );

            /*
             * Step 4:
             * Find all seats belonging to this reservation.
             */
            List<ReservationSeat> reservationSeats =
                    reservationSeatRepository
                            .findByIdReservationId(
                                    reservationId
                            );

            if (reservationSeats.isEmpty()) {

                throw new BookingConflictException(
                        "RESERVATION_SEATS_NOT_FOUND",
                        "No seats found for reservation"
                );
            }

            List<UUID> seatIds =
                    reservationSeats.stream()
                            .map(rs ->
                                    rs.getId().getSeatId()
                            )
                            .toList();

            /*
             * Step 5:
             * Lock all affected seats in deterministic order.
             */
            List<Seat> seats =
                    seatRepository
                            .findByIdInOrderBySeatNumber(
                                    seatIds
                            );

            if (seats.size() != seatIds.size()) {

                throw new BookingConflictException(
                        "SEAT_NOT_FOUND",
                        "One or more reservation seats could not be found"
                );
            }

            /*
             * Verify that every seat still belongs
             * to this reservation.
             *
             * This prevents accidentally releasing a seat
             * that has already been changed by another operation.
             */
            for (Seat seat : seats) {

                if (!reservationId.equals(
                        seat.getReservationId())
                        || seat.getStatus()
                        != SeatStatus.CONFIRMED) {

                    throw new BookingConflictException(
                            "SEAT_STATE_CONFLICT",
                            "Reservation seats are no longer in the expected state"
                    );
                }
            }

            /*
             * Step 6:
             * Release the seats.
             */
            for (Seat seat : seats) {

                seat.setStatus(
                        SeatStatus.AVAILABLE
                );

                seat.setReservationId(null);

                seatRepository.save(seat);
            }

            /*
             * Step 7:
             * Mark reservation as cancelled.
             */
            reservation.setStatus(
                    ReservationStatus.CANCELLED.name()
            );

            reservationRepository.save(
                    reservation
            );

            /*
             * Step 8:
             * Decrease the user's reserved-seat count.
             */
            int cancelledSeatCount =
                    seats.size();

            int newReservedSeatCount =
                    userLimit.getReservedSeats()
                            - cancelledSeatCount;

            /*
             * Defensive protection against
             * an inconsistent counter.
             */
            if (newReservedSeatCount < 0) {

                newReservedSeatCount = 0;
            }

            userLimit.setReservedSeats(
                    newReservedSeatCount
            );

            showUserLimitRepository.save(
                    userLimit
            );

            reservationMetrics.recordCancelled();

            return buildResponse(reservation);

        } catch (BookingConflictException e) {

            reservationMetrics.recordConflict();

            throw e;

        } catch (RuntimeException e) {

            reservationMetrics.recordError();

            throw e;
        }
    }

    private List<String> normalizeSeatNumbers(
            List<String> seats
    ) {

        List<String> normalized =
                new ArrayList<>(seats);

        normalized.replaceAll(String::trim);

        Collections.sort(normalized);

        /*
         * Duplicate seats in the same request are rejected.
         */
        for (int i = 1;
             i < normalized.size();
             i++) {

            if (normalized.get(i)
                    .equals(normalized.get(i - 1))) {

                throw new BookingConflictException(
                        "DUPLICATE_SEAT",
                        "The same seat was requested more than once"
                );
            }
        }

        return normalized;
    }

    private String calculateRequestHash(
            List<String> seats
    ) {

        String normalizedRequest =
                String.join(",", seats);

        try {

            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            byte[] hash =
                    digest.digest(
                            normalizedRequest.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            StringBuilder hex =
                    new StringBuilder();

            for (byte b : hash) {

                hex.append(
                        String.format(
                                "%02x",
                                b
                        )
                );
            }

            return hex.toString();

        } catch (NoSuchAlgorithmException e) {

            throw new IllegalStateException(
                    "SHA-256 algorithm not available",
                    e
            );
        }
    }

    private ReservationResponse buildResponse(
            Reservation reservation
    ) {

        List<String> seatNumbers =
                reservationSeatRepository
                        .findByIdReservationId(
                                reservation.getId()
                        )
                        .stream()
                        .map(rs ->
                                seatRepository
                                        .findById(
                                                rs.getId()
                                                        .getSeatId()
                                        )
                                        .orElseThrow()
                                        .getSeatNumber()
                        )
                        .sorted()
                        .toList();

        ReservationResponse response =
                new ReservationResponse();

        response.setReservationId(
                reservation.getId()
        );

        response.setShowId(
                reservation.getShowId()
        );

        response.setUserId(
                reservation.getUserId()
        );

        response.setSeats(
                seatNumbers
        );

        response.setAmountPaise(
                reservation.getAmountPaise()
        );

        response.setStatus(
                reservation.getStatus()
                        .toLowerCase()
        );

        return response;
    }
}