ALTER TABLE tribute_messages DROP CONSTRAINT chk_tribute_messages_status;

ALTER TABLE tribute_messages
    ADD CONSTRAINT chk_tribute_messages_status
    CHECK (status IN ('PENDING', 'APPROVED', 'HIDDEN', 'REPORTED'));
