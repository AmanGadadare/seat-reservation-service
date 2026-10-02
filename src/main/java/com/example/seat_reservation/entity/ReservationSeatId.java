package com.example.seat_reservation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ReservationSeatId implements Serializable {

    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "seat_id", nullable = false)
    private UUID seatId;

    public ReservationSeatId() {
    }

    public ReservationSeatId(UUID reservationId, UUID seatId) {
        this.reservationId = reservationId;
        this.seatId = seatId;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public void setReservationId(UUID reservationId) {
        this.reservationId = reservationId;
    }

    public UUID getSeatId() {
        return seatId;
    }

    public void setSeatId(UUID seatId) {
        this.seatId = seatId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof ReservationSeatId that)) {
            return false;
        }

        return Objects.equals(reservationId, that.reservationId)
                && Objects.equals(seatId, that.seatId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reservationId, seatId);
    }
}