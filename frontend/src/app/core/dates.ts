/**
 * The API exchanges calendar days as ISO strings (`2030-01-26`) with no time zone. These helpers
 * convert between those strings and local `Date`s without going through UTC, which would shift
 * the day for anyone east or west of Greenwich.
 */

const ISO_DAY = /^(\d{4})-(\d{2})-(\d{2})$/;

export function toIsoDay(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/** The local date for an ISO day, or null when the text isn't a real calendar day. */
export function fromIsoDay(value: string | null | undefined): Date | null {
  const match = value ? ISO_DAY.exec(value) : null;
  if (!match) {
    return null;
  }
  const [y, m, d] = [Number(match[1]), Number(match[2]) - 1, Number(match[3])];
  const date = new Date(y, m, d);
  return date.getFullYear() === y && date.getMonth() === m && date.getDate() === d ? date : null;
}

export function addDays(date: Date, days: number): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + days);
}

export function startOfToday(): Date {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), now.getDate());
}

const DAY_FORMAT = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short' });
const DAY_YEAR_FORMAT = new Intl.DateTimeFormat('en-GB', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
});

/** "21 Nov", or "21 Nov 2027" when the year isn't `currentYear`. */
export function formatDay(iso: string, currentYear = new Date().getFullYear()): string {
  const date = fromIsoDay(iso);
  if (!date) {
    return iso;
  }
  return (date.getFullYear() === currentYear ? DAY_FORMAT : DAY_YEAR_FORMAT).format(date);
}

/** "21 Nov", "21 – 23 Nov" style ranges; both days are included. */
export function formatRange(startIso: string, endIso: string): string {
  return startIso === endIso
    ? formatDay(startIso)
    : `${formatDay(startIso)} – ${formatDay(endIso)}`;
}
