/**
 * Instants the contract sends as RFC 3339 strings (`placedAt`, `expiresAt`), printed in the reader's
 * locale. Same rule as `formatMoney`: the server sends the value, the frontend decides how it reads
 * (ADR-0019) — a date formatted server-side would be one more thing to translate twice.
 */
export function formatDateTime(instant: string, locale: string): string {
  return new Intl.DateTimeFormat(locale, { dateStyle: 'long', timeStyle: 'short' }).format(
    new Date(instant),
  );
}

/** The same instant when only the day matters — an order in a list, a date filter. */
export function formatDate(instant: string, locale: string): string {
  return new Intl.DateTimeFormat(locale, { dateStyle: 'medium' }).format(new Date(instant));
}

