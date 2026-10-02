package com.example.seat_reservation.controller;

import com.example.seat_reservation.dto.ReservationResponse;
import com.example.seat_reservation.dto.ReserveRequest;
import com.example.seat_reservation.service.ReservationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(
            ReservationService reservationService
    ) {
        this.reservationService = reservationService;
    }

    @PostMapping("/shows/{showId}/reserve")
    public ResponseEntity<ReservationResponse> reserve(
            @PathVariable UUID showId,
            @Valid @RequestBody ReserveRequest request,
            HttpServletRequest httpRequest
    ) {

        String userId =
                (String) httpRequest.getAttribute("userId");

        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(401).build();
        }

        ReservationResponse response =
                reservationService.reserve(
                        showId,
                        userId,
                        request
                );

        return ResponseEntity
                .status(201)
                .body(response);
    }

    @PostMapping("/reservations/{reservationId}/cancel")
    public ResponseEntity<ReservationResponse> cancel(
            @PathVariable UUID reservationId,
            HttpServletRequest httpRequest
    ) {

        String userId =
                (String) httpRequest.getAttribute("userId");

        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(401).build();
        }

        ReservationResponse response =
                reservationService.cancel(
                        reservationId,
                        userId
                );

        return ResponseEntity.ok(response);
    }
}