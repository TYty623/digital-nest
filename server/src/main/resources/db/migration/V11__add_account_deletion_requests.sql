CREATE TABLE account_deletion_requests (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    scheduled_for TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_account_deletion_requests_scheduled_for
    ON account_deletion_requests(scheduled_for);
