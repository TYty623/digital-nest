CREATE TABLE memorial_archive_entries (
    id UUID PRIMARY KEY,
    memorial_id UUID NOT NULL REFERENCES memorials(id) ON DELETE CASCADE,
    entry_type VARCHAR(24) NOT NULL,
    title VARCHAR(80) NOT NULL,
    body VARCHAR(1200),
    event_date DATE,
    place_label VARCHAR(100),
    source_label VARCHAR(80),
    verification_status VARCHAR(16) NOT NULL DEFAULT 'CONFIRMED',
    visibility VARCHAR(16) NOT NULL DEFAULT 'PRIVATE',
    media_id UUID REFERENCES media_assets(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_archive_entry_type CHECK (entry_type IN ('PLACE', 'RELATION', 'OBJECT', 'MOMENT', 'MEDIA')),
    CONSTRAINT chk_archive_entry_verification CHECK (verification_status IN ('CONFIRMED', 'PENDING')),
    CONSTRAINT chk_archive_entry_visibility CHECK (visibility IN ('PRIVATE', 'FAMILY'))
);

CREATE INDEX idx_archive_entries_memorial ON memorial_archive_entries(memorial_id, event_date ASC, created_at ASC);

CREATE TABLE memorial_anniversaries (
    id UUID PRIMARY KEY,
    memorial_id UUID NOT NULL REFERENCES memorials(id) ON DELETE CASCADE,
    anniversary_type VARCHAR(24) NOT NULL,
    title VARCHAR(60) NOT NULL,
    event_date DATE NOT NULL,
    repeat_rule VARCHAR(16) NOT NULL DEFAULT 'ANNUAL',
    reminder_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_anniversary_type CHECK (anniversary_type IN ('BIRTHDAY', 'MEETING', 'ADOPTION', 'FAREWELL', 'SEASON', 'CUSTOM')),
    CONSTRAINT chk_anniversary_repeat CHECK (repeat_rule IN ('ANNUAL', 'ONCE', 'OFF'))
);

CREATE INDEX idx_anniversaries_memorial ON memorial_anniversaries(memorial_id, event_date ASC);

CREATE TABLE memorial_time_capsules (
    id UUID PRIMARY KEY,
    memorial_id UUID NOT NULL REFERENCES memorials(id) ON DELETE CASCADE,
    title VARCHAR(80) NOT NULL,
    body VARCHAR(2000) NOT NULL,
    media_id UUID REFERENCES media_assets(id) ON DELETE SET NULL,
    unlock_on DATE NOT NULL,
    visibility VARCHAR(16) NOT NULL DEFAULT 'PRIVATE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    opened_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_capsule_visibility CHECK (visibility IN ('PRIVATE', 'FAMILY'))
);

CREATE INDEX idx_capsules_memorial_unlock ON memorial_time_capsules(memorial_id, unlock_on ASC);

CREATE TABLE memorial_ritual_records (
    id UUID PRIMARY KEY,
    memorial_id UUID NOT NULL REFERENCES memorials(id) ON DELETE CASCADE,
    ritual_type VARCHAR(24) NOT NULL,
    ritual_action VARCHAR(24) NOT NULL,
    note VARCHAR(500),
    ambient_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_ritual_type CHECK (ritual_type IN ('FIRST_HOME', 'BIRTHDAY', 'ADOPTION', 'FAREWELL', 'SEASON', 'CUSTOM', 'FAMILY')),
    CONSTRAINT chk_ritual_action CHECK (ritual_action IN ('LIGHT', 'FLOWER', 'LETTER', 'SOUND', 'CAPSULE'))
);

CREATE INDEX idx_ritual_records_memorial_completed ON memorial_ritual_records(memorial_id, completed_at DESC);

CREATE TABLE digital_life_profiles (
    id UUID PRIMARY KEY,
    memorial_id UUID NOT NULL UNIQUE REFERENCES memorials(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    profile_consent_at TIMESTAMP WITH TIME ZONE NOT NULL,
    text_processing_consent_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consent_version VARCHAR(32) NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_digital_life_status CHECK (status IN ('ACTIVE', 'PAUSED', 'ARCHIVED'))
);

CREATE TABLE digital_life_facts (
    id UUID PRIMARY KEY,
    profile_id UUID NOT NULL REFERENCES digital_life_profiles(id) ON DELETE CASCADE,
    fact_type VARCHAR(24) NOT NULL,
    statement VARCHAR(500) NOT NULL,
    source_type VARCHAR(24) NOT NULL,
    source_label VARCHAR(120) NOT NULL,
    verification_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_digital_fact_type CHECK (fact_type IN ('TRAIT', 'HABIT', 'RELATION', 'EVENT', 'PREFERENCE')),
    CONSTRAINT chk_digital_source_type CHECK (source_type IN ('LIFE_DETAIL', 'ARCHIVE_ENTRY', 'TIMELINE', 'USER_NOTE')),
    CONSTRAINT chk_digital_fact_verification CHECK (verification_status IN ('PENDING', 'CONFIRMED', 'REJECTED'))
);

CREATE INDEX idx_digital_life_facts_profile ON digital_life_facts(profile_id, verification_status, created_at ASC);
