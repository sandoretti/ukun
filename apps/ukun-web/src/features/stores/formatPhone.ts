/** A Spanish number as it arrives from the API: E.164, country code and nine digits, no spaces. */
const SPANISH_E164 = /^\+34(\d{3})(\d{3})(\d{3})$/;

/**
 * The phone number as it is read out, in groups of three. It is not an `Intl` format — there is no
 * such formatter for phone numbers — so anything that is not the shape above comes back untouched
 * rather than sliced into invented groups.
 *
 * Only the display text goes through here. What the `tel:` link carries is always the raw E.164
 * value, which is what a dialler expects.
 *
 * It lives in the feature because only this screen shows a phone number today; the day a second one
 * does, it moves up to `shared/format/` next to `money` and `number`.
 */
export function formatPhone(phone: string): string {
  const match = SPANISH_E164.exec(phone);
  return match === null ? phone : `+34 ${match[1]} ${match[2]} ${match[3]}`;
}
