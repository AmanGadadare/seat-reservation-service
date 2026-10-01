package com.example.seat_reservation.repository;

import com.example.seat_reservation.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {

    List<Seat> findByShowIdOrderBySeatNumber(UUID showId);
}