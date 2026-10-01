package com.example.seat_reservation.dto;

public class SeatResponse {

    private String seat;
    private String status;

    public SeatResponse() {
    }

    public SeatResponse(String seat, String status) {
        this.seat = seat;
        this.status = status;
    }

    public String getSeat() {
        return seat;
    }

    public void setSeat(String seat) {
        this.seat = seat;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}