import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api } from '../api/client';
import { Button, Card, ErrorBox, inputClass, labelClass, PageHeader, SkeletonBlock } from '../components/ui';
import { categoryName } from '../lib/categorize';
import { colorFor } from '../lib/colors';
import { inr, inrShort, monthLabel, MONTH_RE, recentMonths, todayIso } from '../lib/format';
import {
  addDays,
  classify,
  daysIn,
  elapsedDays,
  isValidIso,
  mondayOf,
  monthRange,
  overlaps,
  rangeLabel,
  recentWeeks,
  shortLabel,
  weekRange,
  type CompareMode,
  type Range,
  type RangeKind,
} from '../lib/periods';
import { useAsync } from '../lib/useAsync';
import type { Comparison } from '../types';

/** Two series, validated with the dataviz palette checker (first two categorical slots). */
const COLOR_A = '#2a78d6';
const COLOR_B = '#eb6834';
const MAX_DAYS = 366;

const MODES: { id: CompareMode; title: string; hint: string }[] = [
  { id: 'month', title: 'Month vs month', hint: 'Best for rent, bills and your overall monthly budget.' },
  { id: 'week', title: 'Week vs week', hint: 'Best for everyday spend (food, groceries, transport). Weeks run Mon–Sun.' },
  { id: 'custom', title: 'Custom dates', hint: 'Any two date ranges, e.g. a trip vs a normal fortnight.' },
];

type ValueMode = 'total' | 'perDay';

interface Selection {
  mode: CompareMode;
  a: Range;
  b: Range;
  /** Picker values for month / week mode (YYYY-MM or the Monday's date). */
  pickA: string;
  pickB: string;
}

function readSelection(params: URLSearchParams, today: string): Selection {
  const raw = params.get('mode');
  const mode: CompareMode = raw === 'week' || raw === 'custom' ? raw : 'month';
  const pa = params.get('a') ?? '';
  const pb = params.get('b') ?? '';

  if (mode === 'week') {
    const isMonday = (s: string) => isValidIso(s) && mondayOf(s) === s && s <= today;
    const thisWeek = mondayOf(today);
    const pickB = isMonday(pb) ? pb : thisWeek;
    const pickA = isMonday(pa) ? pa : addDays(pickB, -7);
    return { mode, a: weekRange(pickA), b: weekRange(pickB), pickA, pickB };
  }
  if (mode === 'custom') {
    const get = (k: string, fallback: string) => {
      const v = params.get(k) ?? '';
      return isValidIso(v) ? v : fallback;
    };
    const b = { from: get('bFrom', addDays(today, -29)), to: get('bTo', today) };
    const a = { from: get('aFrom', addDays(today, -59)), to: get('aTo', addDays(today, -30)) };
    return { mode, a, b, pickA: '', pickB: '' };
  }
  const thisMonth = today.slice(0, 7);
  const ok = (s: string) => MONTH_RE.test(s) && s <= thisMonth;
  const pickB = ok(pb) ? pb : thisMonth;
  const pickA = ok(pa) ? pa : addDays(monthRange(pickB).from, -1).slice(0, 7);
  return { mode: 'month', a: monthRange(pickA), b: monthRange(pickB), pickA, pickB };
}

/** Problem with a custom range, or null when it can be sent. */
function rangeError(r: Range, today: string): string | null {
  if (!isValidIso(r.from) || !isValidIso(r.to)) return 'Pick both dates.';
  if (r.to < r.from) return 'End date is before start date.';
  if (r.from > today) return 'This period has not started yet.';
  if (daysIn(r) > MAX_DAYS) return `Keep it to ${MAX_DAYS} days or less.`;
  return null;
}

