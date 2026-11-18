import { useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api, errorMessage, NetworkError } from '../api/client';
import { AnomalyBadge, Amount } from '../components/AnomalyBadge';
import { useToast } from '../components/Toasts';
import { Button, Card, CHART_ANIM, CountUp, ErrorBox, useStagger, inputClass, PageHeader, SkeletonBlock, SkeletonRows } from '../components/ui';
import { inr, inrShort, longDate, monthLabel, monthShort, MONTH_RE, recentMonths } from '../lib/format';
import { useAsync } from '../lib/useAsync';
import { categoryName } from '../lib/categorize';
import { colorFor } from '../lib/colors';
import type { Dashboard, MomRow } from '../types';

export function DashboardPage() {
  const [params, setParams] = useSearchParams();
  const raw = params.get('month') ?? '';
  const month = MONTH_RE.test(raw) ? raw : '';
  const dash = useAsync(() => api.dashboard(month || undefined), [month]);
  const d = dash.data;

  const firstRun = d !== null && d.totals.count === 0 && d.monthlyTotals.length === 0;

  return (
    <>
      <PageHeader title="Dashboard" hero="dashboard">
        {!firstRun && (
          <div className="w-full sm:w-48">
            <label htmlFor="d-month" className="sr-only">
              Period
            </label>
            <select
              id="d-month"
              className={inputClass}
              value={month}
              onChange={(e) => {
                const next = new URLSearchParams(params);
                if (e.target.value) next.set('month', e.target.value);
                else next.delete('month');
                setParams(next, { replace: true });
              }}
            >
              <option value="">All time</option>
              {recentMonths(12).map((m) => (
                <option key={m} value={m}>
                  {monthLabel(m)}
                </option>
              ))}
            </select>
          </div>
        )}
      </PageHeader>

      {dash.error ? (
        <ErrorBox message={dash.error} onRetry={dash.reload} />
      ) : !d ? (
        <DashboardSkeleton />
      ) : firstRun ? (
        <FirstRun onLoaded={dash.reload} />
      ) : (
        <div className={`space-y-6 ${dash.loading ? 'opacity-60 transition-opacity' : ''}`}>
          <StatCards d={d} month={month} />
          {d.uncategorizedVendors > 0 && (
            <Link
              to="/rules"
              className="-mt-2 flex flex-wrap items-center justify-between gap-2 rounded-lg border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm text-slate-600 hover:bg-slate-100"
            >
              <span>
                <strong className="font-semibold text-slate-800">{d.uncategorizedVendors}</strong> vendor
                {d.uncategorizedVendors === 1 ? ' is' : 's are'} in Other without a rule
              </span>
              <span className="font-medium text-indigo-600">Sort them →</span>
            </Link>
          )}
          <div className="grid gap-6 lg:grid-cols-5">
            <div className="min-w-0 lg:col-span-2"><MonthOverMonth d={d} allTime={!month} /></div>
            <div className="min-w-0 lg:col-span-3"><MonthlyChart d={d} /></div>
          </div>
          <div className="grid gap-6 lg:grid-cols-5">
            <div className="min-w-0 lg:col-span-2"><TopVendors d={d} month={month} /></div>
            <div className="min-w-0 lg:col-span-3"><AnomalyList d={d} month={month} /></div>
          </div>
          <MonthlyTable d={d} />
        </div>
      )}
    </>
  );
}

function DashboardSkeleton() {
  return (
    <div className="space-y-6" role="status" aria-label="Loading">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        {[0, 1, 2].map((i) => (
          <SkeletonBlock key={i} className="h-24" />
        ))}
      </div>
      <div className="grid gap-6 lg:grid-cols-2">
        <SkeletonBlock className="h-72" />
        <SkeletonBlock className="h-72" />
      </div>
      <SkeletonRows rows={4} />
    </div>
  );
}

