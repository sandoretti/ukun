/**
 * Formats a plain count for display. Same rule as `formatMoney`: grouping separators are the
 * frontend's business and come from `Intl`, never from a hand-rolled regular expression and never
 * from the backend (ADR-0019) — `1200` reads `1.200` in Spanish and `1,200` in English.
 */
export function formatNumber(value: number, locale: string): string {
  return new Intl.NumberFormat(locale).format(value);
}
