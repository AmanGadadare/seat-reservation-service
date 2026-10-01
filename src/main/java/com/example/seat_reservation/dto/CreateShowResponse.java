package com.example.seat_reservation.dto;

import java.util.List;
import java.util.UUID;

public class CreateShowResponse {

    private UUID id;
    private String name;
    private Long pricePaise;
    private Integer perUserLimit;
    private List<SeatResponse> seats;

    public CreateShowResponse() {
    }

    public CreateShowResponse(
            UUID id,
            String name,
            Long pricePaise,
            Integer perUserLimit,
            List<SeatResponse> seats) {

        this.id = id;
        this.name = name;
        this.pricePaise = pricePaise;
        this.perUserLimit = perUserLimit;
        this.seats = seats;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public Integer getPerUserLimit() {
        return perUserLimit;
    }

    public void setPerUserLimit(Integer perUserLimit) {
        this.perUserLimit = perUserLimit;
    }

    public List<SeatResponse> getSeats() {
        return seats;
    }

    public void setSeats(List<SeatResponse> seats) {
        this.seats = seats;
    }
}