function FirstRun({ onLoaded }: { onLoaded: () => void }) {
  const toast = useToast();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);

  async function loadSample() {
    setLoading(true);
    try {
      const r = await api.loadSampleData();
      toast.success(`${r.saved} rows imported`);
      if (r.anomaliesFound > 0) toast.anomaly(`⚠ ${r.anomaliesFound} high expenses found in the sample data`);
      onLoaded();
    } catch (e) {
      if (!(e instanceof NetworkError)) toast.error(errorMessage(e));
    } finally {
      setLoading(false);
    }
  }

  return (
    <Card className="mx-auto max-w-xl py-10 text-center">
      <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-indigo-50 text-2xl text-indigo-600">₹</div>
      <h2 className="text-lg font-semibold text-slate-900">No expenses yet</h2>
      <p className="mt-1 text-sm text-slate-500">Add your first expense, upload a CSV, or try the app with sample data.</p>
      <div className="mt-6 flex flex-col justify-center gap-2 sm:flex-row">
        <Button onClick={() => navigate('/expenses')}>Add expense</Button>
        <Button variant="secondary" onClick={() => navigate('/upload')}>
          Upload CSV
        </Button>
        <Button variant="secondary" busy={loading} onClick={loadSample}>
          Load sample data
        </Button>
      </div>
    </Card>
  );
}

function StatCards({ d, month }: { d: Dashboard; month: string }) {
  const period = month ? monthLabel(month) : 'All time';
  const anomalyLink = `/expenses?anomaliesOnly=true${month ? `&month=${month}` : ''}`;
  const anomalyStyle = useStagger();
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
      <Card>
        <p className="text-sm text-slate-500">Total spend · {period}</p>
        <p className="mt-1 text-2xl font-semibold tabular-nums text-slate-900">
          <CountUp value={d.totals.spend} format={inr} />
        </p>
      </Card>
      <Card>
        <p className="text-sm text-slate-500">Expenses · {period}</p>
        <p className="mt-1 text-2xl font-semibold tabular-nums text-slate-900">
          <CountUp value={d.totals.count} />
        </p>
      </Card>
      <Link
        to={anomalyLink}
        data-testid="anomaly-card"
        style={anomalyStyle}
        className="anim-fade-up block rounded-xl border border-red-200 bg-red-50 p-4 shadow-sm transition-colors hover:bg-red-100 sm:p-5"
      >
        <p className="text-sm font-medium text-red-700">⚠ High expenses · {period}</p>
        <p className="mt-1 flex items-baseline justify-between text-2xl font-semibold tabular-nums text-red-700">
          <span>
            <CountUp value={d.anomalies.count} />
          </span>
          <span className="text-sm font-medium">View →</span>
        </p>
      </Link>
    </div>
  );
}

interface ChartRow {
  month: string;
  [category: string]: number | string;
}

function useMonthly(d: Dashboard) {
  return useMemo(() => {
    const cats = new Map<number, string>();
    d.monthlyTotals.forEach((t) => cats.set(t.categoryId, t.category));
    const categories = [...cats.entries()].sort((a, b) => a[0] - b[0]).map(([id, name]) => ({ id, name }));
    const rows: ChartRow[] = d.months.map((m) => {
      const row: ChartRow = { month: m };
      categories.forEach((c) => (row[c.name] = 0));
      return row;
    });
    const byMonth = new Map(rows.map((r) => [r.month, r]));
    d.monthlyTotals.forEach((t) => {
      const row = byMonth.get(t.month);
      if (row) row[t.category] = t.total;
    });
    const totalOf = (r: ChartRow) => categories.reduce((s, c) => s + Number(r[c.name] ?? 0), 0);
    return { categories, rows, totalOf };
  }, [d]);
}