export function ComparePage() {
  const [params, setParams] = useSearchParams();
  const today = todayIso();
  const sel = readSelection(params, today);
  const errA = sel.mode === 'custom' ? rangeError(sel.a, today) : null;
  const errB = sel.mode === 'custom' ? rangeError(sel.b, today) : null;
  const valid = !errA && !errB;

  const key = `${sel.a.from}|${sel.a.to}|${sel.b.from}|${sel.b.to}`;
  const cmp = useAsync<Comparison | null>(() => (valid ? api.compare(sel.a, sel.b) : Promise.resolve(null)), [key, valid]);

  const kind = classify(sel.a, sel.b);
  const [userValueMode, setUserValueMode] = useState<ValueMode | null>(null);
  const valueMode: ValueMode = userValueMode ?? (kind === 'uneven' ? 'perDay' : 'total');

  function update(next: Record<string, string>) {
    setParams(new URLSearchParams(next), { replace: true });
  }

  function switchMode(mode: CompareMode) {
    if (mode === sel.mode) return;
    setUserValueMode(null);
    if (mode === 'month') {
      const a = sel.a.from.slice(0, 7);
      const b = sel.b.from.slice(0, 7);
      update(a === b ? { mode } : { mode, a, b });
    } else if (mode === 'week') {
      const a = mondayOf(sel.a.from);
      const b = mondayOf(sel.b.from);
      update(a === b ? { mode } : { mode, a, b });
    } else {
      update({ mode, aFrom: sel.a.from, aTo: sel.a.to, bFrom: sel.b.from, bTo: sel.b.to });
    }
  }

  function swap() {
    if (sel.mode === 'custom') {
      update({ mode: 'custom', aFrom: sel.b.from, aTo: sel.b.to, bFrom: sel.a.from, bTo: sel.a.to });
    } else {
      update({ mode: sel.mode, a: sel.pickB, b: sel.pickA });
    }
  }

  return (
    <>
      <PageHeader title="Compare" />

      <Card className="mb-5">
        <fieldset>
          <legend className="mb-2 text-sm font-medium text-slate-700">What do you want to compare?</legend>
          <div className="grid gap-2 sm:grid-cols-3" role="radiogroup">
            {MODES.map((m) => {
              const on = m.id === sel.mode;
              return (
                <button
                  key={m.id}
                  type="button"
                  role="radio"
                  aria-checked={on}
                  onClick={() => switchMode(m.id)}
                  className={`rounded-lg border px-3 py-2.5 text-left transition-colors ${
                    on ? 'border-indigo-500 bg-indigo-50 ring-1 ring-indigo-500' : 'border-slate-200 hover:bg-slate-50'
                  }`}
                >
                  <span className={`block text-sm font-semibold ${on ? 'text-indigo-700' : 'text-slate-900'}`}>{m.title}</span>
                  <span className="mt-0.5 block text-xs text-slate-500">{m.hint}</span>
                </button>
              );
            })}
          </div>
        </fieldset>

        <div className="mt-4 grid items-end gap-3 sm:grid-cols-[1fr_auto_1fr]">
          <PeriodPicker which="a" sel={sel} today={today} error={errA} update={update} />
          <Button variant="secondary" onClick={swap} aria-label="Swap A and B" className="sm:mb-0.5">
            ⇄ Swap
          </Button>
          <PeriodPicker which="b" sel={sel} today={today} error={errB} update={update} />
        </div>

        <Guidance sel={sel} kind={kind} today={today} valueMode={valueMode} onPerDay={() => setUserValueMode('perDay')} />
      </Card>

      {!valid ? null : cmp.error ? (
        <ErrorBox message={cmp.error} onRetry={cmp.reload} />
      ) : !cmp.data ? (
        <div className="space-y-5" role="status" aria-label="Loading">
          <SkeletonBlock className="h-24 w-full" />
          <SkeletonBlock className="h-80 w-full" />
        </div>
      ) : (
        <Results data={cmp.data} today={today} valueMode={valueMode} onValueMode={setUserValueMode} loading={cmp.loading} />
      )}
    </>
  );
}

