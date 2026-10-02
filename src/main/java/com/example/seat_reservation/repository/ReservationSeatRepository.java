package com.example.seat_reservation.repository;

import com.example.seat_reservation.entity.ReservationSeat;
import com.example.seat_reservation.entity.ReservationSeatId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationSeatRepository
        extends JpaRepository<ReservationSeat, ReservationSeatId> {
}