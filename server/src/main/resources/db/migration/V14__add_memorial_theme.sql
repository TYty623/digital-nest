ALTER TABLE memorials ADD COLUMN theme VARCHAR(20) NOT NULL DEFAULT 'SUNNY';

ALTER TABLE memorials
    ADD CONSTRAINT chk_memorials_theme CHECK (theme IN ('SUNNY', 'NIGHT', 'GARDEN'));
