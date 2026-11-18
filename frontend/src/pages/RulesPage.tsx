import { useState, type FormEvent } from 'react';
import { api, ApiError, errorMessage, NetworkError } from '../api/client';
import { useToast } from '../components/Toasts';
import { Button, Card, ErrorBox, inputClass, labelClass, PageHeader, Pager, SkeletonRows } from '../components/ui';

const RULES_PAGE_SIZE = 10;
import { categoryName } from '../lib/categorize';
import { inr, shortDate } from '../lib/format';
import { useAsync } from '../lib/useAsync';
import type { Category, Rule, RuleInput, UncategorizedVendor } from '../types';

interface FormErrors {
  keyword?: string;
  categoryId?: string;
  label?: string;
  general?: string;
}

const isOther = (categories: Category[], id: string) => categories.find((c) => String(c.id) === id)?.name === 'Other';

/** "Rule saved · 4 expenses moved" */
function savedMessage(r: Rule): string {
  const n = r.recategorized ?? 0;
  return n > 0 ? `Rule saved · ${n} expense${n === 1 ? '' : 's'} moved` : 'Rule saved';
}

/** 400 field errors go under the field; 409 (duplicate keyword) goes under the keyword field too. */
function toFormErrors(e: unknown): FormErrors | null {
  if (!(e instanceof ApiError)) return null;
  if (e.status === 409) return { keyword: e.message };
  const f = e.fieldErrors;
  if (f.keyword || f.categoryId || f.label) {
    return {
      keyword: f.keyword ? `Keyword ${f.keyword}` : undefined,
      categoryId: f.categoryId ? `Category ${f.categoryId}` : undefined,
      label: f.label ? `Nickname ${f.label}` : undefined,
    };
  }
  return { general: e.message };
}

