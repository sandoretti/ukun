import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router-dom';
import { Button, Col, Container, Row } from 'react-bootstrap';

import { toProblem } from '../../shared/api/http';
import { useMyProfile } from '../../shared/api/profile';
import { CART_PATH, PROFILE_PATH, orderPath } from '../../shared/routes';
import { ErrorState } from '../../shared/ui/ErrorState';
import { PaymentMethodCard } from '../../shared/ui/PaymentMethodCard';
import { ResourceBoundary } from '../../shared/ui/ResourceBoundary';
import { useCheckoutAttempt, usePlaceOrder, useReopenCheckout } from './api/queries';
import { AddressBlock } from './components/AddressBlock';
import { CheckoutLineRow } from './components/CheckoutLineRow';
import { CheckoutSummary } from './components/CheckoutSummary';

/**
 * Checkout (RF-04), at `/checkout` and behind `RequireAuth`.
 *
 * The screen opens a **checkout attempt** the moment it is painted (ADR-0016): the server freezes the
 * prices, adds up the total, reports availability line by line and hands back the `attemptId` that
 * travels as `Idempotency-Key` when the order is confirmed. Nothing here invents that key, and
 * nothing here recomputes an amount.
 *
 * Two columns from `lg`, the same split as the cart: what is being bought on the left, what it adds
 * up to and the button on the right.
 *
 * **The address and the card are the profile's current ones**, which is exactly what the order will
 * copy into its immutable snapshots (RF-04, ADR-0011). They are shown before confirming and not
 * after, because after is too late to notice the address is the old one — and the way to change them
 * is the profile, one link away.
 */
export default function CheckoutPage() {
  const { t } = useTranslation('checkout');
  const navigate = useNavigate();

  const attemptQuery = useCheckoutAttempt();
  const profileQuery = useMyProfile();
  const placeOrder = usePlaceOrder();
  const reopenCheckout = useReopenCheckout();

  const attempt = attemptQuery.data;
  const profile = profileQuery.data;

  const failure = placeOrder.error === null ? undefined : toProblem(placeOrder.error);
  // The two answers the contract resolves by opening a new attempt: the old one is gone (`410`) or
  // was already used against a different cart (`422`). Reporting them without a way out would leave
  // the reader on a screen whose only working button is the browser's back.
  const mustReopen = failure?.code === 'CHECKOUT_EXPIRED' || failure?.code === 'IDEMPOTENCY_MISMATCH';

  const confirm = () => {
    if (attempt === undefined || placeOrder.isPending) {
      return;
    }

    placeOrder.mutate(attempt.attemptId, {
      // `replace`, because the attempt behind this entry is spent: coming back to it with the back
      // button would only be able to say `CHECKOUT_EXPIRED`. The flag travels in the history entry
      // and not in the URL — "you have just placed this" is true of this visit, not of the order.
      onSuccess: (order) => navigate(orderPath(order.id), { replace: true, state: { justPlaced: true } }),
    });
  };

  const startAgain = () => {
    placeOrder.reset();
    void reopenCheckout();
  };

  return (
    <Container className="py-5">
      <Row className="g-4 g-lg-5">
        <Col lg={8}>
          <h1 className="h2 mb-2">{t('title')}</h1>
          <p className="text-body-secondary mb-4">{t('lead')}</p>

          {/* What failed while confirming, next to what is still on screen. */}
          <div aria-live="polite">
            {failure !== undefined && (
              <>
                <ErrorState error={placeOrder.error} />
                {mustReopen && (
                  <Button variant="outline-primary" className="mb-4" onClick={startAgain}>
                    {t('reopen')}
                  </Button>
                )}
              </>
            )}
          </div>

          <ResourceBoundary query={attemptQuery} data={attempt} empty={t('empty')}>
            {(openAttempt) => (
              <>
                <h2 className="h5 mb-3">{t('lines.title')}</h2>
                <ul className="order-lines list-unstyled mb-4">
                  {openAttempt.lines.map((line) => (
                    <li key={line.sku}>
                      <CheckoutLineRow line={line} />
                    </li>
                  ))}
                </ul>

                {/* The profile is a second read composed on screen: the lines are painted as soon as
                    the attempt arrives, without waiting for it. */}
                {profileQuery.isError && <ErrorState error={profileQuery.error} />}
                {profile !== undefined && (
                  <Row className="g-4">
                    <Col md={6}>
                      <AddressBlock title={t('shipping.title')} address={profile.address} />
                    </Col>
                    <Col md={6}>
                      <h2 className="h6 text-body-secondary mb-2">{t('payment.title')}</h2>
                      <PaymentMethodCard card={profile.card} />
                    </Col>
                    <Col xs={12}>
                      <Link to={PROFILE_PATH} className="link-secondary small">
                        {t('editProfile')}
                      </Link>
                    </Col>
                  </Row>
                )}
              </>
            )}
          </ResourceBoundary>

          {/* An empty cart answers `422 EMPTY_CART`, which the boundary already explains; what it
              cannot offer is the way out, and the way out of an empty checkout is the shop. */}
          {attempt === undefined && attemptQuery.isError && (
            <Link to={CART_PATH} className="btn btn-outline-primary">
              {t('backToCart')}
            </Link>
          )}
        </Col>

        <Col lg={4}>
          {attempt !== undefined && (
            <CheckoutSummary attempt={attempt} isPlacing={placeOrder.isPending} onConfirm={confirm} />
          )}
        </Col>
      </Row>
    </Container>
  );
}
