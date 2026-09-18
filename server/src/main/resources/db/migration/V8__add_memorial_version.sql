ALTER TABLE memorials ADD COLUMN version INTEGER NOT NULL DEFAULT 0;

CREATE INDEX idx_memorials_id_version ON memorials(id, version);
