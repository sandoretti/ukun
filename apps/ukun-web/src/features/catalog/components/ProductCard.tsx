import { useTranslation } from 'react-i18next';
import { Card } from 'react-bootstrap';
import { Link } from 'react-router-dom';

import { formatMoney } from '../../../shared/format/money';
import { useLocale } from '../../../shared/hooks/useLocale';
import { ProductImage } from '../../../shared/ui/ProductImage';
import type { ProductSummary } from '../api/productKeys';
import type { StoreStock } from '../storeStock';
import { productPath } from '../../../shared/routes';

type ProductCardProps = {
  product: ProductSummary;
  online: StoreStock;
  /**
   * The customer's favourite store, when there is a session with one (RF-01). Its presence is what
   * decides whether the stock lines name their store: with a single store there is nothing to tell
   * apart, with two there is.
   */
  favorite?: StoreStock;
};

export function ProductCard({ product, online, favorite }: ProductCardProps) {
  const { t } = useTranslation('catalog');
  const locale = useLocale();

  const titleId = `product-${product.id}-title`;
  // Out of stock steps back visually instead of announcing itself with a banner; what says so is
  // the line under the price, which is also what a screen reader reads.
  //
  // It is the online store that dims the card, never the favourite: an order is only ever placed
  // against the online one (RF-04), so that is the answer that says whether this product can be
  // bought at all.
  const mediaClass = `product-media${online.available === false ? ' product-media--dimmed' : ''}`;

  // Named lines only once there are two stores to tell apart. Without a favourite the card keeps
  // the line it always had, word for word — most readers are anonymous and nothing changes for them.
  const named = favorite !== undefined;
  const stockLines = (named ? [online, favorite] : [online]).filter(
    (source) => source.available !== undefined && (!named || source.storeName !== undefined),
  );

  return (
    // The whole card is the link to the detail sheet, which is what makes the sheet reachable at
    // all. `h-100` has to travel up here too: left on the card alone it would measure against the
    // link's automatic height and the grid would lose its equal rows. The accessible name comes
    // from the title, so tabbing through the grid announces "Sofá de cuero" and not the card read
    // out whole.
    <Link
      to={productPath(product.id)}
      aria-labelledby={titleId}
      className="product-card-link d-block h-100 text-reset text-decoration-none"
    >
      {/* An `article` named by its own title: the grid becomes a list of named regions for a screen
          reader instead of an undifferentiated pile of divs.
          A plain card box, and `overflow-hidden` so the photo respects its rounded corners: a border
          is what separates one product from the next in a grid with no gaps of its own. */}
      <Card as="article" aria-labelledby={titleId} className="h-100 overflow-hidden">
        {/* `ProductImage` and not `Card.Img`: the photo, the missing photo and the one that fails to
            load are the same box, and that box is now shared with the cart line (issue #120). The
            card only says how big it is — `product-media` already carries the width, the aspect ratio
            and the crop that `card-img-top` would have contributed. */}
        <ProductImage imageUrl={product.imageUrl} alt={product.name} className={mediaClass} />
        <Card.Body className="d-flex flex-column">
          <p className="overline mb-1">{product.typeName}</p>
          <Card.Title as="h3" id={titleId} className="h6 text-truncate-2 mb-2">
            {product.name}
          </Card.Title>
          <p className="fs-5 fw-semibold text-tabular-nums mb-1">{formatMoney(product.price, locale)}</p>
          <div className="mt-auto d-grid gap-1">
            {stockLines.map(({ storeName, available }) => (
              <p
                key={storeName ?? 'online'}
                className="small text-body-secondary d-flex align-items-center gap-2 mb-0"
              >
                <span className={`status-dot status-dot--${available ? 'in' : 'out'}`} aria-hidden="true" />
                {named
                  ? t(available ? 'availability.availableAt' : 'availability.unavailableAt', {
                      store: storeName,
                    })
                  : t(available ? 'availability.available' : 'availability.unavailable')}
              </p>
            ))}
          </div>
        </Card.Body>
      </Card>
    </Link>
  );
}
