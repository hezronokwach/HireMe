-- Booked dates now come from CONFIRMED/ACTIVE bookings, so equipment no longer has a HIRED status.
-- Its status only says whether the listing takes bookings: AVAILABLE or UNDER_MAINTENANCE.
UPDATE app_equipment.equipment SET status = 'AVAILABLE' WHERE status = 'HIRED';

ALTER TABLE app_equipment.equipment DROP CONSTRAINT equipment_status_check;
ALTER TABLE app_equipment.equipment
    ADD CONSTRAINT equipment_status_check CHECK (status IN ('AVAILABLE', 'UNDER_MAINTENANCE'));
