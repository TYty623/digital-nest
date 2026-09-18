CREATE TABLE memorial_habitat_spaces (
    memorial_id UUID PRIMARY KEY REFERENCES memorials(id) ON DELETE CASCADE,
    scene VARCHAR(16) NOT NULL DEFAULT 'FOREST',
    title VARCHAR(60) NOT NULL DEFAULT '留在光里的日子',
    light INTEGER NOT NULL DEFAULT 80,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_habitat_scene CHECK (scene IN ('FOREST', 'COMPANION')),
    CONSTRAINT chk_habitat_light CHECK (light BETWEEN 30 AND 100)
);

CREATE TABLE memorial_habitat_items (
    id UUID PRIMARY KEY,
    memorial_id UUID NOT NULL REFERENCES memorials(id) ON DELETE CASCADE,
    item_kind VARCHAR(16) NOT NULL,
    x_percent INTEGER NOT NULL,
    y_percent INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_habitat_item_kind CHECK (item_kind IN ('LIGHT', 'FLOWER', 'STONE')),
    CONSTRAINT chk_habitat_item_x CHECK (x_percent BETWEEN 5 AND 95),
    CONSTRAINT chk_habitat_item_y CHECK (y_percent BETWEEN 15 AND 85)
);

CREATE INDEX idx_habitat_items_memorial ON memorial_habitat_items(memorial_id, created_at ASC);

CREATE TABLE memorial_habitat_notes (
    id UUID PRIMARY KEY,
    memorial_id UUID NOT NULL REFERENCES memorials(id) ON DELETE CASCADE,
    memory_date DATE NOT NULL,
    note_text VARCHAR(180) NOT NULL,
    source_type VARCHAR(24) NOT NULL DEFAULT 'MANUAL',
    source_id UUID,
    source_label VARCHAR(120),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_habitat_note_source CHECK (source_type IN ('MANUAL', 'DAY_MOMENT', 'LIFE_DETAIL', 'KEEPSAKE', 'ARCHIVE_ENTRY'))
);

CREATE INDEX idx_habitat_notes_memorial ON memorial_habitat_notes(memorial_id, memory_date DESC, created_at DESC);
