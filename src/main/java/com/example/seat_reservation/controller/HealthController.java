package com.example.seat_reservation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health/live")
    public ResponseEntity<Map<String, String>> live() {

        return ResponseEntity.ok(
                Map.of(
                        "status", "UP"
                )
        );
    }

    @GetMapping("/health/ready")
    public ResponseEntity<Map<String, String>> ready() {

        try {

            jdbcTemplate.queryForObject(
                    "SELECT 1",
                    Integer.class
            );

            return ResponseEntity.ok(
                    Map.of(
                            "status", "UP",
                            "database", "UP"
                    )
            );

        } catch (Exception e) {

            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(
                            Map.of(
                                    "status", "DOWN",
                                    "database", "DOWN"
                            )
                    );
        }
    }
}