import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Button } from 'react-bootstrap';

import { cartLineOf, useCart, usePutCartItem } from '../../../shared/api/cart';
import { useAuth } from '../../../shared/hooks/useAuth';
import { CART_PATH } from '../../../shared/routes';
import { ErrorState } from '../../../shared/ui/ErrorState';
import { Icon } from '../../../shared/ui/Icon';

type AddToCartButtonProps = {
  productId: number;
};

/**
 * The way a product gets into the cart, on the sheet where it is being read.
 *
 * It lives in `features/catalog` and reads the cart from `shared/api/cart.ts`, which is the same
 * shape `ProductSheet` already has with `useOnlineAvailability`: the hook of another module travels
 * through `shared/`, the component belongs to the screen using it. Features never import each other.
 *
 * **It sends a total, not an increment**: there is one line per `(customer, product)` (RF-03) and
 * the endpoint is an upsert, so adding to a line of two means asking for three. Hence the read of
 * the cart before writing — and hence a second click being harmless if the answer is slow, because
 * the button waits for it.
 *
 * With no session it goes to sign in instead of firing a request that can only answer `401`. What
 * comes back lands on this same sheet: `signIn` already remembers the route.
 */
export function AddToCartButton({ productId }: AddToCartButtonProps) {
  const { t } = useTranslation('cart');
  const { isAuthenticated, signIn } = useAuth();
  const { data: cart } = useCart();
  const putItem = usePutCartItem();

  const inCart = cartLineOf(cart, productId)?.quantity ?? 0;

  const add = () => {
    if (isAuthenticated) {
      putItem.mutate({ productId, quantity: inCart + 1 });
    } else {
      signIn();
    }
  };

  return (
    <div className="mb-4">
      <Button
        variant="primary"
        size="lg"
        className="d-inline-flex align-items-center gap-2"
        disabled={putItem.isPending}
        onClick={add}
      >
        <Icon name="add_shopping_cart" />
        {t('actions.add')}
      </Button>

      {/* Both the confirmation and the failure land after the click, so they are announced. */}
      <div aria-live="polite" className="mt-2">
        {putItem.isError ? (
          <ErrorState error={putItem.error} />
        ) : (
          inCart > 0 && (
            <p className="small text-body-secondary mb-0">
              {t('actions.inCart', { count: inCart })}{' '}
              <Link to={CART_PATH}>{t('actions.goToCart')}</Link>
            </p>
          )
        )}
      </div>
    </div>
  );
}
