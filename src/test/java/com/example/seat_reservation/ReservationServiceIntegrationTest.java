package com.example.seat_reservation;

import com.example.seat_reservation.dto.ReserveRequest;
import com.example.seat_reservation.entity.Seat;
import com.example.seat_reservation.entity.SeatStatus;
import com.example.seat_reservation.entity.Show;
import com.example.seat_reservation.entity.Reservation;
import com.example.seat_reservation.exception.BookingConflictException;
import com.example.seat_reservation.repository.ReservationRepository;
import com.example.seat_reservation.repository.SeatRepository;
import com.example.seat_reservation.repository.ShowRepository;
import com.example.seat_reservation.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ReservationServiceIntegrationTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ShowRepository showRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private ReservationRepository reservationRepository;


    // ---------------------------------------------------------
    // 1. Reserve available seat
    // ---------------------------------------------------------

    @Test
    @Transactional
    void shouldReserveAvailableSeat() {

        Show show = createShow(
                "reserve-available",
                List.of("A1", "A2")
        );

        ReserveRequest request =
                createReserveRequest(
                        List.of("A1"),
                        "idem-1"
                );

        var response =
                reservationService.reserve(
                        show.getId(),
                        "user-1",
                        request
                );

        assertNotNull(response);
        assertEquals(show.getId(), response.getShowId());
        assertEquals("user-1", response.getUserId());
        assertEquals(List.of("A1"), response.getSeats());
        assertEquals(25000L, response.getAmountPaise());
        assertEquals("confirmed", response.getStatus());
    }


    // ---------------------------------------------------------
    // 2. Reject already reserved seat
    // ---------------------------------------------------------

    @Test
    @Transactional
    void shouldRejectAlreadyReservedSeat() {

        Show show = createShow(
                "already-reserved",
                List.of("A1")
        );

        ReserveRequest request1 =
                createReserveRequest(
                        List.of("A1"),
                        "idem-user1"
                );

        reservationService.reserve(
                show.getId(),
                "user-1",
                request1
        );

        ReserveRequest request2 =
                createReserveRequest(
                        List.of("A1"),
                        "idem-user2"
                );

        BookingConflictException exception =
                assertThrows(
                        BookingConflictException.class,
                        () -> reservationService.reserve(
                                show.getId(),
                                "user-2",
                                request2
                        )
                );

        assertEquals(
                "SEAT_UNAVAILABLE",
                exception.getReason()
        );
    }


    // ---------------------------------------------------------
    // 3. Same idempotency key returns same reservation
    // ---------------------------------------------------------

    @Test
    @Transactional
    void shouldReturnSameReservationForSameIdempotencyKey() {

        Show show = createShow(
                "idempotency-success",
                List.of("A1", "A2")
        );

        ReserveRequest request =
                createReserveRequest(
                        List.of("A1"),
                        "same-key"
                );

        var firstResponse =
                reservationService.reserve(
                        show.getId(),
                        "user-1",
                        request
                );

        var secondResponse =
                reservationService.reserve(
                        show.getId(),
                        "user-1",
                        request
                );

        assertNotNull(firstResponse);
        assertNotNull(secondResponse);

        assertEquals(
                firstResponse.getReservationId(),
                secondResponse.getReservationId()
        );

        assertEquals(
                firstResponse.getShowId(),
                secondResponse.getShowId()
        );

        assertEquals(
                firstResponse.getUserId(),
                secondResponse.getUserId()
        );

        assertEquals(
                firstResponse.getSeats(),
                secondResponse.getSeats()
        );
    }


    // ---------------------------------------------------------
    // 4. Same idempotency key with different request rejected
    // ---------------------------------------------------------

    @Test
    @Transactional
    void shouldRejectSameIdempotencyKeyWithDifferentRequest() {

        Show show = createShow(
                "idempotency-conflict",
                List.of("A1", "A2")
        );

        ReserveRequest firstRequest =
                createReserveRequest(
                        List.of("A1"),
                        "same-key"
                );

        reservationService.reserve(
                show.getId(),
                "user-1",
                firstRequest
        );

        ReserveRequest secondRequest =
                createReserveRequest(
                        List.of("A2"),
                        "same-key"
                );

        BookingConflictException exception =
                assertThrows(
                        BookingConflictException.class,
                        () -> reservationService.reserve(
                                show.getId(),
                                "user-1",
                                secondRequest
                        )
                );

        assertEquals(
                "IDEMPOTENCY_KEY_REUSED",
                exception.getReason()
        );
    }


    // ---------------------------------------------------------
    // 5. Per-user reservation limit
    // ---------------------------------------------------------

    @Test
    @Transactional
    void shouldEnforcePerUserReservationLimit() {

        Show show = createShow(
                "user-limit",
                List.of("A1", "A2", "A3", "A4", "A5")
        );

        for (int i = 1; i <= 4; i++) {

            ReserveRequest request =
                    createReserveRequest(
                            List.of("A" + i),
                            "limit-key-" + i
                    );

            reservationService.reserve(
                    show.getId(),
                    "user-1",
                    request
            );
        }

        ReserveRequest fifthRequest =
                createReserveRequest(
                        List.of("A5"),
                        "limit-key-5"
                );

        BookingConflictException exception =
                assertThrows(
                        BookingConflictException.class,
                        () -> reservationService.reserve(
                                show.getId(),
                                "user-1",
                                fifthRequest
                        )
                );

        assertEquals(
                "USER_LIMIT_EXCEEDED",
                exception.getReason()
        );
    }


    // ---------------------------------------------------------
    // 6. Cancellation releases seat
    // ---------------------------------------------------------

    @Test
    @Transactional
    void shouldReleaseSeatAfterCancellation() {

        Show show = createShow(
                "cancel-release",
                List.of("A1")
        );

        ReserveRequest request =
                createReserveRequest(
                        List.of("A1"),
                        "cancel-key"
                );

        var reservation =
                reservationService.reserve(
                        show.getId(),
                        "user-1",
                        request
                );

        var cancelled =
                reservationService.cancel(
                        reservation.getReservationId(),
                        "user-1"
                );

        assertNotNull(cancelled);

        assertEquals(
                "cancelled",
                cancelled.getStatus()
        );

        ReserveRequest secondRequest =
                createReserveRequest(
                        List.of("A1"),
                        "rebook-key"
                );

        var secondReservation =
                reservationService.reserve(
                        show.getId(),
                        "user-2",
                        secondRequest
                );

        assertNotNull(secondReservation);

        assertEquals(
                "confirmed",
                secondReservation.getStatus()
        );

        assertEquals(
                List.of("A1"),
                secondReservation.getSeats()
        );
    }


    // ---------------------------------------------------------
    // 7. Only owner can cancel reservation
    // ---------------------------------------------------------

    @Test
    @Transactional
    void shouldAllowOnlyOwnerToCancelReservation() {

        Show show = createShow(
                "owner-cancel",
                List.of("A1")
        );

        ReserveRequest request =
                createReserveRequest(
                        List.of("A1"),
                        "owner-key"
                );

        var reservation =
                reservationService.reserve(
                        show.getId(),
                        "user-1",
                        request
                );

        BookingConflictException exception =
                assertThrows(
                        BookingConflictException.class,
                        () -> reservationService.cancel(
                                reservation.getReservationId(),
                                "user-2"
                        )
                );

        assertEquals(
                "RESERVATION_NOT_OWNER",
                exception.getReason()
        );
    }


    // ---------------------------------------------------------
    // 8. Double cancellation rejected
    // ---------------------------------------------------------

    @Test
    @Transactional
    void shouldRejectDoubleCancellation() {

        Show show = createShow(
                "double-cancel",
                List.of("A1")
        );

        ReserveRequest request =
                createReserveRequest(
                        List.of("A1"),
                        "double-cancel-key"
                );

        var reservation =
                reservationService.reserve(
                        show.getId(),
                        "user-1",
                        request
                );

        var firstCancel =
                reservationService.cancel(
                        reservation.getReservationId(),
                        "user-1"
                );

        assertEquals(
                "cancelled",
                firstCancel.getStatus()
        );

        BookingConflictException exception =
                assertThrows(
                        BookingConflictException.class,
                        () -> reservationService.cancel(
                                reservation.getReservationId(),
                                "user-1"
                        )
                );

        assertEquals(
                "RESERVATION_NOT_ACTIVE",
                exception.getReason()
        );
    }




    @Test
    @Transactional
    void shouldRejectMultiSeatRequestAtomically() {

        Show show = createShow(
                "multi-seat-atomic",
                List.of("A1", "A2", "A3")
        );

        // First user reserves A2
        ReserveRequest firstRequest =
                createReserveRequest(
                        List.of("A2"),
                        "first-key"
                );

        reservationService.reserve(
                show.getId(),
                "user-1",
                firstRequest
        );

        // Second user tries A1 + A2.
        // Since A2 is unavailable, the entire request must fail.
        ReserveRequest secondRequest =
                createReserveRequest(
                        List.of("A1", "A2"),
                        "second-key"
                );

        BookingConflictException exception =
                assertThrows(
                        BookingConflictException.class,
                        () -> reservationService.reserve(
                                show.getId(),
                                "user-2",
                                secondRequest
                        )
                );

        assertEquals(
                "SEAT_UNAVAILABLE",
                exception.getReason()
        );

        // Verify A1 was NOT reserved by user-2.
        ReserveRequest thirdRequest =
                createReserveRequest(
                        List.of("A1"),
                        "third-key"
                );

        var thirdResponse =
                reservationService.reserve(
                        show.getId(),
                        "user-3",
                        thirdRequest
                );

        assertNotNull(thirdResponse);

        assertEquals(
                List.of("A1"),
                thirdResponse.getSeats()
        );

        assertEquals(
                "confirmed",
                thirdResponse.getStatus()
        );
    }



    @Test
    void shouldAllowOnlyOneWinnerForConcurrentHotSeatReservations()
            throws Exception {

        Show show = createShow(
                "hot-seat-concurrency",
                List.of("A1")
        );

        int threadCount = 20;

        ExecutorService executor =
                Executors.newFixedThreadPool(threadCount);

        CountDownLatch ready =
                new CountDownLatch(threadCount);

        CountDownLatch start =
                new CountDownLatch(1);

        List<Future<String>> futures =
                new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {

            final int index = i;

            futures.add(
                    executor.submit(() -> {

                        ready.countDown();

                        start.await();

                        ReserveRequest request =
                                createReserveRequest(
                                        List.of("A1"),
                                        "concurrent-key-" + index
                                );

                        try {

                            reservationService.reserve(
                                    show.getId(),
                                    "user-" + index,
                                    request
                            );

                            return "SUCCESS";

                        } catch (BookingConflictException e) {

                            return "CONFLICT";

                        } catch (Exception e) {

                            e.printStackTrace();

                            return "ERROR";
                        }
                    })
            );
        }

        ready.await();

        start.countDown();

        int successCount = 0;
        int conflictCount = 0;
        int errorCount = 0;

        for (Future<String> future : futures) {

            String result = future.get();

            switch (result) {

                case "SUCCESS":
                    successCount++;
                    break;

                case "CONFLICT":
                    conflictCount++;
                    break;

                case "ERROR":
                    errorCount++;
                    break;

                default:
                    fail("Unexpected result: " + result);
            }
        }

        executor.shutdown();

        assertTrue(
                executor.awaitTermination(
                        10,
                        TimeUnit.SECONDS
                )
        );

        assertEquals(
                1,
                successCount,
                "Exactly one request should reserve the hot seat"
        );

        assertEquals(
                19,
                conflictCount,
                "All other requests should receive conflicts"
        );

        assertEquals(
                0,
                errorCount,
                "There should be zero unexpected errors"
        );
    }



    @Test
    void shouldEnforcePerUserLimitUnderConcurrency()
            throws Exception {

        Show show = createShow(
                "concurrent-user-limit",
                List.of(
                        "A1",
                        "A2",
                        "A3",
                        "A4",
                        "A5",
                        "A6",
                        "A7",
                        "A8",
                        "A9",
                        "A10"
                )
        );

        int threadCount = 10;

        ExecutorService executor =
                Executors.newFixedThreadPool(threadCount);

        CountDownLatch ready =
                new CountDownLatch(threadCount);

        CountDownLatch start =
                new CountDownLatch(1);

        List<Future<String>> futures =
                new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {

            final int index = i;

            futures.add(
                    executor.submit(() -> {

                        ready.countDown();

                        start.await();

                        String userId = "same-user";

                        ReserveRequest request =
                                createReserveRequest(
                                        List.of("A" + (index + 1)),
                                        "user-limit-concurrent-" + index
                                );

                        try {

                            reservationService.reserve(
                                    show.getId(),
                                    userId,
                                    request
                            );

                            return "SUCCESS";

                        } catch (BookingConflictException e) {

                            return "CONFLICT";

                        } catch (Exception e) {

                            e.printStackTrace();

                            return "ERROR";
                        }
                    })
            );
        }

        ready.await();

        start.countDown();

        int successCount = 0;
        int conflictCount = 0;
        int errorCount = 0;

        for (Future<String> future : futures) {

            String result = future.get();

            switch (result) {

                case "SUCCESS":
                    successCount++;
                    break;

                case "CONFLICT":
                    conflictCount++;
                    break;

                case "ERROR":
                    errorCount++;
                    break;

                default:
                    fail("Unexpected result: " + result);
            }
        }

        executor.shutdown();

        assertTrue(
                executor.awaitTermination(
                        10,
                        TimeUnit.SECONDS
                )
        );

        assertEquals(
                4,
                successCount,
                "Only 4 reservations should succeed for one user"
        );

        assertEquals(
                6,
                conflictCount,
                "The remaining reservations should be rejected"
        );

        assertEquals(
                0,
                errorCount,
                "There should be zero unexpected errors"
        );
    }


    private Show createShow(
            String name,
            List<String> seatNumbers) {

        Show show =
                new Show(
                        name,
                        25000L,
                        4
                );

        Show savedShow =
                showRepository.save(show);

        for (String seatNumber : seatNumbers) {

            Seat seat =
                    new Seat(
                            savedShow.getId(),
                            seatNumber
                    );

            seat.setStatus(
                    SeatStatus.AVAILABLE
            );

            seatRepository.save(seat);
        }

        return savedShow;
    }




    private ReserveRequest createReserveRequest(
            List<String> seats,
            String idempotencyKey) {

        ReserveRequest request =
                new ReserveRequest();

        request.setSeats(seats);

        request.setIdempotencyKey(
                idempotencyKey
        );

        return request;
    }
}