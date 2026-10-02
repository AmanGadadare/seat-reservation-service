package com.example.seat_reservation.repository;

import com.example.seat_reservation.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findByShowIdAndUserIdAndIdempotencyKey(
            UUID showId,
            String userId,
            String idempotencyKey
    );
}