/**
 * Categorical colors, fixed per category id (never by rank), validated with the dataviz
 * palette checker. "Other" (id 9) is a deliberate neutral gray.
 */
const CATEGORY_COLORS: Record<number, string> = {
  1: '#2a78d6', // Food
  2: '#eb6834', // Groceries
  3: '#1baf7a', // Transport
  4: '#eda100', // Shopping
  5: '#e87ba4', // Entertainment
  6: '#008300', // Bills & Utilities
  7: '#6250d6', // Travel
  8: '#e34948', // Health
  9: '#8a8984', // Other
};
export const colorFor = (id: number) => CATEGORY_COLORS[id] ?? '#8a8984';
