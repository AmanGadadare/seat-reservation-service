package com.example.seat_reservation.repository;

import com.example.seat_reservation.entity.ShowUserLimit;
import com.example.seat_reservation.entity.ShowUserLimitId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ShowUserLimitRepository
        extends JpaRepository<ShowUserLimit, ShowUserLimitId> {

    @Modifying
    @Query(value = """
            INSERT INTO show_user_limits (show_id, user_id, reserved_seats)
            VALUES (:showId, :userId, 0)
            ON CONFLICT (show_id, user_id) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(
            @Param("showId") UUID showId,
            @Param("userId") String userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ShowUserLimit> findByShowIdAndUserId(
            UUID showId,
            String userId
    );
}