export function RulesPage() {
  const toast = useToast();
  const categories = useAsync(() => api.categories(), []);
  const rules = useAsync(() => api.rules(), []);
  const cats = categories.data ?? [];

  const [keyword, setKeyword] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [label, setLabel] = useState('');
  const [addErrors, setAddErrors] = useState<FormErrors>({});
  const [adding, setAdding] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [rulePage, setRulePage] = useState(1);
  // bumped after any rule change so the Uncategorized panel reloads
  const [panelVersion, setPanelVersion] = useState(0);
  const rulesChanged = () => {
    rules.reload();
    setPanelVersion((v) => v + 1);
  };

  async function add(ev: FormEvent) {
    ev.preventDefault();
    setAdding(true);
    try {
      const saved = await api.addRule({
        keyword,
        categoryId: categoryId ? Number(categoryId) : null,
        label: isOther(cats, categoryId) ? label : null,
      });
      toast.success(savedMessage(saved));
      setKeyword('');
      setLabel('');
      setAddErrors({});
      rulesChanged();
    } catch (e) {
      const fe = toFormErrors(e);
      if (fe) setAddErrors(fe);
      else if (!(e instanceof NetworkError)) toast.error(errorMessage(e));
    } finally {
      setAdding(false);
    }
  }

  const sorted = [...(rules.data ?? [])].sort((a, b) => a.category.localeCompare(b.category) || a.keyword.localeCompare(b.keyword));
  // Client-side paging: clamp so deleting the last rule on the last page steps back a page.
  const totalPages = Math.max(1, Math.ceil(sorted.length / RULES_PAGE_SIZE));
  const page = Math.min(rulePage, totalPages);
  const pageRules = sorted.slice((page - 1) * RULES_PAGE_SIZE, page * RULES_PAGE_SIZE);

  return (
    <>
      <PageHeader title="Rules" hero="rules" />
      <p className="-mt-3 mb-5 max-w-2xl text-sm text-slate-500">
        A vendor gets the category of the <strong>longest keyword</strong> it contains (case-insensitive); no match means{' '}
        <strong>Other</strong>. Saving a rule also moves matching expenses that are still in Other; expenses already in
        a real category are never changed. For Other you can add a <strong>nickname</strong> (e.g. "Gym").
      </p>

      <UncategorizedPanel categories={cats} version={panelVersion} onRuleSaved={rulesChanged} />

      <div className="grid gap-6 lg:grid-cols-[22rem_1fr] lg:items-start">
        <Card>
          <h2 className="mb-4 text-base font-semibold text-slate-900">Add rule</h2>
          <form onSubmit={add} noValidate className="space-y-4">
            <div>
              <label htmlFor="r-keyword" className={labelClass}>
                Vendor keyword
              </label>
              <input
                id="r-keyword"
                className={inputClass}
                placeholder="e.g. dunzo"
                maxLength={100}
                value={keyword}
                aria-invalid={Boolean(addErrors.keyword)}
                aria-describedby={addErrors.keyword ? 'r-keyword-err' : undefined}
                onChange={(e) => {
                  setKeyword(e.target.value);
                  setAddErrors((x) => ({ ...x, keyword: undefined }));
                }}
              />
              {addErrors.keyword && (
                <p id="r-keyword-err" className="mt-1 text-xs text-red-600">
                  {addErrors.keyword}
                </p>
              )}
            </div>
            <div>
              <label htmlFor="r-category" className={labelClass}>
                Category
              </label>
              <CategorySelect
                id="r-category"
                categories={cats}
                value={categoryId}
                invalid={Boolean(addErrors.categoryId)}
                onChange={(v) => {
                  setCategoryId(v);
                  setAddErrors((x) => ({ ...x, categoryId: undefined }));
                }}
              />
              {addErrors.categoryId && <p className="mt-1 text-xs text-red-600">{addErrors.categoryId}</p>}
            </div>
            {isOther(cats, categoryId) && (
              <NicknameField
                id="r-label"
                value={label}
                error={addErrors.label}
                onChange={(v) => {
                  setLabel(v);
                  setAddErrors((x) => ({ ...x, label: undefined }));
                }}
              />
            )}
            {addErrors.general && <p className="text-sm text-red-600">{addErrors.general}</p>}
            <Button type="submit" busy={adding} className="w-full">
              {adding ? 'Saving…' : 'Add rule'}
            </Button>
          </form>
        </Card>

        <Card className="min-w-0">
          {rules.error ? (
            <ErrorBox message={rules.error} onRetry={rules.reload} />
          ) : rules.loading && !rules.data ? (
            <SkeletonRows rows={8} />
          ) : sorted.length === 0 ? (
            <div className="rounded-lg border border-dashed border-slate-300 px-4 py-10 text-center text-sm text-slate-500">
              <p className="font-medium text-slate-700">No rules yet</p>
              <p className="mt-1">Add a keyword on the left; every vendor will fall into Other until then.</p>
            </div>
          ) : (
            <>
              <p className="mb-3 text-sm text-slate-500">{sorted.length} rules</p>
              <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200">
                {pageRules.map((r) =>
                  editingId === r.id ? (
                    <EditRow
                      key={r.id}
                      rule={r}
                      categories={cats}
                      onDone={(saved) => {
                        setEditingId(null);
                        if (saved) rulesChanged();
                      }}
                    />
                  ) : (
                    <ViewRow key={r.id} rule={r} onEdit={() => setEditingId(r.id)} onDeleted={rulesChanged} />
                  ),
                )}
              </ul>
              <Pager
                page={page}
                totalPages={totalPages}
                first={(page - 1) * RULES_PAGE_SIZE + 1}
                last={(page - 1) * RULES_PAGE_SIZE + pageRules.length}
                total={sorted.length}
                noun="rules"
                onPage={(p) => {
                  setEditingId(null);
                  setRulePage(p);
                }}
              />
            </>
          )}
        </Card>
      </div>
    </>
  );
}

function CategorySelect({
  id,
  categories,
  value,
  invalid,
  onChange,
}: {
  id: string;
  categories: Category[];
  value: string;
  invalid?: boolean;
  onChange: (v: string) => void;
}) {
  return (
    <select id={id} className={inputClass} value={value} aria-invalid={invalid} onChange={(e) => onChange(e.target.value)}>
      <option value="">Choose a category</option>
      {categories.map((c) => (
        <option key={c.id} value={c.id}>
          {c.name}
        </option>
      ))}
    </select>
  );
}

