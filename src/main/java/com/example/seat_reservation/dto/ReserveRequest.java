package com.example.seat_reservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class ReserveRequest {

    @NotEmpty(message = "At least one seat is required")
    @Valid
    private List<@NotBlank(message = "Seat number cannot be blank") String> seats;

    @JsonProperty("idempotency_key")
    @NotBlank(message = "idempotency_key is required")
    private String idempotencyKey;

    public ReserveRequest() {
    }

    public List<String> getSeats() {
        return seats;
    }

    public void setSeats(List<String> seats) {
        this.seats = seats;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}