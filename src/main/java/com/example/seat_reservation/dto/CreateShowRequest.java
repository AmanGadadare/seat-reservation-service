package com.example.seat_reservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public class CreateShowRequest {

    @NotBlank(message = "Show name is required")
    private String name;

    @NotEmpty(message = "At least one seat is required")
    private List<@NotBlank(message = "Seat number cannot be blank") String> seats;

    @JsonProperty("price_paise")
    @NotNull(message = "price_paise is required")
    @PositiveOrZero(message = "price_paise cannot be negative")
    private Long pricePaise;

    public CreateShowRequest() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getSeats() {
        return seats;
    }

    public void setSeats(List<String> seats) {
        this.seats = seats;
    }

    public Long getPricePaise() {
        return pricePaise;
    }

    public void setPricePaise(Long pricePaise) {
        this.pricePaise = pricePaise;
    }
}