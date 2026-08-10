import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Card } from 'react-bootstrap';
import { Link } from 'react-router-dom';

import { formatMoney } from '../../../shared/format/money';
import { useLocale } from '../../../shared/hooks/useLocale';
import type { ProductSummary } from '../api/productKeys';

type ProductCardProps = {
  product: ProductSummary;
  /** `undefined` while the parallel availability read is still in flight or has failed. */
  available?: boolean;
};

export function ProductCard({ product, available }: ProductCardProps) {
  const { t } = useTranslation('catalog');
  const locale = useLocale();

  const titleId = `product-${product.id}-title`;
  // Whether the photo actually loads is a property of this render, not of the product: a URL that
  // 404s falls back to the same placeholder as a product with no photo, instead of leaving a broken
  // image in the grid.
  const [imageFailed, setImageFailed] = useState(false);
  const showImage = product.imageUrl !== null && !imageFailed;
  // Out of stock steps back visually instead of announcing itself with a banner; what says so is
  // the line under the price, which is also what a screen reader reads.
  const mediaClass = `product-media${available === false ? ' product-media--dimmed' : ''}`;

  return (
    // The whole card is the link to the detail sheet, which is what makes the sheet reachable at
    // all. `h-100` has to travel up here too: left on the card alone it would measure against the
    // link's automatic height and the grid would lose its equal rows. The accessible name comes
    // from the title, so tabbing through the grid announces "Sofá de cuero" and not the card read
    // out whole.
    <Link
      to={`/products/${product.id}`}
      aria-labelledby={titleId}
      className="product-card-link d-block h-100 text-reset text-decoration-none"
    >
      {/* An `article` named by its own title: the grid becomes a list of named regions for a screen
          reader instead of an undifferentiated pile of divs.
          A plain card box, and `overflow-hidden` so the photo respects its rounded corners: a border
          is what separates one product from the next in a grid with no gaps of its own. */}
      <Card as="article" aria-labelledby={titleId} className="h-100 overflow-hidden">
        {showImage ? (
          <Card.Img
            variant="top"
            className={mediaClass}
            src={product.imageUrl!}
            alt={product.name}
            onError={() => setImageFailed(true)}
          />
        ) : (
          // The photo is optional (RF-10) and the URL always comes from the server: there is no
          // identifier-to-path convention to rebuild here.
          <div
            className={`${mediaClass} d-flex align-items-center justify-content-center text-body-secondary small`}
          >
            {t('product.noImage')}
          </div>
        )}
        <Card.Body className="d-flex flex-column">
          <p className="overline mb-1">{product.typeName}</p>
          <Card.Title as="h3" id={titleId} className="h6 text-truncate-2 mb-2">
            {product.name}
          </Card.Title>
          <p className="fs-5 fw-semibold text-tabular-nums mb-1">{formatMoney(product.price, locale)}</p>
          <div className="mt-auto">
            {available !== undefined && (
              <p className="small text-body-secondary d-flex align-items-center gap-2 mb-0">
                <span className={`status-dot status-dot--${available ? 'in' : 'out'}`} aria-hidden="true" />
                {available ? t('availability.available') : t('availability.unavailable')}
              </p>
            )}
          </div>
        </Card.Body>
      </Card>
    </Link>
  );
}
