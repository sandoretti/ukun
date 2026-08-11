import { useTranslation } from 'react-i18next';

import { cartUnitCount, type Cart } from '../../../shared/api/cart';
import { formatMoney } from '../../../shared/format/money';
import { useLocale } from '../../../shared/hooks/useLocale';
import { ComingSoonButton } from '../../../shared/ui/ComingSoon';
import { StockWarning } from '../../../shared/ui/StockWarning';

type CartSummaryProps = {
  /**
   * The whole cart and not just its total: what a summary says — how many units, how much, whether
   * anything is short of stock — is derived from the same object the screen already has.
   */
  cart: Cart;
};

/**
 * What the cart adds up to, as a panel of its own beside the lines.
 *
 * **Every figure here comes from the server.** The amount is `cart.total` as it arrived, printed
 * twice because it is the only line item this contract has: there is no VAT to break out, no
 * shipping, no discount. Inventing those rows —or a "free delivery over 50 €" band— would be
 * exactly the made-up claim `docs/07-frontend-ux-ui.md` forbids: no data in the contract, no
 * sentence on the screen. The unit count is the one thing computed here, and counting is not
 * money arithmetic.
 *
 * The availability notice is the aggregate of what each line already says on its own, so that the
 * reader who scrolled past it still learns before pressing the button. It reports, it does not
 * block: what validates stock is confirming the order (RF-04).
 */
export function CartSummary({ cart }: CartSummaryProps) {
  const { t } = useTranslation('cart');
  const locale = useLocale();

  const units = cartUnitCount(cart);
  const total = formatMoney(cart.total, locale);
  const short = cart.items.some((line) => line.quantity > line.available);

  return (
    <aside className="cart-summary" aria-labelledby="cart-summary-title">
      <h2 id="cart-summary-title" className="h5 mb-3">
        {t('summary.title')}
      </h2>

      {/* Pairs of label and amount are a description list, not two spans in a flex row. */}
      <dl className="cart-summary__lines mb-0">
        <div className="d-flex justify-content-between align-items-baseline gap-3">
          <dt className="fw-normal text-body-secondary">{t('summary.items', { count: units })}</dt>
          <dd className="mb-0 text-tabular-nums">{total}</dd>
        </div>

        <hr className="my-3" />

        <div className="d-flex justify-content-between align-items-baseline gap-3">
          <dt className="fw-semibold">{t('summary.total')}</dt>
          <dd className="mb-0 fs-4 fw-semibold text-tabular-nums">{total}</dd>
        </div>
      </dl>

      {/* Appears and disappears as quantities change, so it is announced rather than silent. */}
      {/* El margen lo pone el CSS solo cuando hay aviso: en un `div` siempre presente, un `mt-3`
          aquí separaría el botón del total también cuando no hay nada que decir. */}
      <div className="cart-summary__notice" aria-live="polite">
        {short && <StockWarning>{t('summary.stockNotice')}</StockWarning>}
      </div>

      <div className="d-grid mt-4">
        <ComingSoonButton label={t('summary.checkout')} variant="outline-primary" />
      </div>
    </aside>
  );
}
