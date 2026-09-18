-- A dedicated, optional profile keeps the pet's everyday character separate
-- from the short farewell sentence displayed in the cover area.
ALTER TABLE memorials ADD COLUMN about_ta VARCHAR(1500);
