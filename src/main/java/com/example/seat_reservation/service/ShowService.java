package com.example.seat_reservation.service;

import com.example.seat_reservation.dto.CreateShowRequest;
import com.example.seat_reservation.dto.CreateShowResponse;
import com.example.seat_reservation.dto.SeatResponse;
import com.example.seat_reservation.dto.ShowResponse;
import com.example.seat_reservation.entity.Seat;
import com.example.seat_reservation.entity.SeatStatus;
import com.example.seat_reservation.entity.Show;
import com.example.seat_reservation.repository.SeatRepository;
import com.example.seat_reservation.repository.ShowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ShowService {

    private static final Logger log =
            LoggerFactory.getLogger(ShowService.class);

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;

    public ShowService(
            ShowRepository showRepository,
            SeatRepository seatRepository) {

        this.showRepository = showRepository;
        this.seatRepository = seatRepository;
    }

    @Transactional
    public CreateShowResponse createShow(CreateShowRequest request) {

        log.info(
                "Creating show name={} seatCount={} pricePaise={}",
                request.getName(),
                request.getSeats().size(),
                request.getPricePaise()
        );

        validateDuplicateSeats(request.getSeats());

        Show show = new Show(
                request.getName().trim(),
                request.getPricePaise(),
                4
        );

        Show savedShow = showRepository.save(show);

        List<Seat> seats = new ArrayList<>();

        for (String seatNumber : request.getSeats()) {

            Seat seat = new Seat(
                    savedShow.getId(),
                    seatNumber.trim()
            );

            seat.setStatus(SeatStatus.AVAILABLE);
            seats.add(seat);
        }

        List<Seat> savedSeats = seatRepository.saveAll(seats);

        List<SeatResponse> seatResponses = savedSeats.stream()
                .map(seat -> new SeatResponse(
                        seat.getSeatNumber(),
                        seat.getStatus().name()
                ))
                .toList();

        log.info(
                "Show created successfully showId={} seatCount={}",
                savedShow.getId(),
                savedSeats.size()
        );

        return new CreateShowResponse(
                savedShow.getId(),
                savedShow.getName(),
                savedShow.getPricePaise(),
                savedShow.getPerUserLimit(),
                seatResponses
        );
    }

    @Transactional(readOnly = true)
    public ShowResponse getShow(UUID showId) {

        Show show = showRepository.findById(showId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Show not found"
                        )
                );

        List<Seat> seats =
                seatRepository.findByShowIdOrderBySeatNumber(showId);

        int availableSeats = 0;
        int heldSeats = 0;
        int confirmedSeats = 0;

        List<SeatResponse> seatResponses = new ArrayList<>();

        for (Seat seat : seats) {

            String status =
                    seat.getStatus().name().toLowerCase();

            seatResponses.add(
                    new SeatResponse(
                            seat.getSeatNumber(),
                            status
                    )
            );

            if (seat.getStatus() == SeatStatus.AVAILABLE) {
                availableSeats++;
            } else if (seat.getStatus() == SeatStatus.HELD) {
                heldSeats++;
            } else if (seat.getStatus() == SeatStatus.CONFIRMED) {
                confirmedSeats++;
            }
        }

        int totalSeats = seats.size();

        /*
         * Reconciliation invariant:
         *
         * available + held + confirmed = total
         */
        if (availableSeats + heldSeats + confirmedSeats
                != totalSeats) {

            log.error(
                    "Show reconciliation invariant failed showId={} " +
                            "available={} held={} confirmed={} total={}",
                    showId,
                    availableSeats,
                    heldSeats,
                    confirmedSeats,
                    totalSeats
            );

            throw new IllegalStateException(
                    "Show seat reconciliation invariant failed"
            );
        }

        ShowResponse response = new ShowResponse();

        response.setShowId(show.getId());
        response.setName(show.getName());
        response.setPricePaise(show.getPricePaise());
        response.setSeats(seatResponses);
        response.setTotalSeats(totalSeats);
        response.setAvailableSeats(availableSeats);
        response.setHeldSeats(heldSeats);
        response.setConfirmedSeats(confirmedSeats);

        return response;
    }

    private void validateDuplicateSeats(List<String> seatNumbers) {

        Set<String> normalizedSeats = new HashSet<>();

        for (String seatNumber : seatNumbers) {

            String normalized = seatNumber.trim();

            if (!normalizedSeats.add(normalized)) {

                throw new IllegalArgumentException(
                        "Duplicate seat: " + normalized
                );
            }
        }
    }
}