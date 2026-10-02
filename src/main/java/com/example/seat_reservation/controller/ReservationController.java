package com.example.seat_reservation.controller;

import com.example.seat_reservation.dto.ReservationResponse;
import com.example.seat_reservation.dto.ReserveRequest;
import com.example.seat_reservation.security.UserIdentityFilter;
import com.example.seat_reservation.service.ReservationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/shows")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/{showId}/reserve")
    public ResponseEntity<ReservationResponse> reserve(
            @PathVariable UUID showId,
            @Valid @RequestBody ReserveRequest request,
            HttpServletRequest httpRequest
    ) {

        String userId = (String) httpRequest.getAttribute(
                UserIdentityFilter.USER_ID_ATTRIBUTE
        );

        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        ReservationResponse response =
                reservationService.reserve(
                        showId,
                        userId,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}