function ViewRow({ rule, onEdit, onDeleted }: { rule: Rule; onEdit: () => void; onDeleted: () => void }) {
  const toast = useToast();
  const [deleting, setDeleting] = useState(false);

  async function remove() {
    if (!window.confirm(`Delete the rule "${rule.keyword}" → ${categoryName(rule.category, rule.label)}?`)) return;
    setDeleting(true);
    try {
      await api.deleteRule(rule.id);
      toast.success('Rule deleted');
      onDeleted();
    } catch (e) {
      if (!(e instanceof NetworkError)) toast.error(errorMessage(e));
      setDeleting(false);
    }
  }

  return (
    <li className="flex flex-wrap items-center gap-x-3 gap-y-2 px-3 py-2.5" data-rule={rule.keyword}>
      <div className="flex min-w-0 flex-1 items-center gap-2 text-sm">
        <code className="truncate rounded bg-slate-100 px-1.5 py-0.5 text-slate-800">{rule.keyword}</code>
        <span className="text-slate-400" aria-hidden="true">
          →
        </span>
        <span className="truncate text-slate-700">{categoryName(rule.category, rule.label)}</span>
      </div>
      <div className="flex gap-1">
        <Button variant="ghost" size="sm" onClick={onEdit} aria-label={`Edit rule ${rule.keyword}`}>
          Edit
        </Button>
        <Button variant="ghost" size="sm" busy={deleting} onClick={remove} className="text-red-600" aria-label={`Delete rule ${rule.keyword}`}>
          Delete
        </Button>
      </div>
    </li>
  );
}

function EditRow({ rule, categories, onDone }: { rule: Rule; categories: Category[]; onDone: (saved: boolean) => void }) {
  const toast = useToast();
  const [keyword, setKeyword] = useState(rule.keyword);
  const [categoryId, setCategoryId] = useState(String(rule.categoryId));
  const [label, setLabel] = useState(rule.label ?? '');
  const [errors, setErrors] = useState<FormErrors>({});
  const [saving, setSaving] = useState(false);
  const other = isOther(categories, categoryId);

  async function save(ev: FormEvent) {
    ev.preventDefault();
    setSaving(true);
    const body: RuleInput = { keyword, categoryId: categoryId ? Number(categoryId) : null, label: other ? label : null };
    try {
      const saved = await api.updateRule(rule.id, body);
      toast.success(savedMessage(saved));
      onDone(true);
    } catch (e) {
      const fe = toFormErrors(e);
      if (fe) setErrors(fe);
      else if (!(e instanceof NetworkError)) toast.error(errorMessage(e));
      setSaving(false);
    }
  }

  return (
    <li className="bg-indigo-50/50 px-3 py-3">
      <form onSubmit={save} noValidate className="grid grid-cols-1 gap-2 sm:grid-cols-[1fr_1fr_auto] sm:items-start">
        <div>
          <label htmlFor={`e-kw-${rule.id}`} className="sr-only">
            Keyword
          </label>
          <input
            id={`e-kw-${rule.id}`}
            className={inputClass}
            value={keyword}
            maxLength={100}
            autoFocus
            aria-invalid={Boolean(errors.keyword)}
            onChange={(e) => {
              setKeyword(e.target.value);
              setErrors((x) => ({ ...x, keyword: undefined }));
            }}
            onKeyDown={(e) => {
              if (e.key === 'Escape') onDone(false);
            }}
          />
          {errors.keyword && <p className="mt-1 text-xs text-red-600">{errors.keyword}</p>}
        </div>
        <div>
          <label htmlFor={`e-cat-${rule.id}`} className="sr-only">
            Category
          </label>
          <CategorySelect id={`e-cat-${rule.id}`} categories={categories} value={categoryId} invalid={Boolean(errors.categoryId)} onChange={setCategoryId} />
          {errors.categoryId && <p className="mt-1 text-xs text-red-600">{errors.categoryId}</p>}
          {other && (
            <div className="mt-2">
              <label htmlFor={`e-label-${rule.id}`} className="sr-only">
                Nickname
              </label>
              <input
                id={`e-label-${rule.id}`}
                className={inputClass}
                placeholder="Nickname (optional), e.g. Gym"
                maxLength={50}
                value={label}
                aria-invalid={Boolean(errors.label)}
                onChange={(e) => {
                  setLabel(e.target.value);
                  setErrors((x) => ({ ...x, label: undefined }));
                }}
              />
              {errors.label && <p className="mt-1 text-xs text-red-600">{errors.label}</p>}
            </div>
          )}
        </div>
        <div className="flex gap-2">
          <Button type="submit" busy={saving} className="flex-1 sm:flex-none">
            Save
          </Button>
          <Button variant="secondary" onClick={() => onDone(false)} disabled={saving} className="flex-1 sm:flex-none">
            Cancel
          </Button>
        </div>
        {errors.general && <p className="text-sm text-red-600 sm:col-span-3">{errors.general}</p>}
      </form>
    </li>
  );
}

