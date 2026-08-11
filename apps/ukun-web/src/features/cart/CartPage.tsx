import { useTranslation } from 'react-i18next';
import { Col, Container, Row } from 'react-bootstrap';

import { useCart, usePutCartItem } from '../../shared/api/cart';
import { ErrorState } from '../../shared/ui/ErrorState';
import { QueryBoundary } from '../../shared/ui/QueryBoundary';
import { useClearCart, useRemoveCartItem } from './api/queries';
import { CartLineRow } from './components/CartLineRow';
import { CartSummary } from './components/CartSummary';
import { ClearCartButton } from './components/ClearCartButton';

/**
 * The cart (RF-03), at `/cart` and behind `RequireAuth` — every operation of this contract requires
 * a session, and the cart is resolved server-side from `account_id`: no customer identifier travels
 * in any of these requests (`docs/08-identidad-y-seguridad.md` §4).
 *
 * **Two columns from `lg`**: what is edited on the left, what it adds up to on the right, stacked in
 * that order below. It is the same split `CatalogPage` already makes with its filter sidebar, and
 * the summary is sticky from `lg` for the same reason the sidebar is — a long list should not leave
 * the total behind.
 *
 * The four states are the boundary's, over the **lines** and not over the cart: an empty cart is a
 * perfectly valid `Cart` (`{items: [], total: "0.00"}`, never a `404`), so handing the whole object
 * to a resource boundary would paint an empty table as a success. It is also not an error — it is
 * `EmptyState`, which is the example `docs/07-frontend-ux-ui.md` §Estados gives by name.
 *
 * **A failed write never wipes the cart.** The rule inside `QueryBoundary` — the error state only
 * takes over when there is nothing to show — extends to the mutations here: their failure is told
 * above the lines, by `code`, while everything that was on screen stays on screen.
 *
 * Emptying the cart lives in the header of the list and not in the panel: the panel is where the
 * purchase moves forward, and putting a destructive button next to it invites the wrong click.
 */
export default function CartPage() {
  const { t } = useTranslation('cart');

  const cartQuery = useCart();
  const putItem = usePutCartItem();
  const removeItem = useRemoveCartItem();
  const clearCart = useClearCart();

  const cart = cartQuery.data;
  const hasLines = cart !== undefined && cart.items.length > 0;

  // Only the line being written to is frozen, so a slow request does not lock the whole cart.
  const busyProductId = putItem.isPending
    ? putItem.variables?.productId
    : removeItem.isPending
      ? removeItem.variables
      : undefined;

  const actionError = putItem.error ?? removeItem.error ?? clearCart.error;

  /**
   * What is reported is the **last** action's failure, not any failure that ever happened: the three
   * mutations keep their own error until they are used again, so removing a line successfully after
   * a quantity change failed would leave the older message standing over a cart that is now right.
   */
  const forgetPreviousFailure = () => {
    putItem.reset();
    removeItem.reset();
    clearCart.reset();
  };

  return (
    <Container className="py-5">
      <Row className="g-4 g-lg-5">
        <Col lg={8}>
          <div className="d-flex flex-wrap justify-content-between align-items-start gap-3 mb-4">
            <div>
              <h1 className="h2 mb-2">{t('title')}</h1>
              <p className="text-body-secondary mb-0">{t('lead')}</p>
            </div>
            {hasLines && (
              <ClearCartButton
                busy={clearCart.isPending}
                onConfirm={() => {
                  forgetPreviousFailure();
                  clearCart.mutate();
                }}
              />
            )}
          </div>

          {/* What failed while writing shows up here, next to what is still there. */}
          <div aria-live="polite">{actionError !== null && <ErrorState error={actionError} />}</div>

          <QueryBoundary query={cartQuery} data={cart?.items} empty={t('empty')}>
            {(lines) => (
              <ul className="cart-lines list-unstyled mb-0">
                {lines.map((line) => (
                  <li key={line.productId}>
                    <CartLineRow
                      line={line}
                      busy={busyProductId === line.productId || clearCart.isPending}
                      onQuantityChange={(quantity) => {
                        forgetPreviousFailure();
                        putItem.mutate({ productId: line.productId, quantity });
                      }}
                      onRemove={() => {
                        forgetPreviousFailure();
                        removeItem.mutate(line.productId);
                      }}
                    />
                  </li>
                ))}
              </ul>
            )}
          </QueryBoundary>
        </Col>

        {/* Outside the boundary, because the total belongs to the cart and not to its lines: the
            boundary hands over the list it painted, and there is nothing to summarise when it is
            empty. */}
        <Col lg={4}>{hasLines && <CartSummary cart={cart} />}</Col>
      </Row>
    </Container>
  );
}
