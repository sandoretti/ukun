import type { components } from '../../types/api';

type CardBrand = components['schemas']['Card']['brand'];

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

/** How long a number of each brand is. `OTHER` takes the whole range ISO/IEC 7812 allows. */
const LENGTHS: Record<CardBrand, number[]> = {
  VISA: [13, 16, 19],
  MASTERCARD: [16],
  AMEX: [15],
  OTHER: [12, 13, 14, 15, 16, 17, 18, 19],
};

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
  return LENGTHS[detectCardBrand(digits)].includes(digits.length) && passesLuhn(digits);
}
