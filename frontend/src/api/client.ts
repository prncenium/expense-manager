import type {
  AssistantNote,
  AssistantStatus,
  Category,
  ChatReply,
  ChatRequest,
  Comparison,
  CreatedExpense,
  Dashboard,
  ExpenseFilters,
  ExpenseInput,
  ExpensePage,
  Rule,
  RuleInput,
  UncategorizedVendor,
  UploadResult,
} from '../types';
import { serverStatus } from '../lib/serverStatus';

/**
 * API base URL. Defaults to relative /api (Vite dev proxy / nginx in Docker forward it to the backend).
 * Set VITE_API_BASE_URL at build time (e.g. https://api.example.com/api) when the backend is on another host.
 */
const BASE = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/+$/, '');

export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors: Record<string, string>;

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

/** Thrown when the backend cannot be reached at all (network error / proxy error). */
export class NetworkError extends Error {
  constructor() {
    super("Can't reach server");
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let res: Response;
  try {
    res = await fetch(BASE + path, init);
  } catch {
    serverStatus.setDown(true);
    throw new NetworkError();
  }

  const text = await res.text();
  let body: unknown = null;
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      body = null;
    }
  }

  if (!res.ok) {
    const err = body as { message?: unknown; fieldErrors?: unknown } | null;
    if (!err || typeof err.message !== 'string') {
      // Not our backend's JSON error: the dev proxy answers like this when Spring Boot is down.
      if (res.status >= 500) {
        serverStatus.setDown(true);
        throw new NetworkError();
      }
      throw new ApiError(res.status, `Request failed (${res.status})`);
    }
    serverStatus.setDown(false);
    const fieldErrors =
      err.fieldErrors && typeof err.fieldErrors === 'object' ? (err.fieldErrors as Record<string, string>) : {};
    throw new ApiError(res.status, err.message, fieldErrors);
  }

  serverStatus.setDown(false);
  return body as T;
}

function json(method: string, data: unknown): RequestInit {
  return { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) };
}

export const api = {
  categories: () => request<Category[]>('/categories'),

  expenses: (f: ExpenseFilters = {}, page = 0, size = 50) => {
    const q = filterParams(f);
    q.set('page', String(page));
    q.set('size', String(size));
    return request<ExpensePage>(`/expenses?${q}`);
  },
  /** Download URL for the CSV export of the current filters (all pages). */
  exportUrl: (f: ExpenseFilters = {}) => {
    const qs = filterParams(f).toString();
    return `${BASE}/expenses/export${qs ? `?${qs}` : ''}`;
  },
  addExpense: (e: ExpenseInput) =>
    request<CreatedExpense>('/expenses', json('POST', { ...e, description: e.description.trim() || null })),
  deleteExpense: (id: number) => request<null>(`/expenses/${id}`, { method: 'DELETE' }),
  upload: (file: File) => {
    const form = new FormData();
    form.append('file', file);
    return request<UploadResult>('/expenses/upload', { method: 'POST', body: form });
  },
  loadSampleData: () => request<UploadResult>('/sample-data', { method: 'POST' }),

  rules: () => request<Rule[]>('/rules'),
  addRule: (r: RuleInput) => request<Rule>('/rules', json('POST', r)),
  updateRule: (id: number, r: RuleInput) => request<Rule>(`/rules/${id}`, json('PUT', r)),
  deleteRule: (id: number) => request<null>(`/rules/${id}`, { method: 'DELETE' }),

  uncategorized: () => request<UncategorizedVendor[]>('/vendors/uncategorized'),
  labels: () => request<string[]>('/labels'),

  dashboard: (month?: string) => request<Dashboard>('/dashboard' + (month ? `?month=${month}` : '')),
  compare: (a: { from: string; to: string }, b: { from: string; to: string }) => {
    const q = new URLSearchParams({ aFrom: a.from, aTo: a.to, bFrom: b.from, bTo: b.to });
    return request<Comparison>(`/compare?${q}`);
  },

  assistantStatus: () => request<AssistantStatus>('/assistant/status'),
  chat: (r: ChatRequest) => request<ChatReply>('/assistant/chat', json('POST', r)),
  notes: () => request<AssistantNote[]>('/assistant/notes'),
  deleteNote: (id: number) => request<null>(`/assistant/notes/${id}`, { method: 'DELETE' }),
  deleteAllNotes: () => request<null>('/assistant/notes', { method: 'DELETE' }),
};

function filterParams(f: ExpenseFilters): URLSearchParams {
  const q = new URLSearchParams();
  if (f.month) q.set('month', f.month);
  if (f.categoryId != null) q.set('categoryId', String(f.categoryId));
  if (f.categoryId != null && f.label) q.set('label', f.label);
  if (f.anomaliesOnly) q.set('anomaliesOnly', 'true');
  if (f.q?.trim()) q.set('q', f.q.trim());
  return q;
}

/** Human message for any error thrown by the client. */
export function errorMessage(e: unknown): string {
  if (e instanceof ApiError || e instanceof NetworkError) return e.message;
  return 'Something went wrong';
}
