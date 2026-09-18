CREATE TABLE media_assets (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    storage_filename VARCHAR(80) NOT NULL UNIQUE,
    content_type VARCHAR(64) NOT NULL,
    byte_size BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (byte_size > 0)
);

ALTER TABLE memorials ADD COLUMN cover_media_id UUID REFERENCES media_assets(id);

CREATE INDEX idx_media_assets_owner_id ON media_assets(owner_id);
CREATE INDEX idx_memorials_cover_media_id ON memorials(cover_media_id);
