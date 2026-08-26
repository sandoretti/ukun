import type { components } from '../../types/api';

type Money = components['schemas']['Money'];

/**
 * The shape of an amount, exactly as the contract declares it (`ukun-api.yaml`, schema `Money`): up
 * to eight whole digits and at most two decimals, as text.
 *
 * It lives next to `formatMoney` because both are the same fact about money read in two directions —
 * how it arrives and how it is shown — and because two features check it: the catalogue drops a
 * malformed `?priceMin=` rather than forwarding it, and the back-office form rejects a mistyped price
 * before it becomes a request the server would only answer with a `400`. Written twice, it is the
 * kind of rule that drifts the day the contract changes one of them.
 */
export const MONEY_PATTERN = /^\d{1,8}(\.\d{1,2})?$/u;

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