function NicknameField({
  id,
  value,
  error,
  required,
  onChange,
}: {
  id: string;
  value: string;
  error?: string;
  required?: boolean;
  onChange: (v: string) => void;
}) {
  return (
    <div>
      <label htmlFor={id} className={labelClass}>
        Nickname {!required && <span className="font-normal text-slate-400">(optional)</span>}
      </label>
      <input
        id={id}
        className={inputClass}
        placeholder="e.g. Gym, Rent, Chai"
        maxLength={50}
        value={value}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? `${id}-err` : `${id}-help`}
        onChange={(e) => onChange(e.target.value)}
      />
      {error ? (
        <p id={`${id}-err`} className="mt-1 text-xs text-red-600">
          {error}
        </p>
      ) : (
        <p id={`${id}-help`} className="mt-1 text-xs text-slate-500">
          Stays in Other, shown as "Other · {value.trim() || 'Gym'}".
        </p>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------------------------

/** SPEC A: vendors stuck in Other with no nickname, each with a one-click "Create rule". */
function UncategorizedPanel({
  categories,
  version,
  onRuleSaved,
}: {
  categories: Category[];
  version: number;
  onRuleSaved: () => void;
}) {
  const panel = useAsync(() => api.uncategorized(), [version]);
  const [openVendor, setOpenVendor] = useState<string | null>(null);
  const rows = panel.data ?? [];

  return (
    <Card className="mb-6 min-w-0">
      <div className="mb-3 flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-base font-semibold text-slate-900">Uncategorized vendors</h2>
        {rows.length > 0 && (
          <p className="text-xs text-slate-500">
            {rows.length}
            {rows.length >= 50 ? '+' : ''} vendor{rows.length === 1 ? '' : 's'} in Other, biggest spend first
          </p>
        )}
      </div>
      {panel.error ? (
        <ErrorBox message={panel.error} onRetry={panel.reload} />
      ) : panel.loading && !panel.data ? (
        <SkeletonRows rows={3} />
      ) : rows.length === 0 ? (
        <p className="rounded-lg border border-dashed border-slate-300 px-4 py-6 text-center text-sm text-slate-500">
          All vendors are sorted 🎉 Nothing is sitting in Other without a rule.
        </p>
      ) : (
        <ul className={`divide-y divide-slate-100 rounded-lg border border-slate-200 ${panel.loading ? 'opacity-60' : ''}`}>
          {rows.map((v) => (
            <li key={v.keyword} className="px-3 py-2.5">
              <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-2">
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-slate-900" title={v.vendor}>
                    {v.vendor}
                  </p>
                  <p className="text-xs text-slate-500">
                    {v.count} expense{v.count === 1 ? '' : 's'} · {inr(v.total)} · last {shortDate(v.lastDate)}
                  </p>
                </div>
                {openVendor !== v.keyword && (
                  <Button variant="secondary" size="sm" onClick={() => setOpenVendor(v.keyword)} aria-label={`Create rule for ${v.vendor}`}>
                    Create rule
                  </Button>
                )}
              </div>
              {openVendor === v.keyword && (
                <QuickRuleForm
                  vendor={v}
                  categories={categories}
                  onDone={(saved) => {
                    setOpenVendor(null);
                    if (saved) onRuleSaved();
                  }}
                />
              )}
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

function QuickRuleForm({
  vendor,
  categories,
  onDone,
}: {
  vendor: UncategorizedVendor;
  categories: Category[];
  onDone: (saved: boolean) => void;
}) {
  const toast = useToast();
  const [keyword, setKeyword] = useState(vendor.keyword);
  const [categoryId, setCategoryId] = useState('');
  const [label, setLabel] = useState('');
  const [errors, setErrors] = useState<FormErrors>({});
  const [saving, setSaving] = useState(false);
  const other = isOther(categories, categoryId);
  const id = `q-${vendor.keyword.replace(/[^a-z0-9]/g, '-')}`;

  async function save(ev: FormEvent) {
    ev.preventDefault();
    // A rule to plain Other would leave the vendor in this panel, so a nickname is required here.
    if (other && !label.trim()) {
      setErrors({ label: 'Nickname is required when the category is Other' });
      return;
    }
    setSaving(true);
    try {
      const saved = await api.addRule({ keyword, categoryId: categoryId ? Number(categoryId) : null, label: other ? label : null });
      toast.success(savedMessage(saved));
      onDone(true);
    } catch (e) {
      const fe = toFormErrors(e);
      if (fe) setErrors(fe);
      else if (!(e instanceof NetworkError)) toast.error(errorMessage(e));
      setSaving(false);
    }
  }

  return (
    <form onSubmit={save} noValidate className="mt-3 grid grid-cols-1 gap-3 rounded-lg bg-indigo-50/50 p-3 sm:grid-cols-2">
      <div>
        <label htmlFor={`${id}-kw`} className={labelClass}>
          Keyword
        </label>
        <input
          id={`${id}-kw`}
          className={inputClass}
          maxLength={100}
          value={keyword}
          aria-invalid={Boolean(errors.keyword)}
          onChange={(e) => {
            setKeyword(e.target.value);
            setErrors((x) => ({ ...x, keyword: undefined }));
          }}
          onKeyDown={(e) => {
            if (e.key === 'Escape') onDone(false);
          }}
        />
        {errors.keyword ? (
          <p className="mt-1 text-xs text-red-600">{errors.keyword}</p>
        ) : (
          <p className="mt-1 text-xs text-slate-500">Shorten it to match more spellings (e.g. "chai point").</p>
        )}
      </div>
      <div>
        <label htmlFor={`${id}-cat`} className={labelClass}>
          Category
        </label>
        <CategorySelect
          id={`${id}-cat`}
          categories={categories}
          value={categoryId}
          invalid={Boolean(errors.categoryId)}
          onChange={(v) => {
            setCategoryId(v);
            setErrors((x) => ({ ...x, categoryId: undefined, label: undefined }));
          }}
        />
        {errors.categoryId && <p className="mt-1 text-xs text-red-600">{errors.categoryId}</p>}
      </div>
      {other && (
        <div className="sm:col-span-2">
          <NicknameField
            id={`${id}-label`}
            value={label}
            error={errors.label}
            required
            onChange={(v) => {
              setLabel(v);
              setErrors((x) => ({ ...x, label: undefined }));
            }}
          />
        </div>
      )}
      {errors.general && <p className="text-sm text-red-600 sm:col-span-2">{errors.general}</p>}
      <div className="flex gap-2 sm:col-span-2">
        <Button type="submit" busy={saving}>
          {saving ? 'Saving…' : 'Save rule'}
        </Button>
        <Button variant="secondary" onClick={() => onDone(false)} disabled={saving}>
          Cancel
        </Button>
      </div>
    </form>
  );
}
