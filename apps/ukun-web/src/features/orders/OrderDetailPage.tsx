import { useTranslation } from 'react-i18next';
import { Link, useLocation, useParams } from 'react-router-dom';
import { Alert, Badge, Col, Container, Row } from 'react-bootstrap';

import { formatDateTime } from '../../shared/format/date';
import { useLocale } from '../../shared/hooks/useLocale';
import { ORDERS_PATH } from '../../shared/routes';
import { Icon } from '../../shared/ui/Icon';
import { PaymentMethodCard } from '../../shared/ui/PaymentMethodCard';
import { ResourceBoundary } from '../../shared/ui/ResourceBoundary';
import { useOrder } from './api/queries';
import { AddressBlock } from './components/AddressBlock';
import { OrderLines } from './components/OrderLines';

/**
 * One order at `/orders/{id}`, behind `RequireAuth`. It is the screen a fresh order lands on —where
 * the `Location` header of `POST /api/orders` points— and the one the history opens, which is why it
 * is a single screen with two ways in and not two.
 *
 * **The id identifies the order; it does not authorise reading it.** Ownership is resolved
 * server-side from `account_id`, and someone else's order answers `404`
 * (`docs/08-identidad-y-seguridad.md` §4) — which is a not-found state here, not a special case.
 *
 * The confirmation banner comes from the history entry the checkout wrote (`state.justPlaced`), so
 * reloading this URL later shows the order without pretending it was just placed.
 */
export default function OrderDetailPage() {
  const { t } = useTranslation('orders');
  const locale = useLocale();
  const { id } = useParams();
  const location = useLocation();

  // The route pattern lets anything through; what is not a number is simply not an order.
  const orderId = Number(id);
  const justPlaced = (location.state as { justPlaced?: boolean } | null)?.justPlaced === true;

  const orderQuery = useOrder(orderId);

  return (
    <Container className="py-5">
      <Row>
        <Col lg={9} xl={8}>
          <Link to={ORDERS_PATH} className="link-secondary d-inline-flex align-items-center gap-2 mb-3">
            <Icon name="arrow_back" size="sm" />
            {t('detail.back')}
          </Link>

          {justPlaced && (
            <Alert variant="success">
              <Alert.Heading as="h2" className="h5">
                {t('detail.confirmed')}
              </Alert.Heading>
              <p className="mb-0">{t('detail.confirmedLead')}</p>
            </Alert>
          )}

          <ResourceBoundary query={orderQuery} data={orderQuery.data} empty={t('detail.empty')}>
            {(order) => (
              <>
                <div className="d-flex flex-wrap justify-content-between align-items-baseline gap-2 mb-2">
                  <h1 className="h2 mb-0">{t('card.number', { id: order.id })}</h1>
                  <Badge bg="success-subtle" text="success-emphasis" className="fw-normal">
                    {t(`status.${order.status}`)}
                  </Badge>
                </div>
                <p className="text-body-secondary mb-4">
                  {t('detail.placedAt', { date: formatDateTime(order.placedAt, locale) })}
                </p>

                <div className="mb-4">
                  <OrderLines order={order} />
                </div>

                <Row className="g-4">
                  <Col md={6}>
                    <AddressBlock title={t('shipping.title')} address={order.shippingAddress} />
                  </Col>
                  <Col md={6}>
                    <h2 className="h6 text-body-secondary mb-2">{t('payment.title')}</h2>
                    <PaymentMethodCard card={order.payment} />
                  </Col>
                </Row>

                {/* The sentence that says what ADR-0011 bought: these are snapshots, and editing the
                    profile does not rewrite them — which is exactly what the legacy did (defect #19). */}
                <p className="small text-body-secondary mt-4 mb-0">{t('snapshotNotice')}</p>
              </>
            )}
          </ResourceBoundary>
        </Col>
      </Row>
    </Container>
  );
}
