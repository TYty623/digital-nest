ALTER TABLE tribute_messages ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'APPROVED';
ALTER TABLE tribute_messages ADD COLUMN report_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE tribute_messages ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE tribute_messages
    ADD CONSTRAINT chk_tribute_messages_status
    CHECK (status IN ('APPROVED', 'HIDDEN', 'REPORTED'));

CREATE INDEX idx_tribute_messages_moderation ON tribute_messages(memorial_id, status, created_at ASC);
