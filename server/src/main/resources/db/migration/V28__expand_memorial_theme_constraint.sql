ALTER TABLE memorials DROP CONSTRAINT IF EXISTS chk_memorials_theme;

ALTER TABLE memorials
    ADD CONSTRAINT chk_memorials_theme
        CHECK (theme IN ('SUNNY', 'NIGHT', 'GARDEN', 'MEADOW', 'ALBUM', 'HOME'));
