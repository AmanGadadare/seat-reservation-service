package com.example.seat_reservation.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "reservation_seats")
public class ReservationSeat {

    @EmbeddedId
    private ReservationSeatId id;

    public ReservationSeat() {
    }

    public ReservationSeat(ReservationSeatId id) {
        this.id = id;
    }

    public ReservationSeatId getId() {
        return id;
    }

    public void setId(ReservationSeatId id) {
        this.id = id;
    }
}