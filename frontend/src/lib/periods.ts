/** Date-range helpers for the Compare page. All dates are local YYYY-MM-DD strings; ranges are inclusive. */

export interface Range {
  from: string;
  to: string;
}

export type CompareMode = 'month' | 'week' | 'custom';

/** What the two chosen ranges actually are, so the page can say how fair the comparison is. */
export type RangeKind = 'month' | 'week' | 'same' | 'uneven';

export const ISO_DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

const pad = (n: number) => String(n).padStart(2, '0');

function toDate(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y ?? 1970, (m ?? 1) - 1, d ?? 1);
}

function toIso(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export function isValidIso(s: string): boolean {
  return ISO_DATE_RE.test(s) && toIso(toDate(s)) === s;
}

export function addDays(iso: string, n: number): string {
  const d = toDate(iso);
  d.setDate(d.getDate() + n);
  return toIso(d);
}

/** Calendar days in a range, both ends included. */
export function daysIn(r: Range): number {
  const [a, b] = [r.from, r.to].map((s) => {
    const [y, m, d] = s.split('-').map(Number);
    return Date.UTC(y ?? 1970, (m ?? 1) - 1, d ?? 1);
  });
  return Math.round(((b ?? 0) - (a ?? 0)) / 86_400_000) + 1;
}

/** "2026-11" → 1 Nov .. 30 Nov. */
export function monthRange(ym: string): Range {
  const [y, m] = ym.split('-').map(Number);
  const first = new Date(y ?? 1970, (m ?? 1) - 1, 1);
  const last = new Date(y ?? 1970, m ?? 1, 0);
  return { from: toIso(first), to: toIso(last) };
}

/** The Monday on or before the date (weeks run Monday to Sunday). */
export function mondayOf(iso: string): string {
  const d = toDate(iso);
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7));
  return toIso(d);
}

export function weekRange(monday: string): Range {
  return { from: monday, to: addDays(monday, 6) };
}

/** Mondays of the last `n` weeks, newest (this week) first. */
export function recentWeeks(n: number, today: string): string[] {
  const start = mondayOf(today);
  return Array.from({ length: n }, (_, i) => addDays(start, -7 * i));
}

/** ISO-8601 week number of the week starting on this Monday. */
export function isoWeek(monday: string): number {
  const thursday = toDate(addDays(monday, 3));
  const jan1 = new Date(thursday.getFullYear(), 0, 1);
  return Math.floor((daysIn({ from: toIso(jan1), to: toIso(thursday) }) - 1) / 7) + 1;
}

const fmt = (iso: string, opts: Intl.DateTimeFormatOptions) => toDate(iso).toLocaleDateString('en-IN', opts);

/** "1 Oct – 15 Nov 2026", "9 – 15 Nov 2026", "30 Dec 2025 – 5 Jan 2026", "18 Nov 2026" for one day. */
export function rangeLabel(r: Range): string {
  if (r.from === r.to) return fmt(r.from, { day: 'numeric', month: 'short', year: 'numeric' });
  const [fy, fm] = r.from.split('-');
  const [ty, tm] = r.to.split('-');
  const end = fmt(r.to, { day: 'numeric', month: 'short', year: 'numeric' });
  if (fy !== ty) return `${fmt(r.from, { day: 'numeric', month: 'short', year: 'numeric' })} – ${end}`;
  if (fm !== tm) return `${fmt(r.from, { day: 'numeric', month: 'short' })} – ${end}`;
  return `${fmt(r.from, { day: 'numeric' })} – ${end}`;
}

/** Short name for legends and table headers: "Oct 2026", "W46 · 9–15 Nov", or the range. */
export function shortLabel(r: Range): string {
  if (isFullMonth(r)) return fmt(r.from, { month: 'short', year: 'numeric' });
  if (isFullWeek(r)) {
    const [, fm] = r.from.split('-');
    const [, tm] = r.to.split('-');
    const span =
      fm === tm
        ? `${fmt(r.from, { day: 'numeric' })}–${fmt(r.to, { day: 'numeric', month: 'short' })}`
        : `${fmt(r.from, { day: 'numeric', month: 'short' })}–${fmt(r.to, { day: 'numeric', month: 'short' })}`;
    return `W${isoWeek(r.from)} · ${span}`;
  }
  return rangeLabel(r);
}

export function isFullMonth(r: Range): boolean {
  return r.from.endsWith('-01') && monthRange(r.from.slice(0, 7)).to === r.to;
}

export function isFullWeek(r: Range): boolean {
  return mondayOf(r.from) === r.from && daysIn(r) === 7;
}

export function classify(a: Range, b: Range): RangeKind {
  if (isFullMonth(a) && isFullMonth(b)) return 'month';
  if (isFullWeek(a) && isFullWeek(b)) return 'week';
  return daysIn(a) === daysIn(b) ? 'same' : 'uneven';
}

export function overlaps(a: Range, b: Range): boolean {
  return a.from <= b.to && b.from <= a.to;
}

/**
 * Days of the range that have already happened (today counts). Equals daysIn() for a past range,
 * less for one that is still running, 0 for one fully in the future.
 */
export function elapsedDays(r: Range, today: string): number {
  if (today < r.from) return 0;
  if (today >= r.to) return daysIn(r);
  return daysIn({ from: r.from, to: today });
}
