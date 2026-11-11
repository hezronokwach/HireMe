import { bookingDays, bookingStatusLabel, ownerActions } from './booking-labels';

describe('booking labels', () => {
  it('offers the owner only what the API allows for each status', () => {
    expect(ownerActions('PENDING')).toEqual(['confirm', 'decline']);
    expect(ownerActions('CONFIRMED')).toEqual(['complete', 'cancel']);
    expect(ownerActions('ACTIVE')).toEqual(['complete', 'cancel']);
    expect(ownerActions('COMPLETED')).toEqual([]);
    expect(ownerActions('CANCELLED')).toEqual([]);
    expect(ownerActions(undefined)).toEqual([]);
  });

  it('counts both the first and last day, across month ends', () => {
    expect(bookingDays('2030-01-26', '2030-01-26')).toBe(1);
    expect(bookingDays('2030-01-30', '2030-02-02')).toBe(4);
  });

  it('labels statuses', () => {
    expect(bookingStatusLabel('PENDING')).toBe('Pending');
    expect(bookingStatusLabel('WHATEVER')).toBe('WHATEVER');
  });
});
