-- Descriptions could only be 255 characters, and nothing checked that, so a longer one (for example an
-- AI-drafted one) made saving the listing fail. Allow 1000; the request DTOs now validate the length.
ALTER TABLE app_equipment.equipment ALTER COLUMN description SET DATA TYPE VARCHAR(1000);
