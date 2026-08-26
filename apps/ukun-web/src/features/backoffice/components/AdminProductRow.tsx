import type { MouseEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router-dom';
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
 * One product of the back-office table. **The whole row opens it**, which is what a table of things
 * you go into wants: a row that is one destination should not need a column of buttons repeating it.
 *
 * The photo, the name and the SKU share one cell, in two lines. They are three ways of naming the
 * same product, and reading them together is how somebody with a label in their hand finds the row —
 * far better than three columns that push the numbers off to the right and make the eye cross the
 * whole table to check it is on the line it thinks it is.
 *
 * The name is still a real `<Link>`, only dressed as plain text. That is deliberate and it is the
 * part that is easy to lose: a `<tr onClick>` on its own cannot be reached with the keyboard, has no
 * address to copy, and does not open in a new tab with ctrl-click. The link is what keeps all three;
 * the row click is the convenience on top.
 */
export function AdminProductRow({ product }: AdminProductRowProps) {
  const { t } = useTranslation('backoffice');
  const locale = useLocale();
  const navigate = useNavigate();

  const path = adminProductPath(product.id);

  const open = (event: MouseEvent<HTMLTableRowElement>) => {
    // The link inside did it already, and doing it twice would leave two entries in the history.
    if (event.defaultPrevented || (event.target as HTMLElement).closest('a, button')) {
      return;
    }
    // Selecting a SKU to copy it ends in a click too, and that must not take anybody anywhere.
    if ((window.getSelection()?.toString() ?? '') !== '') {
      return;
    }

    void navigate(path);
  };

  return (
    <tr onClick={open} className="admin-product-row">
      <td>
        <div className="d-flex align-items-center gap-3">
          {/* Decoration: the name beside it already says which product this is, and repeating it
              would have a screen reader read the row twice. The fallback is an icon because at this
              size the words do not fit. */}
          <ProductImage
            imageUrl={product.imageUrl}
            alt=""
            className="admin-product-row__thumb"
            fallback={<Icon name="image" size="sm" />}
          />

          <div className="min-w-0">
            <Link to={path} className="admin-product-row__name">
              {product.name}
            </Link>
            <p className="admin-product-row__sku mb-0">{product.sku}</p>
          </div>
        </div>
      </td>
      <td className="text-body-secondary">{product.typeName}</td>
      <td>
        {product.isActive ? (
          <Badge bg="success-subtle" text="success-emphasis" className="fw-normal">
            {t('products.status.ACTIVE')}
          </Badge>
        ) : (
          <Badge bg="secondary-subtle" text="secondary-emphasis" className="fw-normal">
            {t('products.status.DISCONTINUED')}
          </Badge>
        )}
      </td>
      <td className="admin-table__price text-end">{formatMoney(product.price, locale)}</td>
    </tr>
  );
}