function MonthlyChart({ d }: { d: Dashboard }) {
  const { categories, rows } = useMonthly(d);
  return (
    <Card className="h-full min-w-0">
      <h2 className="text-base font-semibold text-slate-900">Monthly spend by category</h2>
      <p className="mb-3 text-xs text-slate-500">Last 6 months (not affected by the period filter)</p>
      {categories.length === 0 ? (
        <p className="py-16 text-center text-sm text-slate-500">No spend in the last 6 months.</p>
      ) : (
        <div className="h-80 w-full sm:h-[26rem]">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={rows} margin={{ top: 8, right: 8, bottom: 0, left: 0 }} barCategoryGap="22%">
              <CartesianGrid vertical={false} stroke="#e1e0d9" />
              <XAxis dataKey="month" tickFormatter={monthShort} tick={{ fontSize: 13, fill: '#64748b' }} axisLine={{ stroke: '#c3c2b7' }} tickLine={false} />
              <YAxis tickFormatter={inrShort} tick={{ fontSize: 12, fill: '#64748b' }} axisLine={false} tickLine={false} width={60} />
              <Tooltip
                cursor={{ fill: 'rgba(15,23,42,0.05)' }}
                formatter={(v) => inr(Number(v))}
                labelFormatter={(l) => monthLabel(String(l))}
                contentStyle={{ fontSize: 12, borderRadius: 8 }}
              />
              <Legend
                wrapperStyle={{ fontSize: 12 }}
                iconType="circle"
                iconSize={8}
                formatter={(value) => <span style={{ color: '#334155' }}>{value}</span>}
              />
              {categories.map((c) => (
                <Bar key={c.id} dataKey={c.name} stackId="m" fill={colorFor(c.id)} stroke="#ffffff" strokeWidth={2} maxBarSize={72} {...CHART_ANIM} />
              ))}
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </Card>
  );
}

function MonthlyTable({ d }: { d: Dashboard }) {
  const { categories, rows, totalOf } = useMonthly(d);
  if (categories.length === 0) return null;
  return (
    <Card className="min-w-0">
      <h2 className="mb-3 text-base font-semibold text-slate-900">Monthly totals</h2>
      <div className="overflow-x-auto rounded-lg border border-slate-200">
        <table className="w-full text-right text-sm">
          <thead className="bg-slate-50 text-xs text-slate-500">
            <tr>
              <th className="sticky left-0 bg-slate-50 px-3 py-2 text-left font-medium">Month</th>
              {categories.map((c) => (
                <th key={c.id} className="whitespace-nowrap px-3 py-2 font-medium">
                  <span className="mr-1.5 inline-block h-2 w-2 rounded-full" style={{ background: colorFor(c.id) }} aria-hidden="true" />
                  {c.name}
                </th>
              ))}
              <th className="px-3 py-2 font-semibold text-slate-700">Total</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 tabular-nums">
            {[...rows].reverse().map((r) => (
              <tr key={r.month}>
                <th scope="row" className="sticky left-0 whitespace-nowrap bg-white px-3 py-2 text-left font-medium text-slate-700">
                  {monthLabel(r.month)}
                </th>
                {categories.map((c) => {
                  const v = Number(r[c.name] ?? 0);
                  return (
                    <td key={c.id} className={`whitespace-nowrap px-3 py-2 ${v ? 'text-slate-700' : 'text-slate-300'}`}>
                      {v ? inr(v) : '–'}
                    </td>
                  );
                })}
                <td className="whitespace-nowrap px-3 py-2 font-semibold text-slate-900">{inr(Math.round(totalOf(r) * 100) / 100)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Card>
  );
}

function TopVendors({ d, month }: { d: Dashboard; month: string }) {
  const max = Math.max(1, ...d.topVendors.map((v) => v.total));
  return (
    <Card className="h-full min-w-0">
      <h2 className="text-base font-semibold text-slate-900">Top 5 vendors</h2>
      <p className="mb-4 text-xs text-slate-500">{month ? monthLabel(month) : 'All time'}, by total spend</p>
      {d.topVendors.length === 0 ? (
        <p className="py-16 text-center text-sm text-slate-500">No expenses in this period.</p>
      ) : (
        <ol className="space-y-4">
          {d.topVendors.map((v, i) => (
            <li key={v.vendor}>
              <div className="mb-1 flex items-baseline justify-between gap-3 text-sm">
                <span className="min-w-0 truncate font-medium text-slate-800">
                  <span className="mr-2 text-slate-400">{i + 1}</span>
                  {v.vendor}
                </span>
                <span className="whitespace-nowrap tabular-nums text-slate-900">
                  {inr(v.total)} <span className="text-xs text-slate-500">· {v.count}×</span>
                </span>
              </div>
              <div className="h-2 rounded-full bg-slate-100">
                <div className="h-2 rounded-full bg-indigo-500" style={{ width: `${(v.total / max) * 100}%` }} />
              </div>
            </li>
          ))}
        </ol>
      )}
    </Card>
  );
}

function AnomalyList({ d, month }: { d: Dashboard; month: string }) {
  const items = d.anomalies.items;
  return (
    <Card className="min-w-0">
      <div className="mb-3 flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-base font-semibold text-slate-900">High expenses</h2>
        {items.length > 0 && (
          <Link to={`/expenses?anomaliesOnly=true${month ? `&month=${month}` : ''}`} className="text-sm text-indigo-600 hover:underline">
            Open in Expenses →
          </Link>
        )}
      </div>
      {items.length === 0 ? (
        <p className="text-sm text-slate-500">No high expenses {month ? `in ${monthLabel(month)}` : 'yet'}. An expense is high when it's over 3× its category average.</p>
      ) : (
        <ul className="divide-y divide-red-100 rounded-lg border border-red-200">
          {items.map((e) => (
            <li key={e.id} className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1 border-l-4 border-l-red-600 bg-red-50 px-3 py-2.5">
              <div className="min-w-0">
                <p className="truncate text-sm font-medium text-slate-900">{e.vendor}</p>
                <p className="text-xs text-slate-500">
                  {longDate(e.date)} · {categoryName(e.category, e.label)}
                </p>
              </div>
              <div className="flex items-center gap-2">
                <AnomalyBadge expense={e} />
                <Amount expense={e} />
              </div>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

/**
 * SPEC B: "Nov 2026: ₹12,400 · ▲ 18% vs Oct". Spending more is red, less is green, no previous
 * spend is a gray "new". Other is split by nickname.
 */
function MonthOverMonth({ d, allTime }: { d: Dashboard; allTime: boolean }) {
  const m = d.monthOverMonth;
  const title = allTime ? 'This month' : monthLabel(m.month);
  return (
    <Card className="h-full min-w-0">
      <h2 className="text-base font-semibold text-slate-900">Month over month</h2>
      <p className="mb-3 text-xs text-slate-500">
        {monthLabel(m.month)} vs {monthLabel(m.previousMonth)}
      </p>
      <div className="mb-4 flex flex-wrap items-baseline gap-x-3 gap-y-1">
        <p className="text-sm text-slate-500">{title}</p>
        <p className="text-2xl font-semibold tabular-nums text-slate-900">{inr(m.total.current)}</p>
        <Change row={m.total} vs={monthShort(m.previousMonth)} />
      </div>
      {m.rows.length === 0 ? (
        <p className="py-6 text-center text-sm text-slate-500">
          No spend in {monthLabel(m.previousMonth)} or {monthLabel(m.month)}.
        </p>
      ) : (
        <ul className="max-h-80 space-y-2 overflow-y-auto pr-1">
          {m.rows.map((r) => (
            <li key={`${r.categoryId}|${r.label ?? ''}`} className="flex items-center justify-between gap-3 text-sm">
              <span className="flex min-w-0 items-center gap-2">
                <span className="inline-block h-2 w-2 shrink-0 rounded-full" style={{ background: colorFor(r.categoryId ?? 0) }} aria-hidden="true" />
                <span className="truncate text-slate-700">{categoryName(r.category ?? '', r.label)}</span>
              </span>
              <span className="flex shrink-0 items-center gap-2">
                <span className="tabular-nums text-slate-900" title={`${monthLabel(m.previousMonth)}: ${inr(r.previous)}`}>
                  {inr(r.current)}
                </span>
                <Change row={r} />
              </span>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

function Change({ row, vs }: { row: MomRow; vs?: string }) {
  const suffix = vs ? ` vs ${vs}` : '';
  const base = 'inline-flex items-center whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-semibold tabular-nums';
  if (row.changePct === null) {
    return row.current > 0 ? <span className={`${base} bg-slate-100 text-slate-600`}>new{suffix}</span> : null;
  }
  const pct = Math.abs(row.changePct).toLocaleString('en-IN', { maximumFractionDigits: 1 });
  if (row.changePct === 0) return <span className={`${base} bg-slate-100 text-slate-600`}>0%{suffix}</span>;
  return row.changePct > 0 ? (
    <span className={`${base} bg-red-50 text-red-700`} aria-label={`up ${pct}%${suffix}`}>
      ▲ {pct}%{suffix}
    </span>
  ) : (
    <span className={`${base} bg-emerald-50 text-emerald-700`} aria-label={`down ${pct}%${suffix}`}>
      ▼ {pct}%{suffix}
    </span>
  );
}