function PeriodPicker({
  which,
  sel,
  today,
  error,
  update,
}: {
  which: 'a' | 'b';
  sel: Selection;
  today: string;
  error: string | null;
  update: (next: Record<string, string>) => void;
}) {
  const title = which === 'a' ? 'Period A (baseline)' : 'Period B (compared with A)';
  const dot = which === 'a' ? COLOR_A : COLOR_B;
  const id = `cmp-${which}`;
  const Label = (
    <span className={`${labelClass} flex items-center gap-1.5`}>
      <span className="inline-block h-2.5 w-2.5 rounded-sm" style={{ background: dot }} aria-hidden="true" />
      {title}
    </span>
  );

  if (sel.mode === 'custom') {
    const r = which === 'a' ? sel.a : sel.b;
    const set = (field: 'From' | 'To', v: string) => {
      update({ mode: 'custom', aFrom: sel.a.from, aTo: sel.a.to, bFrom: sel.b.from, bTo: sel.b.to, [`${which}${field}`]: v });
    };
    return (
      <div>
        {Label}
        <div className="flex items-center gap-2">
          <input
            id={id}
            type="date"
            aria-label={`${title} start`}
            className={inputClass}
            value={r.from}
            max={today}
            aria-invalid={!!error}
            onChange={(e) => set('From', e.target.value)}
          />
          <span className="text-slate-400">–</span>
          <input
            type="date"
            aria-label={`${title} end`}
            className={inputClass}
            value={r.to}
            aria-invalid={!!error}
            onChange={(e) => set('To', e.target.value)}
          />
        </div>
        {error ? (
          <p className="mt-1 text-xs text-red-600">{error}</p>
        ) : (
          <p className="mt-1 text-xs text-slate-500">{daysIn(r)} days</p>
        )}
      </div>
    );
  }

  const value = which === 'a' ? sel.pickA : sel.pickB;
  const options =
    sel.mode === 'month'
      ? recentMonths(24).map((m) => ({ value: m, label: monthLabel(m) }))
      : recentWeeks(26, today).map((w) => ({ value: w, label: shortLabel(weekRange(w)) + (w === mondayOf(today) ? ' (this week)' : '') }));
  if (!options.some((o) => o.value === value)) {
    options.push({ value, label: sel.mode === 'month' ? monthLabel(value) : shortLabel(weekRange(value)) });
  }
  const r = which === 'a' ? sel.a : sel.b;
  return (
    <div>
      <label htmlFor={id}>{Label}</label>
      <select
        id={id}
        className={inputClass}
        value={value}
        onChange={(e) =>
          update({ mode: sel.mode, a: which === 'a' ? e.target.value : sel.pickA, b: which === 'b' ? e.target.value : sel.pickB })
        }
      >
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
      <p className="mt-1 text-xs text-slate-500">
        {rangeLabel(r)} · {daysIn(r)} days
      </p>
    </div>
  );
}

const KIND_TEXT: Record<RangeKind, string> = {
  month: 'Monthly comparison',
  week: 'Weekly comparison',
  same: 'Equal-length comparison',
  uneven: 'Different lengths',
};

