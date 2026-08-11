import type { components } from '../../types/api';

type Card = components['schemas']['Card'];
type CardBrand = Card['brand'];

const DOT = '•';

/**
 * What this application knows about each card network, in one table.
 *
 * The two facts belong together and used to live apart, which is how they drift: `groups` is how the
 * number is **printed** (an American Express is 4-6-5, not four groups of four) and `lengths` is how
 * long it may **be**. They are not derivable from each other — a Visa prints in the sixteen-digit
 * shape whether it has 13, 16 or 19 digits — but a brand added to one and forgotten in the other is a
 * bug nothing would report.
 *
 * `OTHER` is a card outside the four the contract knows: no shape to imitate, and the whole range
 * ISO/IEC 7812 allows.
 */
const NETWORKS: Record<CardBrand, { groups: number[] | null; lengths: number[] }> = {
  VISA: { groups: [4, 4, 4, 4], lengths: [13, 16, 19] },
  MASTERCARD: { groups: [4, 4, 4, 4], lengths: [16] },
  AMEX: { groups: [4, 6, 5], lengths: [15] },
  OTHER: { groups: null, lengths: [12, 13, 14, 15, 16, 17, 18, 19] },
};

/**
 * The digits of what somebody typed. Cards are printed in groups and people type them that way, so
 * spaces and dashes are input, not mistakes.
 */
export function cardDigits(value: string): string {
  return value.replace(/\D/gu, '');
}

/**
 * Which network a number belongs to, from its first digits (the IIN).
 *
 * These prefixes are public and stable — they are printed in the networks' own specifications — and
 * knowing them is what lets the form stop asking the reader something the number already says. What
 * it cannot tell is a card outside the four brands the contract knows: that one is `OTHER`, which is
 * an honest answer and not a failure.
 */
export function detectCardBrand(digits: string): CardBrand {
  if (/^4/u.test(digits)) {
    return 'VISA';
  }
  if (/^3[47]/u.test(digits)) {
    return 'AMEX';
  }
  // The old 51-55 range and the 2221-2720 one Mastercard added in 2017.
  if (/^5[1-5]/u.test(digits) || /^2(2[2-9]|[3-6]\d|7[01]|720)/u.test(digits)) {
    return 'MASTERCARD';
  }

  return 'OTHER';
}

/**
 * The Luhn checksum every card number carries in its last digit.
 *
 * It is not security — anybody can compute it — it is a typo catcher, and that is exactly what is
 * needed here: a mistyped digit would otherwise be stored as somebody else's last four.
 */
function passesLuhn(digits: string): boolean {
  let sum = 0;
  let double = false;

  for (let index = digits.length - 1; index >= 0; index -= 1) {
    let value = Number(digits[index]);
    if (double) {
      value *= 2;
      if (value > 9) {
        value -= 9;
      }
    }
    sum += value;
    double = !double;
  }

  return sum % 10 === 0;
}

/**
 * Whether what was typed can be a real card number: the right length for its brand, and a checksum
 * that adds up.
 *
 * This is all the checking a page like this can do, and it is done **before** anything is derived
 * from the number — a number that fails here never becomes a `last4`.
 */
export function isPlausibleCardNumber(digits: string): boolean {
  return NETWORKS[detectCardBrand(digits)].lengths.includes(digits.length) && passesLuhn(digits);
}

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
  const { groups } = NETWORKS[brand];

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
