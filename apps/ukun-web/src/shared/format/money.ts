import type { components } from '../../types/api';

type Money = components['schemas']['Money'];

/**
 * Formats an amount that travels as an exact decimal string (defect #5: money is never a float).
 * `Intl.NumberFormat` accepts the string as-is since Intl V3, so the value is never rounded through
 * a `double` on its way to the screen — hence no `Number()`/`parseFloat` here. Currency and
 * separators belong to the frontend, never to the backend (ADR-0019).
 */
export function formatMoney(amount: Money, locale: string): string {
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency: 'EUR',
    // The lib types only accept numeric literals; the runtime accepts any decimal string.
  }).format(amount as `${number}`);
}