/** Plain-language notes: what kind of comparison this is and anything that makes it less fair. */
function Guidance({
  sel,
  kind,
  today,
  valueMode,
  onPerDay,
}: {
  sel: Selection;
  kind: RangeKind;
  today: string;
  valueMode: ValueMode;
  onPerDay: () => void;
}) {
  if (sel.mode === 'custom' && (rangeError(sel.a, today) || rangeError(sel.b, today))) return null;

  const da = daysIn(sel.a);
  const db = daysIn(sel.b);
  const notes: { tone: 'info' | 'warn'; text: string; action?: boolean }[] = [];

  if (kind === 'month') {
    notes.push({
      tone: 'info',
      text:
        'Two full calendar months (1st to last day). Good for bills, rent and your monthly budget.' +
        (da !== db ? ` ${shortLabel(sel.a)} has ${da} days and ${shortLabel(sel.b)} has ${db}; per-day average evens that out.` : ''),
    });
  } else if (kind === 'week') {
    notes.push({ tone: 'info', text: 'Two full weeks, Monday to Sunday. Good for everyday spend like food, groceries and transport.' });
  } else if (kind === 'same') {
    let text = `Both periods are ${da} days, so totals compare fairly.`;
    if (da >= 28 && da <= 31) text += ' Tip: to compare whole calendar months, use Month vs month.';
    if (da === 7) text += ' Tip: Week vs week lines up Monday–Sunday weeks for you.';
    notes.push({ tone: 'info', text });
  } else {
    notes.push({
      tone: 'warn',
      text: `A is ${da} days and B is ${db} days, so totals are not like-for-like. Per-day average is the fairer view.`,
      action: valueMode === 'total',
    });
  }

  for (const [name, r] of [['A', sel.a], ['B', sel.b]] as const) {
    const done = elapsedDays(r, today);
    if (done < daysIn(r)) {
      notes.push({
        tone: 'warn',
        text: `${name} (${shortLabel(r)}) is still running: ${done} of ${daysIn(r)} days so far, so its total will grow. Per-day average compares the pace so far.`,
        action: valueMode === 'total',
      });
    }
  }

  if (sel.a.from === sel.b.from && sel.a.to === sel.b.to) {
    notes.push({ tone: 'warn', text: 'A and B are the same period. Pick a different one to compare.' });
  } else if (overlaps(sel.a, sel.b)) {
    notes.push({ tone: 'warn', text: 'The periods overlap: expenses on shared days count in both.' });
  }

  return (
    <div className="mt-4 space-y-2">
      <p>
        <span
          className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-semibold ${
            kind === 'uneven' ? 'bg-amber-50 text-amber-800' : 'bg-indigo-50 text-indigo-700'
          }`}
        >
          {KIND_TEXT[kind]}
        </span>
      </p>
      {notes.map((n, i) => (
        <div
          key={i}
          className={`flex flex-wrap items-center justify-between gap-2 rounded-lg px-3 py-2 text-sm ${
            n.tone === 'warn' ? 'border border-amber-200 bg-amber-50 text-amber-900' : 'bg-slate-50 text-slate-700'
          }`}
        >
          <span>
            {n.tone === 'warn' ? '⚠ ' : 'ℹ '}
            {n.text}
          </span>
          {n.action && (
            <Button size="sm" variant="secondary" onClick={onPerDay}>
              Show per day
            </Button>
          )}
        </div>
      ))}
    </div>
  );
}

interface ViewRow {
  key: string;
  name: string;
  categoryId: number;
  a: number;
  b: number;
}

function Results({
  data,
  today,
  valueMode,
  onValueMode,
  loading,
}: {
  data: Comparison;
  today: string;
  valueMode: ValueMode;
  onValueMode: (m: ValueMode) => void;
  loading: boolean;
}) {
  const ra: Range = { from: data.a.from, to: data.a.to };
  const rb: Range = { from: data.b.from, to: data.b.to };
  const labelA = shortLabel(ra);
  const labelB = shortLabel(rb);
  // Per-day divides by the days that have happened, so a running month is compared by its pace so far.
  const divA = valueMode === 'perDay' ? Math.max(1, elapsedDays(ra, today)) : 1;
  const divB = valueMode === 'perDay' ? Math.max(1, elapsedDays(rb, today)) : 1;
  const va = (n: number) => (valueMode === 'perDay' ? Math.round(n / divA) : n);
  const vb = (n: number) => (valueMode === 'perDay' ? Math.round(n / divB) : n);

  const rows: ViewRow[] = data.rows.map((r) => ({
    key: `${r.categoryId}|${r.label ?? ''}`,
    name: categoryName(r.category ?? '', r.label),
    categoryId: r.categoryId ?? 0,
    a: va(r.a),
    b: vb(r.b),
  }));
  const totalA = va(data.total.a);
  const totalB = vb(data.total.b);

  const diffs = rows.map((r) => ({ name: r.name, d: r.b - r.a }));
  const rise = diffs.reduce<{ name: string; d: number } | null>((m, x) => (x.d > 0 && (!m || x.d > m.d) ? x : m), null);
  const drop = diffs.reduce<{ name: string; d: number } | null>((m, x) => (x.d < 0 && (!m || x.d < m.d) ? x : m), null);
  const unit = valueMode === 'perDay' ? ' / day' : '';

  return (
    <div className={`space-y-5 transition-opacity ${loading ? 'opacity-60' : ''}`} aria-busy={loading}>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="inline-flex rounded-lg bg-slate-100 p-1" role="radiogroup" aria-label="Show values as">
          {(['total', 'perDay'] as const).map((m) => (
            <button
              key={m}
              type="button"
              role="radio"
              aria-checked={valueMode === m}
              onClick={() => onValueMode(m)}
              className={`rounded-md px-3 py-1.5 text-sm font-medium ${
                valueMode === m ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              {m === 'total' ? 'Total' : 'Per-day average'}
            </button>
          ))}
        </div>
        {(rise || drop) && (
          <p className="text-sm text-slate-600">
            {rise && (
              <>
                Biggest rise: <b className="text-slate-900">{rise.name}</b> <span className="text-red-700">+{inr(rise.d)}{unit}</span>
              </>
            )}
            {rise && drop && ' · '}
            {drop && (
              <>
                Biggest drop: <b className="text-slate-900">{drop.name}</b>{' '}
                <span className="text-emerald-700">−{inr(Math.abs(drop.d))}{unit}</span>
              </>
            )}
          </p>
        )}
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <StatTile color={COLOR_A} title={`A · ${labelA}`} value={totalA} unit={unit} sub={`${plural(data.a.count, "expense")} · ${data.a.days} days`} />
        <StatTile color={COLOR_B} title={`B · ${labelB}`} value={totalB} unit={unit} sub={`${plural(data.b.count, "expense")} · ${data.b.days} days`} />
        <Card>
          <p className="text-sm text-slate-500">Change A → B</p>
          <p className="mt-1 text-2xl font-semibold tabular-nums text-slate-900">
            {totalB - totalA >= 0 ? '+' : '−'}
            {inr(Math.abs(totalB - totalA))}
            <span className="text-sm font-normal text-slate-500">{unit}</span>
          </p>
          <div className="mt-1">
            <ChangePill a={totalA} b={totalB} />
          </div>
        </Card>
      </div>

      {rows.length === 0 ? (
        <Card>
          <p className="py-10 text-center text-sm text-slate-500">No expenses in either period.</p>
        </Card>
      ) : (
        <>
          <CompareChart rows={rows} labelA={labelA} labelB={labelB} unit={unit} />
          <CompareTable rows={rows} labelA={labelA} labelB={labelB} totalA={totalA} totalB={totalB} unit={unit} />
        </>
      )}
    </div>
  );
}

