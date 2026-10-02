package com.example.seat_reservation.service;

import com.example.seat_reservation.dto.ReservationResponse;
import com.example.seat_reservation.dto.ReserveRequest;
import com.example.seat_reservation.entity.Reservation;
import com.example.seat_reservation.entity.ReservationSeat;
import com.example.seat_reservation.entity.ReservationSeatId;
import com.example.seat_reservation.entity.ReservationStatus;
import com.example.seat_reservation.entity.Seat;
import com.example.seat_reservation.entity.SeatStatus;
import com.example.seat_reservation.entity.Show;
import com.example.seat_reservation.entity.ShowUserLimit;
import com.example.seat_reservation.exception.BookingConflictException;
import com.example.seat_reservation.repository.ReservationRepository;
import com.example.seat_reservation.repository.ReservationSeatRepository;
import com.example.seat_reservation.repository.SeatRepository;
import com.example.seat_reservation.repository.ShowRepository;
import com.example.seat_reservation.repository.ShowUserLimitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ReservationService {

    private static final Logger log =
            LoggerFactory.getLogger(ReservationService.class);

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final ShowUserLimitRepository showUserLimitRepository;

    public ReservationService(
            ShowRepository showRepository,
            SeatRepository seatRepository,
            ReservationRepository reservationRepository,
            ReservationSeatRepository reservationSeatRepository,
            ShowUserLimitRepository showUserLimitRepository
    ) {
        this.showRepository = showRepository;
        this.seatRepository = seatRepository;
        this.reservationRepository = reservationRepository;
        this.reservationSeatRepository = reservationSeatRepository;
        this.showUserLimitRepository = showUserLimitRepository;
    }

    @Transactional
    public ReservationResponse reserve(
            UUID showId,
            String userId,
            ReserveRequest request
    ) {

        if (userId == null || userId.isBlank()) {
            throw new BookingConflictException(
                    "UNAUTHORIZED",
                    "Authenticated user is required"
            );
        }

        List<String> requestedSeats =
                normalizeAndValidateSeats(request.getSeats());

        String idempotencyKey =
                request.getIdempotencyKey().trim();

        String requestHash =
                calculateRequestHash(requestedSeats);

        log.info(
                "Reservation request received: showId={}, userId={}, seats={}, idempotencyKey={}",
                showId,
                userId,
                requestedSeats,
                idempotencyKey
        );

        /*
         * Fast idempotency check.
         *
         * This handles the common case where the reservation
         * already exists before we need to acquire locks.
         */
        var existingReservation =
                reservationRepository
                        .findByShowIdAndUserIdAndIdempotencyKey(
                                showId,
                                userId,
                                idempotencyKey
                        );

        if (existingReservation.isPresent()) {

            Reservation existing = existingReservation.get();

            validateIdempotencyRequest(
                    existing,
                    requestHash
            );

            return buildResponse(
                    existing,
                    requestedSeats
            );
        }

        Show show = showRepository.findById(showId)
                .orElseThrow(() ->
                        new BookingConflictException(
                                "SHOW_NOT_FOUND",
                                "Show not found: " + showId
                        )
                );

        /*
         * Make sure the user-limit row exists.
         *
         * ON CONFLICT DO NOTHING makes this safe when
         * multiple requests arrive for the same user/show
         * for the first time.
         */
        showUserLimitRepository.insertIfAbsent(
                showId,
                userId
        );

        /*
         * Lock the user/show limit row.
         *
         * This serializes reservations made by the same
         * user for the same show.
         */
        ShowUserLimit userLimit =
                showUserLimitRepository
                        .findByShowIdAndUserId(
                                showId,
                                userId
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Unable to create user reservation limit"
                                )
                        );

        /*
         * IMPORTANT:
         *
         * Another identical request could have completed
         * while this request was waiting for the user-limit
         * lock.
         *
         * Therefore we MUST check idempotency again after
         * acquiring the lock.
         */
        var existingAfterLock =
                reservationRepository
                        .findByShowIdAndUserIdAndIdempotencyKey(
                                showId,
                                userId,
                                idempotencyKey
                        );

        if (existingAfterLock.isPresent()) {

            Reservation existing =
                    existingAfterLock.get();

            validateIdempotencyRequest(
                    existing,
                    requestHash
            );

            return buildResponse(
                    existing,
                    requestedSeats
            );
        }

        /*
         * Lock all requested seats.
         *
         * The repository query sorts them by seat number,
         * which gives every request the same lock order and
         * reduces deadlock risk for multi-seat reservations.
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
         * Because the seats are locked, this check is
         * concurrency-safe.
         */
        for (Seat seat : seats) {

            if (seat.getStatus() != SeatStatus.AVAILABLE) {

                throw new BookingConflictException(
                        "SEAT_UNAVAILABLE",
                        "Seat is not available: "
                                + seat.getSeatNumber()
                );
            }
        }

        int currentReservedSeats =
                userLimit.getReservedSeats() == null
                        ? 0
                        : userLimit.getReservedSeats();

        int requestedSeatCount =
                seats.size();

        if (currentReservedSeats + requestedSeatCount
                > show.getPerUserLimit()) {

            throw new BookingConflictException(
                    "USER_LIMIT_EXCEEDED",
                    "User cannot reserve more than "
                            + show.getPerUserLimit()
                            + " seats for this show"
            );
        }

        long amountPaise =
                show.getPricePaise()
                        * (long) requestedSeatCount;

        /*
         * Create reservation.
         */
        Reservation reservation =
                new Reservation(
                        showId,
                        userId,
                        amountPaise,
                        ReservationStatus.CONFIRMED.name(),
                        idempotencyKey,
                        requestHash
                );

        Reservation savedReservation =
                reservationRepository.save(reservation);

        /*
         * Confirm every requested seat.
         */
        List<ReservationSeat> reservationSeats =
                new ArrayList<>();

        for (Seat seat : seats) {

            seat.setStatus(
                    SeatStatus.CONFIRMED
            );

            seat.setReservationId(
                    savedReservation.getId()
            );

            reservationSeats.add(
                    new ReservationSeat(
                            new ReservationSeatId(
                                    savedReservation.getId(),
                                    seat.getId()
                            )
                    )
            );
        }

        seatRepository.saveAll(seats);

        reservationSeatRepository.saveAll(
                reservationSeats
        );

        /*
         * Update the user's reserved-seat count.
         */
        userLimit.setReservedSeats(
                currentReservedSeats
                        + requestedSeatCount
        );

        showUserLimitRepository.save(
                userLimit
        );

        log.info(
                "Reservation confirmed: reservationId={}, showId={}, userId={}, seats={}, amountPaise={}",
                savedReservation.getId(),
                showId,
                userId,
                requestedSeats,
                amountPaise
        );

        return buildResponse(
                savedReservation,
                requestedSeats
        );
    }

    private void validateIdempotencyRequest(
            Reservation existing,
            String requestHash
    ) {

        if (!existing.getRequestHash().equals(requestHash)) {

            throw new BookingConflictException(
                    "IDEMPOTENCY_KEY_REUSED",
                    "Idempotency key was already used with a different request"
            );
        }
    }

    private List<String> normalizeAndValidateSeats(
            List<String> seats
    ) {

        if (seats == null || seats.isEmpty()) {

            throw new IllegalArgumentException(
                    "At least one seat is required"
            );
        }

        List<String> normalizedSeats =
                seats.stream()
                        .map(String::trim)
                        .toList();

        Set<String> uniqueSeats =
                new HashSet<>(normalizedSeats);

        if (uniqueSeats.size()
                != normalizedSeats.size()) {

            throw new BookingConflictException(
                    "DUPLICATE_SEAT",
                    "Duplicate seat requested"
            );
        }

        return normalizedSeats.stream()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private String calculateRequestHash(
            List<String> seats
    ) {

        String canonicalRequest =
                String.join(",", seats);

        try {

            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash =
                    digest.digest(
                            canonicalRequest.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            StringBuilder result =
                    new StringBuilder();

            for (byte b : hash) {

                result.append(
                        String.format(
                                "%02x",
                                b
                        )
                );
            }

            return result.toString();

        } catch (NoSuchAlgorithmException e) {

            throw new IllegalStateException(
                    "SHA-256 algorithm is not available",
                    e
            );
        }
    }

    private ReservationResponse buildResponse(
            Reservation reservation,
            List<String> seats
    ) {

        return new ReservationResponse(
                reservation.getId(),
                reservation.getShowId(),
                reservation.getUserId(),
                seats,
                reservation.getAmountPaise(),
                reservation.getStatus().toLowerCase()
        );
    }
}