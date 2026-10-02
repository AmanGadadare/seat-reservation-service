package com.example.seat_reservation.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ReservationMetrics {

    private final Counter reservationSuccess;
    private final Counter reservationConflict;
    private final Counter reservationError;
    private final Counter reservationCancelled;

    public ReservationMetrics(MeterRegistry meterRegistry) {

        reservationSuccess = Counter.builder("reservation_success_total")
                .description("Number of successful reservations")
                .register(meterRegistry);

        reservationConflict = Counter.builder("reservation_conflict_total")
                .description("Number of reservation conflicts")
                .register(meterRegistry);

        reservationError = Counter.builder("reservation_error_total")
                .description("Number of reservation errors")
                .register(meterRegistry);

        reservationCancelled = Counter.builder("reservation_cancelled_total")
                .description("Number of cancelled reservations")
                .register(meterRegistry);
    }

    public void recordSuccess() {
        reservationSuccess.increment();
    }

    public void recordConflict() {
        reservationConflict.increment();
    }

    public void recordError() {
        reservationError.increment();
    }

    public void recordCancelled() {
        reservationCancelled.increment();
    }
}