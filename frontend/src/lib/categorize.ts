import type { Rule } from '../types';

/**
 * Client copy of the backend rule (SPEC §5), used only for the live "will be categorized as" hint:
 * case-insensitive "vendor contains keyword", longest keyword wins (tie: alphabetical), no match → Other.
 * The backend still decides the real category when the expense is saved.
 */
export function previewCategory(vendor: string, rules: Rule[]): string {
  const v = vendor.trim().toLowerCase();
  if (!v) return '';
  let best: Rule | null = null;
  for (const r of rules) {
    if (!v.includes(r.keyword)) continue;
    if (!best || r.keyword.length > best.keyword.length || (r.keyword.length === best.keyword.length && r.keyword < best.keyword)) {
      best = r;
    }
  }
  return best ? categoryName(best.category, best.label) : 'Other';
}

/** "Other · Gym" when there is a nickname, else just the category. */
export function categoryName(category: string, label?: string | null): string {
  return label ? `${category} · ${label}` : category;
}
