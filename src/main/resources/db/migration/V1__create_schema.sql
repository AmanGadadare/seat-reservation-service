CREATE TABLE shows (
                       id UUID PRIMARY KEY,
                       name VARCHAR(255) NOT NULL,
                       price_paise BIGINT NOT NULL,
                       per_user_limit INTEGER NOT NULL DEFAULT 4,
                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                       CONSTRAINT chk_show_price
                           CHECK (price_paise >= 0),

                       CONSTRAINT chk_per_user_limit
                           CHECK (per_user_limit > 0)
);

CREATE TABLE reservations (
                              id UUID PRIMARY KEY,
                              show_id UUID NOT NULL,
                              user_id VARCHAR(255) NOT NULL,
                              amount_paise BIGINT NOT NULL,
                              status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
                              idempotency_key VARCHAR(255) NOT NULL,
                              request_hash VARCHAR(64) NOT NULL,
                              created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                              CONSTRAINT fk_reservation_show
                                  FOREIGN KEY (show_id)
                                      REFERENCES shows(id),

                              CONSTRAINT chk_reservation_amount
                                  CHECK (amount_paise >= 0),

                              CONSTRAINT chk_reservation_status
                                  CHECK (status IN ('CONFIRMED', 'CANCELLED')),

                              CONSTRAINT uq_show_idempotency
                                  UNIQUE (show_id, idempotency_key)
);

CREATE TABLE seats (
                       id UUID PRIMARY KEY,
                       show_id UUID NOT NULL,
                       seat_number VARCHAR(50) NOT NULL,
                       status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
                       reservation_id UUID NULL,
                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                       CONSTRAINT fk_seat_show
                           FOREIGN KEY (show_id)
                               REFERENCES shows(id)
                               ON DELETE CASCADE,

                       CONSTRAINT chk_seat_status
                           CHECK (status IN ('AVAILABLE', 'HELD', 'CONFIRMED')),

                       CONSTRAINT uq_show_seat
                           UNIQUE (show_id, seat_number)
);

CREATE TABLE reservation_seats (
                                   reservation_id UUID NOT NULL,
                                   seat_id UUID NOT NULL,

                                   PRIMARY KEY (reservation_id, seat_id),

                                   CONSTRAINT fk_rs_reservation
                                       FOREIGN KEY (reservation_id)
                                           REFERENCES reservations(id)
                                           ON DELETE CASCADE,

                                   CONSTRAINT fk_rs_seat
                                       FOREIGN KEY (seat_id)
                                           REFERENCES seats(id)
);

CREATE TABLE show_user_limits (
                                  show_id UUID NOT NULL,
                                  user_id VARCHAR(255) NOT NULL,
                                  reserved_seats INTEGER NOT NULL DEFAULT 0,

                                  PRIMARY KEY (show_id, user_id),

                                  CONSTRAINT fk_user_limit_show
                                      FOREIGN KEY (show_id)
                                          REFERENCES shows(id)
                                          ON DELETE CASCADE,

                                  CONSTRAINT chk_reserved_seats
                                      CHECK (reserved_seats >= 0)
);

ALTER TABLE seats
    ADD CONSTRAINT fk_seat_reservation
        FOREIGN KEY (reservation_id)
            REFERENCES reservations(id)
            ON DELETE SET NULL;

CREATE INDEX idx_seats_show_status
    ON seats(show_id, status);

CREATE INDEX idx_reservations_show_user
    ON reservations(show_id, user_id);

CREATE INDEX idx_reservations_show_status
    ON reservations(show_id, status);

CREATE INDEX idx_reservation_seats_seat
    ON reservation_seats(seat_id);