function StatTile({ color, title, value, unit, sub }: { color: string; title: string; value: number; unit: string; sub: string }) {
  return (
    <Card>
      <p className="flex items-center gap-1.5 text-sm text-slate-500">
        <span className="inline-block h-2.5 w-2.5 rounded-sm" style={{ background: color }} aria-hidden="true" />
        {title}
      </p>
      <p className="mt-1 text-2xl font-semibold tabular-nums text-slate-900">
        {inr(value)}
        <span className="text-sm font-normal text-slate-500">{unit}</span>
      </p>
      <p className="mt-1 text-xs text-slate-500">{sub}</p>
    </Card>
  );
}

/** Spending more is red, less is green, nothing in A is a gray "new". */
function ChangePill({ a, b }: { a: number; b: number }) {
  const base = 'inline-flex items-center whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-semibold tabular-nums';
  if (a === 0) return b > 0 ? <span className={`${base} bg-slate-100 text-slate-600`}>new</span> : <span className="text-xs text-slate-400">—</span>;
  const pct = ((b - a) / a) * 100;
  const shown = Math.abs(pct).toLocaleString('en-IN', { maximumFractionDigits: 1 });
  if (Math.abs(pct) < 0.05) return <span className={`${base} bg-slate-100 text-slate-600`}>0%</span>;
  return pct > 0 ? (
    <span className={`${base} bg-red-50 text-red-700`} aria-label={`up ${shown}%`}>
      ▲ {shown}%
    </span>
  ) : (
    <span className={`${base} bg-emerald-50 text-emerald-700`} aria-label={`down ${shown}%`}>
      ▼ {shown}%
    </span>
  );
}

