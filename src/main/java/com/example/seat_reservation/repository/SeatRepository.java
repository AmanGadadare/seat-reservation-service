package com.example.seat_reservation.repository;

import com.example.seat_reservation.entity.Seat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {

    List<Seat> findByShowIdOrderBySeatNumber(UUID showId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Seat> findByShowIdAndSeatNumberInOrderBySeatNumber(
            UUID showId,
            List<String> seatNumbers
    );
}