import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Badge } from 'react-bootstrap';

import { formatMoney } from '../../../shared/format/money';
import { useLocale } from '../../../shared/hooks/useLocale';
import { Icon } from '../../../shared/ui/Icon';
import { ProductImage } from '../../../shared/ui/ProductImage';
import { adminProductPath } from '../adminRoutes';
import type { AdminProduct } from '../api/adminProductKeys';

type AdminProductRowProps = {
  product: AdminProduct;
};

/**
 * One product of the back-office table.
 *
 * There is a single action, and it is a link: everything that can be done to a product —edit it,
 * photograph it, pull it from the catalog, delete it— happens on its sheet, where each of those has
 * the room to say what it does. A row of four buttons would be four decisions taken with the product
 * reduced to a line of text.
 *
 * The badge is not decoration either: this listing is the only screen in the app that shows
 * discontinued products (RF-10), so what tells them apart has to be legible at a glance and not only
 * through the `status` filter that brought them here.
 */
export function AdminProductRow({ product }: AdminProductRowProps) {
  const { t } = useTranslation('backoffice');
  const locale = useLocale();

  return (
    <tr>
      <td>
        {/* Decoration: the link beside it already names the product, and repeating the name would
            have a screen reader read the row twice. The fallback is an icon because at this size the
            words do not fit. */}
        <ProductImage
          imageUrl={product.imageUrl}
          alt=""
          className="product-thumb"
          fallback={<Icon name="image" size="sm" />}
        />
      </td>
      <td className="font-monospace">{product.sku}</td>
      <td>
        <Link to={adminProductPath(product.id)}>{product.name}</Link>
      </td>
      <td>{product.typeName}</td>
      <td className="text-end">{formatMoney(product.price, locale)}</td>
      <td>
        {product.isActive ? (
          <Badge bg="success-subtle" text="success-emphasis">
            {t('products.status.ACTIVE')}
          </Badge>
        ) : (
          <Badge bg="secondary-subtle" text="secondary-emphasis">
            {t('products.status.DISCONTINUED')}
          </Badge>
        )}
      </td>
      <td className="text-end">
        <Link to={adminProductPath(product.id)} className="btn btn-outline-primary btn-sm">
          {t('products.table.manage')}
        </Link>
      </td>
    </tr>
  );
}
