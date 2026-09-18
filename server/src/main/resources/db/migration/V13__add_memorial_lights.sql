CREATE TABLE memorial_lights (
    id UUID PRIMARY KEY,
    memorial_id UUID NOT NULL REFERENCES memorials(id) ON DELETE CASCADE,
    visitor_fingerprint VARCHAR(64) NOT NULL,
    window_date DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_memorial_lights_visitor_day UNIQUE (memorial_id, visitor_fingerprint, window_date)
);

CREATE INDEX idx_memorial_lights_memorial_id ON memorial_lights(memorial_id);
