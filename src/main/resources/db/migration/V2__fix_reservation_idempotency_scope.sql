ALTER TABLE reservations
DROP CONSTRAINT uq_show_idempotency;

ALTER TABLE reservations
    ADD CONSTRAINT uq_show_user_idempotency
        UNIQUE (show_id, user_id, idempotency_key);