import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Button, Col, Row } from 'react-bootstrap';

import type { CartLine } from '../../../shared/api/cart';
import { formatMoney } from '../../../shared/format/money';
import { useLocale } from '../../../shared/hooks/useLocale';
import { productPath } from '../../../shared/routes';
import { Icon } from '../../../shared/ui/Icon';
import { QuantityStepper } from './QuantityStepper';

type CartLineRowProps = {
  line: CartLine;
  /** This line has a write in flight — its own, not another line's. */
  busy: boolean;
  onQuantityChange: (quantity: number) => void;
  onRemove: () => void;
};

/**
 * One line of the cart: what it is, what it costs, how many, and the way out.
 *
 * **The row is the same height whatever it holds**, which is a correctness matter and not a
 * cosmetic one: the quantity stepper sits inside it, and a row that grows when the availability
 * warning appears pushes the next one under the cursor mid-click. Two things guarantee it without
 * pinning a height per breakpoint — the name clamps to two lines and reserves them
 * (`docs/07-frontend-ux-ui.md` §a11y: every container decides what it does when the text does not
 * fit), and the warning lives in a slot that is reserved whether or not there is anything to say.
 *
 * The columns are fixed widths from `md` instead of `auto` so that price, quantity and subtotal
 * line up down the whole list. Below `md` they stack, which is the only honest layout at that
 * width.
 *
 * **Availability is reported, never enforced.** The legacy system does not check stock until the
 * order is confirmed, which lets 999 units of a product with 3 into the cart; RF-03 calls that a
 * shortcoming and asks the cart to say so from the start — but the barrier stays where it belongs,
 * at order confirmation (RF-04), so the stepper keeps working past the warning. A product
 * discontinued after it was added keeps its line with `available: 0`, which is why the
 * out-of-stock wording does not promise it will come back.
 *
 * Amounts come formatted by `Intl` in the active locale and travel as exact decimal strings; the
 * line total is the server's, not a multiplication done here (defect #5).
 */
export function CartLineRow({ line, busy, onQuantityChange, onRemove }: CartLineRowProps) {
  const { t } = useTranslation('cart');
  const locale = useLocale();

  const short = line.quantity > line.available;

  return (
    <Row className="cart-line align-items-center g-3 py-3">
      <Col xs={12} md={5}>
        {/* The way back to the product: the line carries its name precisely so it can be read
            again, and there is no other route to it from here that is not the search box. */}
        <Link to={productPath(line.productId)} className="cart-line__name">
          {line.productName}
        </Link>
        <p className="small text-body-secondary mb-0">{t('line.reference', { sku: line.sku })}</p>

        {/* Reserved whether or not there is a warning: this is what keeps the row from resizing
            under the stepper. Announced, because it lands after the answer does. */}
        <div className="cart-line__notice" aria-live="polite">
          {short && (
            <p className="cart-line__warning small d-flex align-items-center gap-1 mb-0">
              <Icon name="warning" size="sm" />
              {line.available === 0
                ? t('line.unavailable')
                : t('line.onlyLeft', { count: line.available })}
            </p>
          )}
        </div>
      </Col>

      <Col xs={6} md={2} className="text-body-secondary text-tabular-nums small">
        {t('line.unitPrice', { price: formatMoney(line.unitPrice, locale) })}
      </Col>

      <Col xs={6} md={2}>
        <QuantityStepper
          quantity={line.quantity}
          productName={line.productName}
          busy={busy}
          onChange={onQuantityChange}
        />
      </Col>

      <Col xs={6} md={2} className="fw-semibold text-tabular-nums text-md-end cart-line__total">
        {formatMoney(line.lineTotal, locale)}
      </Col>

      <Col xs={6} md={1} className="text-end">
        <Button
          variant="link"
          className="link-secondary p-1"
          disabled={busy}
          aria-label={t('line.remove', { product: line.productName })}
          onClick={onRemove}
        >
          <Icon name="delete" />
        </Button>
      </Col>
    </Row>
  );
}
