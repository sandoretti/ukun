import type { components } from '../../types/api';

type Card = components['schemas']['Card'];
type CardBrand = Card['brand'];

const DOT = '•';

/**
 * How many digits each network's number has, in the groups it is printed in.
 *
 * It is a map and not a single "four groups of four" because that is only true for two of the four
 * brands the contract accepts: American Express numbers are fifteen digits printed 4-6-5, and
 * `OTHER` is, by definition, a card whose length we do not know. Painting sixteen dots for all of
 * them would look like a card and lie about two of them.
 */
const GROUPS: Record<CardBrand, number[] | null> = {
  VISA: [4, 4, 4, 4],
  MASTERCARD: [4, 4, 4, 4],
  AMEX: [4, 6, 5],
  // No shape to imitate: it gets the short form, which is true for every card there is.
  OTHER: null,
};

/**
 * The card as it is shown to its owner: the last four digits, with the rest of the number replaced by
 * dots in the grouping its network really uses.
 *
 * There is nothing to unmask here and no full number to hide — the API never sends one, because it
 * never stores one (`docs/08-identidad-y-seguridad.md` §5). This turns the four digits the contract
 * does carry into something a person recognises as their own card.
 *
 * The result is decorative: a row of dots read aloud is noise, so whoever paints it hides it from
 * assistive technology and gives the control a sentence instead.
 */
export function maskedCardNumber({ brand, last4 }: Pick<Card, 'brand' | 'last4'>): string {
  const groups = GROUPS[brand];

  if (groups === null) {
    return `${DOT.repeat(4)} ${last4}`;
  }

  return groups
    .map((size, index) =>
      // Only the last group carries the real digits; it keeps its width with dots in front, which is
      // what makes an AMEX read `•3456` and not `3456` floating short of the line.
      index === groups.length - 1 ? `${DOT.repeat(size - last4.length)}${last4}` : DOT.repeat(size),
    )
    .join(' ');
}

/**
 * The expiry as it is printed on the card itself: `12/27`.
 *
 * Two digits for the year because that is what the plastic says and what anybody comparing the two
 * expects; the profile keeps the four-digit year, which is the one that gets stored and compared.
 * No `Intl` here on purpose — this is not a date (a card expires over a whole month, not on a day,
 * `docs/10-modelo-de-datos.md` §2), and no locale prints it differently.
 */
export function formatCardExpiry({ expMonth, expYear }: Pick<Card, 'expMonth' | 'expYear'>): string {
  return `${String(expMonth).padStart(2, '0')}/${String(expYear % 100).padStart(2, '0')}`;
}
