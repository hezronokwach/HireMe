import { addDays, formatDay, formatRange, fromIsoDay, toIsoDay } from './dates';

describe('dates', () => {
  it('round-trips local days without shifting through UTC', () => {
    const day = new Date(2030, 0, 26, 23, 30);
    expect(toIsoDay(day)).toBe('2030-01-26');
    expect(fromIsoDay('2030-01-26')).toEqual(new Date(2030, 0, 26));
  });

  it('rejects text that is not a real calendar day', () => {
    for (const value of ['2030-02-30', '26/01/2030', '2030-1-2', '', null, undefined]) {
      expect(fromIsoDay(value)).toBeNull();
    }
  });

  it('adds days across month ends', () => {
    expect(toIsoDay(addDays(new Date(2030, 0, 30), 3))).toBe('2030-02-02');
  });

  it('formats days and ranges, adding the year only when it differs', () => {
    expect(formatDay('2030-11-21', 2030)).toBe('21 Nov');
    expect(formatDay('2031-01-02', 2030)).toBe('2 Jan 2031');
    const year = new Date().getFullYear();
    expect(formatRange(`${year}-11-21`, `${year}-11-21`)).toBe('21 Nov');
    expect(formatRange(`${year}-11-21`, `${year}-11-23`)).toBe('21 Nov – 23 Nov');
  });
});
