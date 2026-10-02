package com.example.seat_reservation.repository;

import com.example.seat_reservation.entity.Reservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository
        extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findByShowIdAndUserIdAndIdempotencyKey(
            UUID showId,
            String userId,
            String idempotencyKey
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Reservation> findById(UUID reservationId);
}