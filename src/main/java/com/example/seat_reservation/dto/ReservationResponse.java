package com.example.seat_reservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public class ReservationResponse {

    @JsonProperty("reservation_id")
    private UUID reservationId;

    @JsonProperty("show_id")
    private UUID showId;

    @JsonProperty("user_id")
    private String userId;

    private List<String> seats;

    @JsonProperty("amount_paise")
    private Long amountPaise;

    private String status;

    public ReservationResponse() {
    }

    public ReservationResponse(
            UUID reservationId,
            UUID showId,
            String userId,
            List<String> seats,
            Long amountPaise,
            String status
    ) {
        this.reservationId = reservationId;
        this.showId = showId;
        this.userId = userId;
        this.seats = seats;
        this.amountPaise = amountPaise;
        this.status = status;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public void setReservationId(UUID reservationId) {
        this.reservationId = reservationId;
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

    public List<String> getSeats() {
        return seats;
    }

    public void setSeats(List<String> seats) {
        this.seats = seats;
    }

    public Long getAmountPaise() {
        return amountPaise;
    }

    public void setAmountPaise(Long amountPaise) {
        this.amountPaise = amountPaise;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}