-- A visit is deduplicated per memorial, anonymous fingerprint, and calendar day.
-- The application stores only an HMAC-SHA-256 fingerprint, never the source IP address.
CREATE TABLE memorial_visit_events (
    id UUID PRIMARY KEY,
    memorial_id UUID NOT NULL REFERENCES memorials(id) ON DELETE CASCADE,
    visitor_fingerprint VARCHAR(64) NOT NULL,
    visited_on DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_memorial_visit_events_daily_visitor UNIQUE (memorial_id, visitor_fingerprint, visited_on)
);

CREATE INDEX idx_memorial_visit_events_visited_on ON memorial_visit_events(visited_on DESC);
