CREATE TABLE memorial_timeline_entries (
    id UUID PRIMARY KEY,
    memorial_id UUID NOT NULL REFERENCES memorials(id) ON DELETE CASCADE,
    event_date DATE,
    date_precision VARCHAR(12) NOT NULL DEFAULT 'UNKNOWN',
    title VARCHAR(80) NOT NULL,
    body VARCHAR(1000),
    position INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (date_precision IN ('DAY', 'MONTH', 'YEAR', 'UNKNOWN'))
);

CREATE INDEX idx_timeline_entries_memorial_id ON memorial_timeline_entries(memorial_id, position ASC, event_date ASC);
