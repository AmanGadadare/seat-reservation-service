package com.example.seat_reservation.controller;

import com.example.seat_reservation.dto.CreateShowRequest;
import com.example.seat_reservation.dto.CreateShowResponse;
import com.example.seat_reservation.service.ShowService;
import com.example.seat_reservation.security.UserIdentityFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/shows")
public class ShowController {

    private final ShowService showService;

    public ShowController(ShowService showService) {
        this.showService = showService;
    }

    @PostMapping
    public ResponseEntity<CreateShowResponse> createShow(
            @Valid @RequestBody CreateShowRequest request,
            HttpServletRequest httpRequest) {

        String role = (String) httpRequest.getAttribute(
                UserIdentityFilter.ROLE_ATTRIBUTE
        );

        if (!"ADMIN".equals(role)) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .build();
        }

        CreateShowResponse response =
                showService.createShow(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}