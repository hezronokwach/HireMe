-- Optimistic locking for bookings (BookingEntity.version, @Version): every update checks and bumps
-- the version, so a change based on a stale read fails instead of overwriting a newer one.
ALTER TABLE app_booking.bookings ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
