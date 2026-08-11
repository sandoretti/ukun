import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Badge } from 'react-bootstrap';

import { cartUnitCount, useCart } from '../../../shared/api/cart';
import { useAuth } from '../../../shared/hooks/useAuth';
import { Icon } from '../../../shared/ui/Icon';
import { CART_PATH } from '../../../shared/routes';

/**
 * The cart in the navigation bar: the way in from anywhere, and how many units are waiting.
 *
 * It is composed into the bar from `AppLayout` next to the account block, and for the same reason —
 * `shared/ui/SiteHeader` cannot import a feature, and the bar knows nothing about who is looking at
 * it.
 *
 * Nothing is rendered without a session, deliberately: the cart requires one (RF-03), and while the
 * session probe is in flight this is the anonymous case, exactly as `SessionMenu` treats it. The
 * count is units and not lines, which is what a reader expects a cart badge to say.
 */
export function CartIndicator() {
  const { t } = useTranslation('cart');
  const { isAuthenticated } = useAuth();
  const { data: cart } = useCart();

  if (!isAuthenticated) {
    return null;
  }

  const units = cartUnitCount(cart);

  return (
    <Link
      to={CART_PATH}
      className="nav-link d-inline-flex align-items-center gap-1"
      aria-label={t('indicator.label', { count: units })}
    >
      <Icon name="shopping_cart" />
      {units > 0 && (
        <Badge bg="light" text="dark" pill className="text-tabular-nums">
          {units}
        </Badge>
      )}
    </Link>
  );
}
