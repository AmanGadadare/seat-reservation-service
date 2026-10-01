package com.example.seat_reservation.service;

import com.example.seat_reservation.dto.CreateShowRequest;
import com.example.seat_reservation.dto.CreateShowResponse;
import com.example.seat_reservation.dto.SeatResponse;
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