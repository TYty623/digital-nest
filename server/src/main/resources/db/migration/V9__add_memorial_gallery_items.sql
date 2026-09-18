CREATE TABLE memorial_gallery_items (
    id UUID PRIMARY KEY,
    memorial_id UUID NOT NULL REFERENCES memorials(id) ON DELETE CASCADE,
    media_id UUID NOT NULL UNIQUE REFERENCES media_assets(id) ON DELETE CASCADE,
    caption VARCHAR(280),
    position INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_gallery_items_memorial_id ON memorial_gallery_items(memorial_id, position ASC);
