-- Both dates are optional: families can record an adoption/birth date, an end
-- date, or only the portion of the companionship they know.
ALTER TABLE memorials ADD COLUMN companion_started_on DATE;
ALTER TABLE memorials ADD COLUMN companion_ended_on DATE;
