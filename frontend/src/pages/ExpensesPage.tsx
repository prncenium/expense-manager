import { useEffect, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api, ApiError, errorMessage, NetworkError } from '../api/client';
import { AnomalyBadge, Amount } from '../components/AnomalyBadge';
import { useToast } from '../components/Toasts';
import {
  Button,
  Card,
  ErrorBox,
  FieldError,
  inputClass,
  labelClass,
  PageHeader,
  SkeletonRows,
} from '../components/ui';
import { categoryName, previewCategory } from '../lib/categorize';
import { inr, longDate, monthLabel, MONTH_RE, recentMonths, shortDate, times, todayIso } from '../lib/format';
import { useAsync, type AsyncState } from '../lib/useAsync';
import type { Category, Expense, ExpenseFilters, ExpensePage, Rule } from '../types';

const PAGE_SIZE = 50;

export function ExpensesPage() {
  const [params, setParams] = useSearchParams();
  const rawMonth = params.get('month') ?? '';
  const month = MONTH_RE.test(rawMonth) ? rawMonth : '';
  const rawCat = Number(params.get('categoryId'));
  const categoryId = Number.isInteger(rawCat) && rawCat > 0 ? rawCat : undefined;
  const label = categoryId ? params.get('label') ?? '' : '';
  const anomaliesOnly = params.get('anomaliesOnly') === 'true';
  const q = params.get('q') ?? '';
  const rawPage = Number(params.get('page'));
  const page = Number.isInteger(rawPage) && rawPage > 1 ? rawPage : 1; // 1-based in the URL

  const categories = useAsync(() => api.categories(), []);
  const labels = useAsync(() => api.labels(), []);
  const rules = useAsync(() => api.rules(), []);
  const filters: ExpenseFilters = { month: month || undefined, categoryId, label: label || undefined, anomaliesOnly, q };
  const list = useAsync(
    () => api.expenses(filters, page - 1, PAGE_SIZE),
    [month, categoryId, label, anomaliesOnly, q, page],
  );

  /** Changing any filter goes back to page 1. */
  function setFilters(changes: Record<string, string>) {
    // functional update: the debounced search must not undo a filter picked meanwhile
    setParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        for (const [key, value] of Object.entries(changes)) {
          if (value) next.set(key, value);
          else next.delete(key);
        }
        if (!('page' in changes)) next.delete('page');
        return next;
      },
      { replace: true },
    );
  }

  // Search box: typed text is local, pushed to the URL after 300 ms of no typing.
  const [search, setSearch] = useState(q);
  useEffect(() => setSearch(q), [q]);
  useEffect(() => {
    if (search.trim() === q.trim()) return;
    const t = window.setTimeout(() => setFilters({ q: search.trim() }), 300);
    return () => window.clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [search]);

  const otherId = categories.data?.find((c) => c.name === 'Other')?.id;
  const catValue = categoryId ? (label ? `${categoryId}|${label}` : String(categoryId)) : '';
  const filtered = Boolean(month || categoryId || anomaliesOnly || q);

  return (
    <>
      <PageHeader title="Expenses" />
      <div className="grid gap-6 lg:grid-cols-[22rem_1fr] lg:items-start">
        <AddExpenseForm rules={rules.data} onAdded={() => {
          list.reload();
          labels.reload();
        }} />

        <Card className="min-w-0">
          <div className="mb-3">
            <label htmlFor="f-q" className="sr-only">
              Search
            </label>
            <input
              id="f-q"
              type="search"
              maxLength={100}
              placeholder="Search vendor or description"
              autoComplete="off"
              className={inputClass}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
          <div className="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-3 sm:items-end">
            <div>
              <label htmlFor="f-month" className={labelClass}>
                Month
              </label>
              <select id="f-month" className={inputClass} value={month} onChange={(e) => setFilters({ month: e.target.value })}>
                <option value="">All time</option>
                {withSelected(recentMonths(12), month).map((m) => (
                  <option key={m} value={m}>
                    {monthLabel(m)}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label htmlFor="f-cat" className={labelClass}>
                Category
              </label>
              <select
                id="f-cat"
                className={inputClass}
                value={catValue}
                onChange={(e) => {
                  const [id = '', l = ''] = e.target.value.split('|');
                  setFilters({ categoryId: id, label: l });
                }}
              >
                <option value="">All categories</option>
                {(categories.data ?? []).map((c: Category) => [
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>,
                  ...(c.id === otherId
                    ? withSelected(labels.data ?? [], categoryId === otherId ? label : '').map((l) => (
                        <option key={`${c.id}|${l}`} value={`${c.id}|${l}`}>
                          {categoryName(c.name, l)}
                        </option>
                      ))
                    : []),
                ])}
              </select>
            </div>
            <label className="flex cursor-pointer items-center gap-2 rounded-md px-1 py-2 text-sm font-medium text-slate-700">
              <input
                type="checkbox"
                className="h-4 w-4 rounded border-slate-300 accent-red-600"
                checked={anomaliesOnly}
                onChange={(e) => setFilters({ anomaliesOnly: e.target.checked ? 'true' : '' })}
              />
              Only high expenses
            </label>
          </div>

          <ExpenseList
            state={list}
            filtered={filtered}
            exportUrl={api.exportUrl(filters)}
            onPage={(p) => setFilters({ page: p > 1 ? String(p) : '' })}
            onClear={() => {
              setSearch('');
              setParams(new URLSearchParams(), { replace: true });
            }}
          />
        </Card>
      </div>
    </>
  );
}

/** Keep a value from the URL selectable even if it's not in the loaded list. */
function withSelected(values: string[], selected: string): string[] {
  return selected && !values.includes(selected) ? [...values, selected] : values;
}

// ---------------------------------------------------------------------------------------------

const EMPTY = { date: todayIso(), amount: '', vendor: '', description: '' };

function AddExpenseForm({ rules, onAdded }: { rules: Rule[] | null; onAdded: () => void }) {
  const toast = useToast();
  const [form, setForm] = useState({ ...EMPTY, date: todayIso() });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [duplicateOf, setDuplicateOf] = useState<string | null>(null);

  // No hint until the rules have loaded, so we never show a wrong "Other".
  const hint = rules ? previewCategory(form.vendor, rules) : '';

  function update(field: keyof typeof EMPTY, value: string) {
    setForm((f) => ({ ...f, [field]: value }));
    if (errors[field]) setErrors((e) => ({ ...e, [field]: '' }));
  }

  async function submit(ev: FormEvent) {
    ev.preventDefault();
    const amount = form.amount.trim() === '' ? null : Number(form.amount);
    setSaving(true);
    setDuplicateOf(null);
    try {
      const saved = await api.addExpense({
        date: form.date,
        amount: amount !== null && Number.isFinite(amount) ? amount : null,
        vendor: form.vendor,
        description: form.description,
      });
      setErrors({});
      setForm({ ...EMPTY, date: form.date });
      toast.success('Expense added');
      if (saved.anomaly) {
        toast.anomaly(
          `⚠ High expense: ${inr(saved.amount)} at ${saved.vendor} is ${times(saved.timesAvg)} your ${saved.category} average.`,
        );
      }
      if (saved.possibleDuplicate) setDuplicateOf(saved.date);
      onAdded();
    } catch (e) {
      if (e instanceof ApiError && Object.keys(e.fieldErrors).length > 0) {
        setErrors(e.fieldErrors);
      } else if (!(e instanceof NetworkError)) {
        toast.error(errorMessage(e));
      }
    } finally {
      setSaving(false);
    }
  }

  const fieldProps = (name: string) => ({
    id: `x-${name}`,
    'aria-invalid': Boolean(errors[name]),
    'aria-describedby': errors[name] ? `x-${name}-err` : undefined,
  });
  const err = (name: string, label: string) => (
    <FieldError id={`x-${name}-err`} message={errors[name] ? `${label} ${errors[name]}` : undefined} />
  );

  return (
    <Card>
      <h2 className="mb-4 text-base font-semibold text-slate-900">Add expense</h2>
      <form onSubmit={submit} noValidate className="space-y-4">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-1">
          <div>
            <label htmlFor="x-date" className={labelClass}>
              Date
            </label>
            <input
              {...fieldProps('date')}
              type="date"
              max={todayIso()}
              className={inputClass}
              value={form.date}
              onChange={(e) => update('date', e.target.value)}
            />
            {err('date', 'Date')}
          </div>
          <div>
            <label htmlFor="x-amount" className={labelClass}>
              Amount (₹)
            </label>
            <input
              {...fieldProps('amount')}
              type="number"
              inputMode="decimal"
              min="0.01"
              step="0.01"
              placeholder="0.00"
              className={inputClass}
              value={form.amount}
              onChange={(e) => update('amount', e.target.value)}
            />
            {err('amount', 'Amount')}
          </div>
        </div>
        <div>
          <label htmlFor="x-vendor" className={labelClass}>
            Vendor
          </label>
          <input
            {...fieldProps('vendor')}
            type="text"
            maxLength={150}
            placeholder="e.g. Swiggy"
            autoComplete="off"
            className={inputClass}
            value={form.vendor}
            onChange={(e) => update('vendor', e.target.value)}
          />
          {err('vendor', 'Vendor')}
          <p className="mt-1 min-h-5 text-xs text-slate-500" aria-live="polite" data-testid="category-hint">
            {hint && (
              <>
                → will be categorized as <strong className="font-semibold text-slate-800">{hint}</strong>
              </>
            )}
          </p>
        </div>
        <div>
          <label htmlFor="x-description" className={labelClass}>
            Description <span className="font-normal text-slate-400">(optional)</span>
          </label>
          <input
            {...fieldProps('description')}
            type="text"
            maxLength={500}
            className={inputClass}
            value={form.description}
            onChange={(e) => update('description', e.target.value)}
          />
          {err('description', 'Description')}
        </div>

        {duplicateOf && (
          <div role="status" className="flex items-start gap-2 rounded-md border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-800">
            <span className="flex-1">Looks like a duplicate of an expense on {shortDate(duplicateOf)}. It was saved anyway; delete it below if it's a mistake.</span>
            <button type="button" className="text-amber-700 hover:text-amber-900" aria-label="Dismiss" onClick={() => setDuplicateOf(null)}>
              ✕
            </button>
          </div>
        )}

        <Button type="submit" busy={saving} className="w-full">
          {saving ? 'Saving…' : 'Add expense'}
        </Button>
      </form>
    </Card>
  );
}

// ---------------------------------------------------------------------------------------------

function ExpenseList({
  state,
  filtered,
  exportUrl,
  onPage,
  onClear,
}: {
  state: AsyncState<ExpensePage>;
  filtered: boolean;
  exportUrl: string;
  onPage: (page: number) => void;
  onClear: () => void;
}) {
  const toast = useToast();
  const [deleting, setDeleting] = useState<number | null>(null);

  async function remove(e: Expense) {
    if (!window.confirm(`Delete ${inr(e.amount)} at ${e.vendor} on ${longDate(e.date)}?`)) return;
    setDeleting(e.id);
    try {
      await api.deleteExpense(e.id);
      toast.success('Expense deleted');
      state.reload();
    } catch (err) {
      if (!(err instanceof NetworkError)) toast.error(errorMessage(err));
    } finally {
      setDeleting(null);
    }
  }

  if (state.error) return <ErrorBox message={state.error} onRetry={state.reload} />;
  if (state.loading && !state.data) return <SkeletonRows rows={6} />;
  const data = state.data;
  const rows = data?.items ?? [];

  if (data && data.totalItems > 0 && rows.length === 0) {
    // page past the end (e.g. after deleting the last row of the last page)
    return (
      <div className="rounded-lg border border-dashed border-slate-300 px-4 py-10 text-center text-sm text-slate-500">
        <p className="font-medium text-slate-700">This page is empty</p>
        <button type="button" className="mt-2 text-indigo-600 hover:underline" onClick={() => onPage(data.totalPages)}>
          Go to the last page
        </button>
      </div>
    );
  }

  if (!data || rows.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-slate-300 px-4 py-10 text-center text-sm text-slate-500">
        {filtered ? (
          <>
            <p className="font-medium text-slate-700">No expenses match these filters</p>
            <button type="button" className="mt-2 text-indigo-600 hover:underline" onClick={onClear}>
              Clear filters
            </button>
          </>
        ) : (
          <>
            <p className="font-medium text-slate-700">No expenses yet</p>
            <p className="mt-1">
              Add one with the form, or <Link to="/upload" className="text-indigo-600 hover:underline">upload a CSV</Link>.
            </p>
          </>
        )}
      </div>
    );
  }

  const first = data.page * data.size + 1;
  const last = data.page * data.size + rows.length;

  return (
    <div className={state.loading ? 'opacity-60 transition-opacity' : ''}>
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm text-slate-500" data-testid="list-summary">
          {data.totalItems} expense{data.totalItems === 1 ? '' : 's'} · {inr(data.totalAmount)}
        </p>
        <a
          href={exportUrl}
          download
          className="inline-flex items-center gap-1.5 rounded-md bg-white px-2.5 py-1.5 text-xs font-medium text-slate-700 ring-1 ring-slate-300 hover:bg-slate-50"
        >
          <span aria-hidden="true">⤓</span> Export CSV
        </a>
      </div>

      {/* Tablet / desktop: table, scrolls inside its own box if needed */}
      <div className="hidden overflow-x-auto rounded-lg border border-slate-200 sm:block">
        <table className="w-full min-w-[40rem] text-left text-sm">
          <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-3 py-2 font-medium">Date</th>
              <th className="px-3 py-2 font-medium">Vendor</th>
              <th className="px-3 py-2 font-medium">Category</th>
              <th className="px-3 py-2 text-right font-medium">Amount</th>
              <th className="px-3 py-2">
                <span className="sr-only">Actions</span>
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {rows.map((e) => (
              <tr
                key={e.id}
                data-anomaly={e.anomaly || undefined}
                className={e.anomaly ? 'bg-red-50 shadow-[inset_4px_0_0_0_#dc2626]' : 'hover:bg-slate-50'}
              >
                <td className="whitespace-nowrap px-3 py-2.5 text-slate-600">{longDate(e.date)}</td>
                <td className="max-w-[16rem] px-3 py-2.5">
                  <div className="truncate font-medium text-slate-900" title={e.vendor}>
                    {e.vendor}
                  </div>
                  {e.description && (
                    <div className="truncate text-xs text-slate-500" title={e.description}>
                      {e.description}
                    </div>
                  )}
                </td>
                <td className="whitespace-nowrap px-3 py-2.5 text-slate-600">{categoryName(e.category, e.label)}</td>
                <td className="px-3 py-2.5 text-right">
                  <div className="flex items-center justify-end gap-2">
                    {e.anomaly && <AnomalyBadge expense={e} />}
                    <Amount expense={e} />
                  </div>
                </td>
                <td className="px-3 py-2.5 text-right">
                  <Button variant="ghost" size="sm" busy={deleting === e.id} onClick={() => remove(e)} aria-label={`Delete ${e.vendor} ${inr(e.amount)}`}>
                    Delete
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Phone: cards */}
      <ul className="space-y-2 sm:hidden">
        {rows.map((e) => (
          <li
            key={e.id}
            data-anomaly={e.anomaly || undefined}
            className={`rounded-lg border p-3 ${e.anomaly ? 'border-red-200 border-l-4 border-l-red-600 bg-red-50' : 'border-slate-200 bg-white'}`}
          >
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <p className="truncate font-medium text-slate-900">{e.vendor}</p>
                <p className="text-xs text-slate-500">
                  {longDate(e.date)} · {categoryName(e.category, e.label)}
                </p>
              </div>
              <Amount expense={e} />
            </div>
            {e.description && <p className="mt-1 truncate text-xs text-slate-500">{e.description}</p>}
            <div className="mt-2 flex items-center justify-between gap-2">
              <div>{e.anomaly && <AnomalyBadge expense={e} align="left" />}</div>
              <Button variant="ghost" size="sm" busy={deleting === e.id} onClick={() => remove(e)} aria-label={`Delete ${e.vendor} ${inr(e.amount)}`}>
                Delete
              </Button>
            </div>
          </li>
        ))}
      </ul>

      <Pager page={data.page + 1} totalPages={data.totalPages} first={first} last={last} total={data.totalItems} busy={state.loading} onPage={onPage} />
    </div>
  );
}

function Pager({
  page,
  totalPages,
  first,
  last,
  total,
  busy,
  onPage,
}: {
  page: number;
  totalPages: number;
  first: number;
  last: number;
  total: number;
  busy: boolean;
  onPage: (page: number) => void;
}) {
  return (
    <nav aria-label="Pages" className="mt-4 flex flex-wrap items-center justify-between gap-3 text-sm">
      <p className="text-slate-500">
        {first}–{last} of {total}
      </p>
      {totalPages > 1 && (
        <div className="flex items-center gap-2">
          <Button variant="secondary" size="sm" disabled={busy || page <= 1} onClick={() => onPage(page - 1)}>
            ← Prev
          </Button>
          <span className="tabular-nums text-slate-600" aria-current="page">
            Page {page} of {totalPages}
          </span>
          <Button variant="secondary" size="sm" disabled={busy || page >= totalPages} onClick={() => onPage(page + 1)}>
            Next →
          </Button>
        </div>
      )}
    </nav>
  );
}
