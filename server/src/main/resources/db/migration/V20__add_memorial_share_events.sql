-- Owner-initiated sharing is recorded as a minimal product event. No recipient,
-- device, address, or link destination is stored.
CREATE TABLE memorial_share_events (
    id UUID PRIMARY KEY,
    memorial_id UUID NOT NULL REFERENCES memorials(id) ON DELETE CASCADE,
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_type VARCHAR(32) NOT NULL CHECK (event_type IN ('LINK_COPIED', 'SHARE_CARD_DOWNLOADED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_memorial_share_events_created_at ON memorial_share_events(created_at DESC);
CREATE INDEX idx_memorial_share_events_memorial_created_at
    ON memorial_share_events(memorial_id, created_at DESC);
