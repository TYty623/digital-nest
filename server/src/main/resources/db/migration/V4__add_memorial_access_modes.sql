ALTER TABLE memorials DROP CONSTRAINT chk_memorials_visibility;
ALTER TABLE memorials
    ADD CONSTRAINT chk_memorials_visibility
    CHECK (visibility IN ('PUBLIC', 'LINK', 'PASSWORD', 'PRIVATE'));

ALTER TABLE memorials ADD COLUMN access_code_hash VARCHAR(100);
