CREATE TABLE memorial_sound_memories (
    id UUID PRIMARY KEY,
    memorial_id UUID NOT NULL REFERENCES memorials(id) ON DELETE CASCADE,
    media_id UUID NOT NULL UNIQUE REFERENCES media_assets(id) ON DELETE CASCADE,
    title VARCHAR(80) NOT NULL,
    story VARCHAR(500),
    position INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sound_memories_memorial ON memorial_sound_memories(memorial_id, position ASC);

CREATE TABLE memorial_interview_answers (
    id UUID PRIMARY KEY,
    memorial_id UUID NOT NULL REFERENCES memorials(id) ON DELETE CASCADE,
    prompt_key VARCHAR(40) NOT NULL,
    answer VARCHAR(1000) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_interview_memorial_prompt UNIQUE (memorial_id, prompt_key)
);

CREATE TABLE memorial_keepsakes (
    id UUID PRIMARY KEY,
    memorial_id UUID NOT NULL REFERENCES memorials(id) ON DELETE CASCADE,
    title VARCHAR(80) NOT NULL,
    story VARCHAR(800) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_keepsakes_memorial ON memorial_keepsakes(memorial_id, created_at ASC);

CREATE TABLE memorial_burial_records (
    id UUID PRIMARY KEY,
    memorial_id UUID NOT NULL UNIQUE REFERENCES memorials(id) ON DELETE CASCADE,
    disposition_type VARCHAR(32) NOT NULL,
    occurred_on DATE,
    region VARCHAR(64),
    place_name VARCHAR(80),
    remembrance_text VARCHAR(500),
    review_status VARCHAR(16) NOT NULL DEFAULT 'PRIVATE',
    compliance_attested BOOLEAN NOT NULL DEFAULT FALSE,
    review_note VARCHAR(500),
    reviewed_by UUID REFERENCES users(id) ON DELETE SET NULL,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_burial_disposition CHECK (disposition_type IN ('PROFESSIONAL_HARMLESS', 'CREMATION', 'ASHES_KEPT', 'ASHES_PLACED', 'OTHER_LAWFUL')),
    CONSTRAINT chk_burial_review_status CHECK (review_status IN ('PRIVATE', 'PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_burial_review_queue ON memorial_burial_records(review_status, updated_at DESC);
