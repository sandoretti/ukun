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

/** Where the currency symbol goes and what it looks like, for the locale in force. */
export type CurrencyAffix = {
  symbol: string;
  /** `es` writes `1.234,00 €`; `en` writes `€1,234.00`. The side is part of the locale, not decoration. */
  position: 'prefix' | 'suffix';
};

/**
 * The currency symbol as a standalone piece, for the one place that cannot use `formatMoney`: an
 * input the reader is typing an amount **into**, where the symbol is an adornment beside the field
 * and not part of its value.
 *
 * It is asked of `Intl` rather than written down because the side changes with the language — the
 * catalogue's price filter used to carry a literal `€` after the field, which is right in Spanish
 * and wrong in English. Reading it from `formatToParts` keeps that decision in the same place as
 * `formatMoney`'s, so the two can never disagree.
 */
export function currencyAffix(locale: string): CurrencyAffix {
  const parts = new Intl.NumberFormat(locale, { style: 'currency', currency: 'EUR' }).formatToParts(0);
  const index = parts.findIndex((part) => part.type === 'currency');

  return {
    // `Intl` always emits a currency part for `style: 'currency'`; the fallback is there so a missing
    // one degrades to the symbol instead of to `undefined` on screen.
    symbol: index === -1 ? '€' : parts[index].value,
    position: parts.slice(0, index).some((part) => part.type === 'integer') ? 'suffix' : 'prefix',
  };
}
