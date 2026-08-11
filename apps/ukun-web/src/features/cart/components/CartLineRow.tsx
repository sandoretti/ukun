import { useTranslation } from 'react-i18next';
import { Button, Col, Row } from 'react-bootstrap';

import type { CartLine } from '../../../shared/api/cart';
import { formatMoney } from '../../../shared/format/money';
import { useLocale } from '../../../shared/hooks/useLocale';
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
 * **Availability is reported, never enforced.** The legacy system does not check stock until the
 * order is confirmed, which lets 999 units of a product with 3 into the cart; RF-03 calls that a
 * shortcoming and asks the cart to say so from the start — but the barrier stays where it belongs,
 * at order confirmation (RF-04), so the stepper keeps working past the warning. `available` reserves
 * nothing and may have moved by then.
 *
 * A product discontinued after it was added keeps its line with `available: 0`, which is why the
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
      <Col xs={12} md>
        <p className="fw-semibold mb-1">{line.productName}</p>
        <p className="small text-body-secondary mb-0">{t('line.reference', { sku: line.sku })}</p>

        {/* The warning appears and disappears as the quantity changes, after the answer lands: it is
            announced instead of showing up in silence. */}
        <div aria-live="polite">
          {short && (
            <p className="cart-line__warning small d-flex align-items-center gap-1 mb-0 mt-1">
              <Icon name="warning" size="sm" />
              {line.available === 0
                ? t('line.unavailable')
                : t('line.onlyLeft', { count: line.available })}
            </p>
          )}
        </div>
      </Col>

      <Col xs={6} md="auto" className="text-body-secondary text-tabular-nums">
        {t('line.unitPrice', { price: formatMoney(line.unitPrice, locale) })}
      </Col>

      <Col xs={6} md="auto">
        <QuantityStepper
          quantity={line.quantity}
          productName={line.productName}
          busy={busy}
          onChange={onQuantityChange}
        />
      </Col>

      <Col xs={6} md="auto" className="fw-semibold text-tabular-nums text-md-end cart-line__total">
        {formatMoney(line.lineTotal, locale)}
      </Col>

      <Col xs={6} md="auto" className="text-end">
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
