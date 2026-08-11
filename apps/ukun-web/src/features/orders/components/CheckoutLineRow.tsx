import { useTranslation } from 'react-i18next';
import { Col, Row } from 'react-bootstrap';

import { formatMoney } from '../../../shared/format/money';
import { useLocale } from '../../../shared/hooks/useLocale';
import { StockWarning } from '../../../shared/ui/StockWarning';
import type { CheckoutAttemptLine } from '../api/orderKeys';

type CheckoutLineRowProps = {
  line: CheckoutAttemptLine;
};

/**
 * One line of the attempt (ADR-0016), read-only on purpose: this screen confirms a purchase, it does
 * not edit one. Changing a quantity here would change what the attempt froze, and the way back is
 * the cart, which is one link away in the summary.
 *
 * There is no link to the product either: the attempt carries the `sku`, not the `productId` the
 * catalogue's route is built from, and inventing an id the contract did not send is how a screen
 * starts guessing.
 *
 * `available` is informational and reserves nothing (ADR-0016): a line short of stock is warned
 * about, never blocked. What validates stock is confirming the order.
 */
export function CheckoutLineRow({ line }: CheckoutLineRowProps) {
  const { t } = useTranslation('checkout');
  const locale = useLocale();

  const short = line.quantity > line.available;

  return (
    <div className="order-line">
      <Row className="align-items-center g-2">
        <Col xs={12} md={6}>
          <p className="order-line__name mb-0">{line.productName}</p>
          <p className="small text-body-secondary mb-0">{t('lines.reference', { sku: line.sku })}</p>
        </Col>

        <Col xs={6} md={3} className="text-body-secondary text-tabular-nums small text-md-center">
          {t('lines.unitPrice', { price: formatMoney(line.unitPrice, locale) })}
        </Col>

        <Col xs={6} md={3} className="text-tabular-nums text-md-end">
          {t('lines.quantity', { count: line.quantity })}
        </Col>
      </Row>

      {/* Its own band under the row, like the cart's: inside the name's column it would push the
          figures out of line with the rows above and below. */}
      <div className="order-line__notice">
        {short && <StockWarning>{t('lines.shortStock', { count: line.available })}</StockWarning>}
      </div>
    </div>
  );
}
