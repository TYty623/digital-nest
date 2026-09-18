CREATE TABLE memorial_day_moments (
    id UUID PRIMARY KEY,
    memorial_id UUID NOT NULL REFERENCES memorials(id) ON DELETE CASCADE,
    moment_time VARCHAR(5) NOT NULL,
    title VARCHAR(80) NOT NULL,
    place_name VARCHAR(80),
    story VARCHAR(500),
    media_id UUID REFERENCES media_assets(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_day_moment_time CHECK (LENGTH(moment_time) = 5)
);

CREATE INDEX idx_day_moments_memorial_time
    ON memorial_day_moments(memorial_id, moment_time ASC, created_at ASC);

CREATE TABLE memorial_life_details (
    id UUID PRIMARY KEY,
    memorial_id UUID NOT NULL REFERENCES memorials(id) ON DELETE CASCADE,
    detail_key VARCHAR(40) NOT NULL,
    answer VARCHAR(300) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_life_detail_memorial_key UNIQUE (memorial_id, detail_key)
);

CREATE INDEX idx_life_details_memorial ON memorial_life_details(memorial_id, detail_key ASC);
