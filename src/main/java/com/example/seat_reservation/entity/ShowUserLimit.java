package com.example.seat_reservation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "show_user_limits")
@IdClass(ShowUserLimitId.class)
public class ShowUserLimit {

    @Id
    @Column(name = "show_id", nullable = false)
    private UUID showId;

    @Id
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "reserved_seats", nullable = false)
    private Integer reservedSeats;

    public ShowUserLimit() {
    }

    public ShowUserLimit(UUID showId, String userId, Integer reservedSeats) {
        this.showId = showId;
        this.userId = userId;
        this.reservedSeats = reservedSeats;
    }

    public UUID getShowId() {
        return showId;
    }

    public void setShowId(UUID showId) {
        this.showId = showId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Integer getReservedSeats() {
        return reservedSeats;
    }

    public void setReservedSeats(Integer reservedSeats) {
        this.reservedSeats = reservedSeats;
    }
}