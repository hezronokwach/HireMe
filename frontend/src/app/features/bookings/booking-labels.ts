export type BookingStatus = 'PENDING' | 'CONFIRMED' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED';

export const BOOKING_STATUSES: readonly { value: BookingStatus; label: string }[] = [
  { value: 'PENDING', label: 'Pending' },
  { value: 'CONFIRMED', label: 'Confirmed' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'CANCELLED', label: 'Cancelled' },
];

export function bookingStatusLabel(value: string | null | undefined): string {
  return BOOKING_STATUSES.find((s) => s.value === value)?.label ?? value ?? '';
}

export function isBookingStatus(value: unknown): value is BookingStatus {
  return BOOKING_STATUSES.some((s) => s.value === value);
}

export type OwnerAction = 'confirm' | 'decline' | 'complete' | 'cancel';

/**
 * What the equipment owner may do with a booking, matching the API's rules: only PENDING
 * bookings can be confirmed; CONFIRMED or ACTIVE ones can be completed; anything not yet
 * finished can be cancelled (declining is cancelling a PENDING request).
 */
export function ownerActions(status: string | null | undefined): OwnerAction[] {
  switch (status) {
    case 'PENDING':
      return ['confirm', 'decline'];
    case 'CONFIRMED':
    case 'ACTIVE':
      return ['complete', 'cancel'];
    default:
      return [];
  }
}

/** The number of days a booking covers; both the first and last day count. */
export function bookingDays(startIso: string, endIso: string): number {
  const start = Date.UTC(+startIso.slice(0, 4), +startIso.slice(5, 7) - 1, +startIso.slice(8, 10));
  const end = Date.UTC(+endIso.slice(0, 4), +endIso.slice(5, 7) - 1, +endIso.slice(8, 10));
  return Math.round((end - start) / 86_400_000) + 1;
}
