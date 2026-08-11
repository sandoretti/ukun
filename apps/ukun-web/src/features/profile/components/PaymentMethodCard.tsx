import { useTranslation } from 'react-i18next';

import { formatCardExpiry, maskedCardNumber } from '../../../shared/format/card';
import type { CustomerProfile } from '../api/profileKeys';

type PaymentMethodCardProps = {
  card: CustomerProfile['card'];
};

/**
 * The payment method, shown as a card and not as four boxes with `4242` in one of them.
 *
 * There is no full number to reveal and no CVV anywhere near this component: the contract carries
 * `{last4, brand, expMonth, expYear}` and nothing else, because storing more is the PCI-DSS violation
 * of the legacy schema (`docs/08-identidad-y-seguridad.md` §5). What this does is turn the little the
 * contract keeps into something its owner recognises at a glance.
 *
 * The brand is text. A blue rectangle with "VISA" on it would be somebody else's logo, would need an
 * image served for it, and would drag a colour into the screen that is not in the theme's palette.
 *
 * **Everything visible is hidden from assistive technology, and one sentence replaces it.** A row of
 * bullets read out loud is noise: "bullet bullet bullet bullet…" says nothing, while "Visa card
 * ending in 4242, expires 12/27" says all of it in one breath.
 */
export function PaymentMethodCard({ card }: PaymentMethodCardProps) {
  const { t } = useTranslation('profile');

  const brand = t(`card.brands.${card.brand}`);
  const expiry = formatCardExpiry(card);

  return (
    <div className="payment-card">
      <div aria-hidden="true">
        <p className="overline mb-2">{brand}</p>
        <p className="payment-card__number text-tabular-nums mb-2">{maskedCardNumber(card)}</p>
        <p className="text-body-secondary small mb-0">{t('card.expires', { expiry })}</p>
      </div>

      <span className="visually-hidden">
        {t('card.summaryLabel', { brand, last4: card.last4, expiry })}
      </span>
    </div>
  );
}
