package com.example.seat_reservation.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class ShowUserLimitId implements Serializable {

    private UUID showId;

    private String userId;

    public ShowUserLimitId() {
    }

    public ShowUserLimitId(UUID showId, String userId) {
        this.showId = showId;
        this.userId = userId;
    }

    public UUID getShowId() {
        return showId;
    }

    public void setShowId(UUID showId) {
        this.showId = showId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof ShowUserLimitId that)) {
            return false;
        }

        return Objects.equals(showId, that.showId)
                && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(showId, userId);
    }
}