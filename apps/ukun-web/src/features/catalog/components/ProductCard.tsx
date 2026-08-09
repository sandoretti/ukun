import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Badge, Card } from 'react-bootstrap';

import { Icon } from '../../../shared/ui/Icon';
import { formatMoney } from '../../../shared/format/money';
import type { ProductSummary } from '../api/productKeys';

type ProductCardProps = {
  product: ProductSummary;
  /** `undefined` while the parallel availability read is still in flight or has failed. */
  available?: boolean;
};

export function ProductCard({ product, available }: ProductCardProps) {
  const { t, i18n } = useTranslation('catalog');
  const locale = i18n.resolvedLanguage ?? 'es';

  const titleId = `product-${product.id}-title`;
  // Whether the photo actually loads is a property of this render, not of the product: a URL that
  // 404s (nothing serves `/media/**` until the storage adapter exists) falls back to the same
  // placeholder as a product with no photo, instead of leaving a broken image in the grid.
  const [imageFailed, setImageFailed] = useState(false);
  const showImage = product.imageUrl !== null && !imageFailed;

  return (
    // An `article` named by its own title: the grid becomes a list of named regions for a screen
    // reader instead of an undifferentiated pile of divs.
    <Card as="article" aria-labelledby={titleId} className="h-100">
      {showImage ? (
        <Card.Img
          variant="top"
          src={product.imageUrl!}
          alt={product.name}
          onError={() => setImageFailed(true)}
        />
      ) : (
        // The photo is optional (RF-10) and the URL always comes from the server: there is no
        // identifier-to-path convention to rebuild here.
        <div className="d-flex align-items-center justify-content-center bg-light text-muted py-5">
          {t('product.noImage')}
        </div>
      )}
      <Card.Body className="d-flex flex-column">
        <Card.Title as="h3" id={titleId} className="h6 text-truncate-2">
          {product.name}
        </Card.Title>
        <div className="mb-2">
          <Badge bg="secondary">{product.typeName}</Badge>
        </div>
        <p className="text-muted small mb-2">{t('product.sku', { sku: product.sku })}</p>
        <p className="fs-5 fw-semibold text-tabular-nums mb-2">{formatMoney(product.price, locale)}</p>
        <div className="mt-auto">
          {available === undefined ? null : available ? (
            <span className="text-success d-inline-flex align-items-center gap-1">
              <Icon name="check_circle" size="sm" />
              {t('availability.available')}
            </span>
          ) : (
            <span className="text-muted d-inline-flex align-items-center gap-1">
              <Icon name="error" size="sm" />
              {t('availability.unavailable')}
            </span>
          )}
        </div>
      </Card.Body>
    </Card>
  );
}
