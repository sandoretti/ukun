import { useTranslation } from 'react-i18next';
import { Button, Spinner } from 'react-bootstrap';
import { Link } from 'react-router-dom';

import { formatDateTime } from '../../../shared/format/date';
import { formatMoney } from '../../../shared/format/money';
import { useLocale } from '../../../shared/hooks/useLocale';
import { CART_PATH } from '../../../shared/routes';
import { StockWarning } from '../../../shared/ui/StockWarning';
import type { CheckoutAttempt } from '../api/orderKeys';

type CheckoutSummaryProps = {
  attempt: CheckoutAttempt;
  /** Whether the confirmation is in flight, which is what the button says and why it is disabled. */
  isPlacing: boolean;
  onConfirm: () => void;
};

/**
 * What the purchase adds up to, and the button that confirms it.
 *
 * **Every figure comes from the attempt**, which is where the server froze the prices and computed
 * the total (ADR-0016): nothing is added up in the browser, and there is no VAT row, no shipping row
 * and no discount row because the contract has no such fields — inventing them is the made-up claim
 * `docs/07-frontend-ux-ui.md` forbids.
 *
 * The button is disabled while the request is in flight. That is a plain UX improvement and
 * **not** what stops a duplicate order: it does not cover a network retry after a timeout, an F5 on
 * this screen, or the back button. What covers those is the `Idempotency-Key` the server issued and
 * validates (ADR-0016) — this is defence in depth on top of it.
 */
export function CheckoutSummary({ attempt, isPlacing, onConfirm }: CheckoutSummaryProps) {
  const { t } = useTranslation('checkout');
  const locale = useLocale();

  const total = formatMoney(attempt.total, locale);
  const short = attempt.lines.some((line) => line.quantity > line.available);

  return (
    <aside className="order-panel" aria-labelledby="checkout-summary-title">
      <h2 id="checkout-summary-title" className="h5 mb-3">
        {t('summary.title')}
      </h2>

      <dl className="mb-0">
        <div className="d-flex justify-content-between align-items-baseline gap-3">
          <dt className="fw-semibold">{t('summary.total')}</dt>
          <dd className="mb-0 fs-4 fw-semibold text-tabular-nums">{total}</dd>
        </div>
      </dl>

      <p className="small text-body-secondary mt-3 mb-0">
        {t('summary.priceNotice')} {t('summary.validUntil', { time: formatDateTime(attempt.expiresAt, locale) })}
      </p>

      <div className="order-panel__notice" aria-live="polite">
        {short && <StockWarning>{t('summary.stockNotice')}</StockWarning>}
      </div>

      <div className="d-grid mt-4">
        <Button type="button" variant="primary" size="lg" disabled={isPlacing} onClick={onConfirm}>
          {isPlacing && <Spinner as="span" animation="border" size="sm" className="me-2" aria-hidden="true" />}
          {isPlacing ? t('confirming') : t('confirm')}
        </Button>
      </div>

      {/* Editing what is being bought happens in the cart, which is where the quantities live. */}
      <div className="text-center mt-3">
        <Link to={CART_PATH} className="link-secondary small">
          {t('backToCart')}
        </Link>
      </div>
    </aside>
  );
}
