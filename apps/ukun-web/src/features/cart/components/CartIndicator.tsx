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
 * Nothing is rendered for anyone but a **customer**, deliberately. Not having a session is the
 * obvious half — the cart requires one (RF-03), and while the probe is in flight this is the
 * anonymous case, exactly as `SessionMenu` treats it. The other half is that an employee *has* a
 * session and still has no cart (`docs/02-requisitos.md` §3, ADR-0006): a badge painted for them
 * counts a cart the server will never hand over.
 *
 * The count is units and not lines, which is what a reader expects a cart badge to say.
 */
export function CartIndicator() {
  const { t } = useTranslation('cart');
  const { hasRole } = useAuth();
  const { data: cart, isError } = useCart();

  if (!hasRole('CUSTOMER')) {
    return null;
  }

  // Una lectura que falló no es un carrito vacío. Sin esto el indicador se quedaba en cero y lo
  // anunciaba —«Carrito, 0 unidades»—, que es afirmar lo que no se sabe: el peor de los dos errores,
  // porque quien tuviera algo dentro se iba convencido de que no. El enlace se queda (es la vía de
  // entrada al carrito, y allí sí se explica el fallo con todo el espacio de la pantalla); lo que se
  // calla es la cuenta. Un error en la barra de navegación no cabe y no es donde se resuelve.
  const units = isError ? undefined : cartUnitCount(cart);

  return (
    <Link
      to={CART_PATH}
      className="nav-link d-inline-flex align-items-center gap-1"
      aria-label={units === undefined ? t('indicator.labelUnknown') : t('indicator.label', { count: units })}
    >
      <Icon name="shopping_cart" />
      {units !== undefined && units > 0 && (
        <Badge bg="light" text="dark" pill className="text-tabular-nums">
          {units}
        </Badge>
      )}
    </Link>
  );
}