/** Grouped ("double") bars: one pair per category, A and B side by side. */
function CompareChart({ rows, labelA, labelB, unit }: { rows: ViewRow[]; labelA: string; labelB: string; unit: string }) {
  const many = rows.length > 5;
  return (
    <Card className="min-w-0">
      <h2 className="text-base font-semibold text-slate-900">Spend by category</h2>
      <p className="mb-3 text-xs text-slate-500">
        {labelA} vs {labelB}
        {unit && ' · average per day'}
      </p>
      <div className="h-80 w-full sm:h-[26rem]">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={rows} margin={{ top: 8, right: 8, bottom: 0, left: 0 }} barCategoryGap="20%" barGap={2}>
            <CartesianGrid vertical={false} stroke="#e1e0d9" />
            <XAxis
              dataKey="name"
              interval={0}
              tick={{ fontSize: 12, fill: '#64748b' }}
              angle={many ? -40 : 0}
              textAnchor={many ? 'end' : 'middle'}
              height={many ? 84 : 30}
              axisLine={{ stroke: '#c3c2b7' }}
              tickLine={false}
            />
            <YAxis tickFormatter={inrShort} tick={{ fontSize: 12, fill: '#64748b' }} axisLine={false} tickLine={false} width={60} />
            <Tooltip
              cursor={{ fill: 'rgba(15,23,42,0.05)' }}
              formatter={(v) => inr(Number(v)) + unit}
              contentStyle={{ fontSize: 12, borderRadius: 8 }}
            />
            <Legend
              verticalAlign="top"
              wrapperStyle={{ fontSize: 12, paddingBottom: 8 }}
              iconType="square"
              iconSize={10}
              formatter={(value) => <span style={{ color: '#334155' }}>{value}</span>}
            />
            <Bar dataKey="a" name={`A · ${labelA}`} fill={COLOR_A} radius={[4, 4, 0, 0]} maxBarSize={40} />
            <Bar dataKey="b" name={`B · ${labelB}`} fill={COLOR_B} radius={[4, 4, 0, 0]} maxBarSize={40} />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </Card>
  );
}

function CompareTable({
  rows,
  labelA,
  labelB,
  totalA,
  totalB,
  unit,
}: {
  rows: ViewRow[];
  labelA: string;
  labelB: string;
  totalA: number;
  totalB: number;
  unit: string;
}) {
  const diff = (a: number, b: number) => (b - a === 0 ? '—' : `${b - a > 0 ? '+' : '−'}${inr(Math.abs(b - a))}`);
  return (
    <Card className="min-w-0">
      <h2 className="mb-3 text-base font-semibold text-slate-900">By category{unit && ' (per day)'}</h2>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[32rem] text-sm">
          <thead>
            <tr className="border-b border-slate-200 text-left text-xs uppercase tracking-wide text-slate-500">
              <th className="py-2 pr-3 font-medium">Category</th>
              <th className="py-2 pr-3 text-right font-medium">A · {labelA}</th>
              <th className="py-2 pr-3 text-right font-medium">B · {labelB}</th>
              <th className="py-2 pr-3 text-right font-medium">Change</th>
              <th className="py-2 text-right font-medium">%</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.key} className="border-b border-slate-100">
                <td className="py-2 pr-3">
                  <span className="flex items-center gap-2">
                    <span className="inline-block h-2 w-2 shrink-0 rounded-full" style={{ background: colorFor(r.categoryId) }} aria-hidden="true" />
                    <span className="text-slate-700">{r.name}</span>
                  </span>
                </td>
                <td className="py-2 pr-3 text-right tabular-nums text-slate-900">{inr(r.a)}</td>
                <td className="py-2 pr-3 text-right tabular-nums text-slate-900">{inr(r.b)}</td>
                <td className="py-2 pr-3 text-right tabular-nums text-slate-700">{diff(r.a, r.b)}</td>
                <td className="py-2 text-right">
                  <ChangePill a={r.a} b={r.b} />
                </td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr className="font-semibold">
              <td className="py-2 pr-3 text-slate-900">Total</td>
              <td className="py-2 pr-3 text-right tabular-nums text-slate-900">{inr(totalA)}</td>
              <td className="py-2 pr-3 text-right tabular-nums text-slate-900">{inr(totalB)}</td>
              <td className="py-2 pr-3 text-right tabular-nums text-slate-900">{diff(totalA, totalB)}</td>
              <td className="py-2 text-right">
                <ChangePill a={totalA} b={totalB} />
              </td>
            </tr>
          </tfoot>
        </table>
      </div>
    </Card>
  );
}

function plural(n: number, word: string): string {
  return `${n} ${word}${n === 1 ? '' : 's'}`;
}
