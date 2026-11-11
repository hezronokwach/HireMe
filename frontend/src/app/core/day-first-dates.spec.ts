import { TestBed } from '@angular/core/testing';
import { DateAdapter } from '@angular/material/core';
import { provideDayFirstDates } from './day-first-dates';

describe('DayFirstDateAdapter', () => {
  let adapter: DateAdapter<Date>;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: provideDayFirstDates() });
    adapter = TestBed.inject(DateAdapter<Date>);
  });

  it('reads day/month/year as typed in Kenya, with / . or -', () => {
    for (const text of ['15/11/2026', '15.11.2026', '15-11-2026', ' 15/11/2026 ']) {
      expect(adapter.parse(text, null)).toEqual(new Date(2026, 10, 15));
    }
    expect(adapter.parse('5/1/2027', null)).toEqual(new Date(2027, 0, 5));
  });

  it('reads 05/11 as 5 November, not 11 May', () => {
    expect(adapter.parse('05/11/2026', null)).toEqual(new Date(2026, 10, 5));
  });

  it('also reads ISO days', () => {
    expect(adapter.parse('2026-11-15', null)).toEqual(new Date(2026, 10, 15));
  });

  it('rejects impossible days and junk instead of guessing', () => {
    for (const text of ['31/02/2026', '15/13/2026', 'next friday']) {
      const parsed = adapter.parse(text, null);
      expect(parsed).not.toBeNull();
      expect(adapter.isValid(parsed!)).toBe(false);
    }
    expect(adapter.parse('', null)).toBeNull();
  });

  it('shows dates day first', () => {
    expect(
      adapter.format(new Date(2026, 10, 5), { year: 'numeric', month: '2-digit', day: '2-digit' }),
    ).toBe('05/11/2026');
  });
});
