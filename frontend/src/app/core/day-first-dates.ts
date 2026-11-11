import { Injectable, Provider } from '@angular/core';
import {
  DateAdapter,
  MAT_DATE_LOCALE,
  NativeDateAdapter,
  provideNativeDateAdapter,
} from '@angular/material/core';
import { fromIsoDay } from './dates';

const DAY_FIRST = /^\s*(\d{1,2})[/.-](\d{1,2})[/.-](\d{4})\s*$/;

/**
 * Reads typed dates the way they are written in Kenya, day first: "15/11/2026" (also with `.` or
 * `-`), plus ISO "2026-11-15". The plain native adapter leaves parsing to `Date.parse`, which reads
 * "05/11/2026" as 11 May and can't read "15/11/2026" at all.
 */
@Injectable()
export class DayFirstDateAdapter extends NativeDateAdapter {
  override parse(value: unknown, parseFormat?: unknown): Date | null {
    if (typeof value === 'string') {
      if (!value.trim()) {
        return null;
      }
      const iso = fromIsoDay(value.trim());
      if (iso) {
        return iso;
      }
      const match = DAY_FIRST.exec(value);
      if (match) {
        const [d, m, y] = [Number(match[1]), Number(match[2]) - 1, Number(match[3])];
        const date = new Date(y, m, d);
        // Rejects 31/02/2026 and the like instead of rolling over into March.
        return date.getFullYear() === y && date.getMonth() === m && date.getDate() === d
          ? date
          : this.invalid();
      }
      return this.invalid();
    }
    return super.parse(value, parseFormat);
  }
}

/** Date pickers that show and read day/month/year. */
export function provideDayFirstDates(): Provider[] {
  return [
    ...provideNativeDateAdapter(),
    { provide: DateAdapter, useClass: DayFirstDateAdapter },
    { provide: MAT_DATE_LOCALE, useValue: 'en-GB' },
  ];
}
