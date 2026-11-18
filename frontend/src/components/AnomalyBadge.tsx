import type { Expense } from '../types';
import { inr, times } from '../lib/format';

/**
 * Red "⚠ High · 3.2× avg" badge with a hover / focus tooltip (SPEC §10).
 * align="right" (default) opens the tooltip to the left, so it stays inside scrolling tables;
 * align="left" opens it above, for badges at the left edge of a phone card.
 */
export function AnomalyBadge({ expense, align = 'right' }: { expense: Expense; align?: 'left' | 'right' }) {
  const tip = `${inr(expense.amount)} is ${times(expense.timesAvg)} the ${expense.category} average (${inr(expense.categoryAvg)})`;
  return (
    <span className="group relative inline-flex">
      <span
        tabIndex={0}
        aria-label={tip}
        className="inline-flex cursor-help items-center whitespace-nowrap rounded-full bg-red-100 px-2 py-0.5 text-xs font-semibold text-red-700 ring-1 ring-red-200 focus:outline-none focus:ring-2 focus:ring-red-400"
      >
        ⚠ High · {times(expense.timesAvg)} avg
      </span>
      <span
        role="tooltip"
        className={`pointer-events-none absolute z-20 ${align === 'right' ? 'right-full top-1/2 mr-2 -translate-y-1/2' : 'bottom-full left-0 mb-2'} hidden w-56 rounded-md bg-slate-900 px-3 py-2 text-xs font-normal text-white shadow-lg group-hover:block group-focus-within:block`}
      >
        {tip}
      </span>
    </span>
  );
}

/** Amount text: bold red when the expense is a high one. */
export function Amount({ expense }: { expense: Expense }) {
  return (
    <span className={`tabular-nums ${expense.anomaly ? 'font-bold text-red-700' : 'font-medium text-slate-900'}`}>
      {inr(expense.amount)}
    </span>
  );
}
