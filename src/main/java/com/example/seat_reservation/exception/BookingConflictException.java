package com.example.seat_reservation.exception;

public class BookingConflictException extends RuntimeException {

    private final String reason;

    public BookingConflictException(String reason, String message) {
        super(message);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}