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

/**
 * Whether the client who made a booking may cancel it, matching the API: PENDING and CONFIRMED
 * bookings can be cancelled by the client; an ACTIVE one only by the owner.
 */
export function clientCanCancel(status: string | null | undefined): boolean {
  return status === 'PENDING' || status === 'CONFIRMED';
}

/** What a status means for the client, in plain words. */
export const CLIENT_STATUS_HELP: Record<BookingStatus, string> = {
  PENDING: 'Waiting for the owner to confirm. You can still cancel.',
  CONFIRMED: 'The owner confirmed it. The days are yours. You can still cancel before it starts.',
  ACTIVE: 'In progress. Only the owner can cancel it now.',
  COMPLETED: 'Done. The equipment was returned.',
  CANCELLED:
    'Cancelled. If you did not cancel it, the owner declined or another booking took the days.',
};

export function clientStatusHelp(status: string | null | undefined): string {
  return CLIENT_STATUS_HELP[status as BookingStatus] ?? '';
}

/** Every day (ISO) covered by the given periods, both ends included. */
export function bookedDaySet(
  periods: readonly { startDate?: string; endDate?: string }[],
): Set<string> {
  const days = new Set<string>();
  for (const period of periods) {
    if (!period.startDate || !period.endDate) continue;
    const end = Date.UTC(
      +period.endDate.slice(0, 4),
      +period.endDate.slice(5, 7) - 1,
      +period.endDate.slice(8, 10),
    );
    for (
      let t = Date.UTC(
        +period.startDate.slice(0, 4),
        +period.startDate.slice(5, 7) - 1,
        +period.startDate.slice(8, 10),
      );
      t <= end;
      t += 86_400_000
    ) {
      days.add(new Date(t).toISOString().slice(0, 10));
    }
  }
  return days;
}
