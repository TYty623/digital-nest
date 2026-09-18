ALTER TABLE memorial_timeline_entries
    ADD COLUMN media_id UUID REFERENCES media_assets(id) ON DELETE SET NULL;

CREATE INDEX idx_timeline_entries_media_id ON memorial_timeline_entries(media_id);
