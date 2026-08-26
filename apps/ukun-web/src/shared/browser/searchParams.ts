/**
 * A whole, non-negative number out of a query parameter, or `null` when it is not one.
 *
 * Every listing in the app needs it —the page number, an identifier used as a filter— and every
 * listing had written it: the catalogue, the order history, the stock table and the back-office
 * catalogue carried four identical copies of these four lines. It lives here, next to `navigateTo`,
 * because what it knows about is the address bar and not any one feature's filters.
 *
 * **A regular expression and not `Number(...)`**, which is the part worth keeping: `Number('')` is
 * `0`, `Number(' 3 ')` is `3` and `Number('3px')` is `NaN`, so a hand-typed `?page=` or a pasted link
 * with a stray character would each turn into a different kind of wrong. Anything that is not digits
 * is rejected outright, and what the caller does with a `null` —fall back to page one, drop the
 * filter— is the caller's decision.
 */
export function readPositiveInteger(value: string | null): number | null {
  if (value === null || !/^\d+$/.test(value)) {
    return null;
  }

  return Number(value);
}
