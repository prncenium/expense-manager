export interface Category {
  id: number;
  name: string;
}

export interface Expense {
  id: number;
  date: string; // YYYY-MM-DD
  amount: number;
  vendor: string;
  description?: string | null;
  categoryId: number;
  category: string;
  /** Nickname inside Other (e.g. "Gym"), else absent. */
  label?: string | null;
  source: 'MANUAL' | 'CSV';
  createdAt: string;
  categoryAvg: number;
  timesAvg: number;
  anomaly: boolean;
}

/** Response of POST /api/expenses. */
export interface CreatedExpense extends Expense {
  possibleDuplicate: boolean;
}

export interface ExpenseInput {
  date: string;
  amount: number | null;
  vendor: string;
  description: string;
}

export interface ExpenseFilters {
  month?: string; // YYYY-MM
  categoryId?: number;
  label?: string;
  anomaliesOnly?: boolean;
  q?: string;
}

export interface ExpensePage {
  items: Expense[];
  page: number; // 0-based
  size: number;
  totalItems: number;
  totalPages: number;
  totalAmount: number;
}

export interface UncategorizedVendor {
  vendor: string;
  keyword: string;
  count: number;
  total: number;
  lastDate: string;
}

export interface UploadResult {
  total: number;
  saved: number;
  skipped: number; // includes duplicates
  duplicates: number;
  errors: { row: number; reason: string }[];
  anomaliesFound: number;
}

export interface Rule {
  id: number;
  keyword: string;
  categoryId: number;
  category: string;
  label?: string | null;
  /** Only on create / update: how many Other expenses moved. */
  recategorized?: number;
}

export interface RuleInput {
  keyword: string;
  categoryId: number | null;
  label?: string | null;
}

export interface MomRow {
  categoryId: number | null;
  category: string | null;
  label: string | null;
  current: number;
  previous: number;
  changePct: number | null;
}

export interface Dashboard {
  months: string[];
  monthlyTotals: { month: string; categoryId: number; category: string; total: number }[];
  topVendors: { vendor: string; total: number; count: number }[];
  anomalies: { count: number; items: Expense[] };
  totals: { spend: number; count: number };
  monthOverMonth: { month: string; previousMonth: string; total: MomRow; rows: MomRow[] };
  uncategorizedVendors: number;
}

/** GET /api/compare: period A is the baseline, B is compared with it. Dates YYYY-MM-DD, inclusive. */
export interface ComparePeriod {
  from: string;
  to: string;
  days: number;
  count: number;
}

export interface CompareRow {
  categoryId: number | null;
  category: string | null;
  label: string | null;
  a: number;
  b: number;
  changePct: number | null;
}

export interface Comparison {
  a: ComparePeriod;
  b: ComparePeriod;
  total: CompareRow;
  rows: CompareRow[];
}

/** Muneem assistant (docs/ASSISTANT-SPEC.md §7). */
export interface AssistantStatus {
  enabled: boolean;
  providers: string[];
}

export type ChatRole = 'user' | 'assistant';

export interface ChatRequest {
  message: string;
  history: { role: ChatRole; content: string }[];
  voice: boolean;
  lang: 'en-IN' | 'hi-IN';
}

export interface ChatReply {
  reply: string;
  provider: string;
  toolsUsed: string[];
  suggestions: string[];
}

export interface AssistantNote {
  id: number;
  note: string;
  createdAt: string;
}
