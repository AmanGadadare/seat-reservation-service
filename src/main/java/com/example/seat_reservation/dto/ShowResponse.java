package com.example.seat_reservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public class ShowResponse {

    @JsonProperty("show_id")
    private UUID showId;

    private String name;

    @JsonProperty("price_paise")
    private Long pricePaise;

    private List<SeatResponse> seats;

    @JsonProperty("total_seats")
    private int totalSeats;

    @JsonProperty("available_seats")
    private int availableSeats;

    @JsonProperty("held_seats")
    private int heldSeats;

    @JsonProperty("confirmed_seats")
    private int confirmedSeats;

    public UUID getShowId() {
        return showId;
    }

    public void setShowId(UUID showId) {
        this.showId = showId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getPricePaise() {
        return pricePaise;
    }

    public void setPricePaise(Long pricePaise) {
        this.pricePaise = pricePaise;
    }

    public List<SeatResponse> getSeats() {
        return seats;
    }

    public void setSeats(List<SeatResponse> seats) {
        this.seats = seats;
    }

    public int getTotalSeats() {
        return totalSeats;
    }

    public void setTotalSeats(int totalSeats) {
        this.totalSeats = totalSeats;
    }

    public int getAvailableSeats() {
        return availableSeats;
    }

    public void setAvailableSeats(int availableSeats) {
        this.availableSeats = availableSeats;
    }

    public int getHeldSeats() {
        return heldSeats;
    }

    public void setHeldSeats(int heldSeats) {
        this.heldSeats = heldSeats;
    }

    public int getConfirmedSeats() {
        return confirmedSeats;
    }

    public void setConfirmedSeats(int confirmedSeats) {
        this.confirmedSeats = confirmedSeats;
    }
}