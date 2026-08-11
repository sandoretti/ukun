import { useTranslation } from 'react-i18next';

import type { Cart } from '../../../shared/api/cart';
import { formatMoney } from '../../../shared/format/money';
import { useLocale } from '../../../shared/hooks/useLocale';
import { ComingSoonButton } from '../../../shared/ui/ComingSoon';
import { ClearCartButton } from './ClearCartButton';

type CartSummaryProps = {
  /** The server's total. Never the sum of the lines done here — money is not added in the browser. */
  total: Cart['total'];
  busy: boolean;
  onClear: () => void;
};

/**
 * What closes the cart: the way to empty it on one side, what it adds up to and the way forward on
 * the other.
 *
 * «Place order» is a disabled button and not a link: checkout is issue #35 and its route does not
 * exist yet, and this repo never points an `<a>` at a screen that is not there (`ComingSoon`). When
 * that slice lands, this is one line to change.
 */
export function CartSummary({ total, busy, onClear }: CartSummaryProps) {
  const { t } = useTranslation('cart');
  const locale = useLocale();

  return (
    <div className="d-flex flex-wrap justify-content-between align-items-center gap-3 pt-3 border-top">
      <ClearCartButton busy={busy} onConfirm={onClear} />

      <div className="d-flex align-items-center gap-3">
        <p className="mb-0">
          <span className="text-body-secondary me-2">{t('summary.total')}</span>
          <span className="fs-4 fw-semibold text-tabular-nums">{formatMoney(total, locale)}</span>
        </p>
        <ComingSoonButton label={t('summary.checkout')} variant="outline-primary" />
      </div>
    </div>
  );
}
