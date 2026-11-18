const inrWhole = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });
const inrPaise = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2 });
const inrCompact = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  notation: 'compact',
  maximumFractionDigits: 1,
});

/** ₹3,000 for whole rupees, ₹937.50 otherwise (en-IN grouping: ₹1,00,000). */
export function inr(n: number): string {
  return Number.isInteger(n) ? inrWhole.format(n) : inrPaise.format(n);
}

/** Short axis labels: ₹12K, ₹1.2L. */
export function inrShort(n: number): string {
  return inrCompact.format(n);
}

/** "3.2×" (API gives 2 decimals; UI shows 1). */
export function times(n: number): string {
  return `${n.toFixed(1)}×`;
}

function parseDate(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y ?? 1970, (m ?? 1) - 1, d ?? 1);
}

/** "18 Nov 2026" */
export function longDate(iso: string): string {
  return parseDate(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
}

/** "18 Nov" */
export function shortDate(iso: string): string {
  return parseDate(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' });
}

/** "2026-11" → "Nov 2026" */
export function monthLabel(ym: string): string {
  const [y, m] = ym.split('-').map(Number);
  return new Date(y ?? 1970, (m ?? 1) - 1, 1).toLocaleDateString('en-IN', { month: 'short', year: 'numeric' });
}

/** "2026-11" → "Nov" (chart axis) */
export function monthShort(ym: string): string {
  const [y, m] = ym.split('-').map(Number);
  return new Date(y ?? 1970, (m ?? 1) - 1, 1).toLocaleDateString('en-IN', { month: 'short' });
}

function pad(n: number): string {
  return String(n).padStart(2, '0');
}

/** Today as YYYY-MM-DD in local time. */
export function todayIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** The last `n` months as YYYY-MM, newest first (for month filters). */
export function recentMonths(n = 12): string[] {
  const d = new Date();
  const out: string[] = [];
  for (let i = 0; i < n; i++) {
    const x = new Date(d.getFullYear(), d.getMonth() - i, 1);
    out.push(`${x.getFullYear()}-${pad(x.getMonth() + 1)}`);
  }
  return out;
}

export const MONTH_RE = /^\d{4}-(0[1-9]|1[0-2])$/;
