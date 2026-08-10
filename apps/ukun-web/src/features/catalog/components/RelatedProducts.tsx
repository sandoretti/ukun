import { useId, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Col, Row } from 'react-bootstrap';

import { useOnlineAvailability } from '../../../shared/api/availability';
import { useRelatedProducts } from '../api/queries';
import { ProductCard } from './ProductCard';

type RelatedProductsProps = {
  typeId: number;
  /** The product being looked at: it is in its own type's listing and would show up as its own neighbour. */
  excludeId: number;
};

/**
 * The other products of the same type, at the foot of the sheet — the strip Amazon and IKEA put there
 * so a sheet is not a dead end when this particular sofa is not the one.
 *
 * It is secondary content and behaves like it: if the read fails, or the type has nothing else in it,
 * the whole section disappears. An error message about "you may also like" is noise on top of a sheet
 * that is doing its job perfectly well.
 */
export function RelatedProducts({ typeId, excludeId }: RelatedProductsProps) {
  const { t } = useTranslation('catalog');
  const titleId = useId();

  const relatedQuery = useRelatedProducts(typeId, excludeId);

  const products = relatedQuery.data ?? [];
  const productIds = useMemo(
    () => (relatedQuery.data ?? []).map((product) => product.id),
    [relatedQuery.data],
  );
  // Same reading as the listing's, so a card in the strip says the same as that card in the grid.
  const availability = useOnlineAvailability(productIds);

  if (products.length === 0) {
    return null;
  }

  return (
    <section className="mt-5" aria-labelledby={titleId}>
      <h2 id={titleId} className="h5 mb-3">
        {t('detail.related')}
      </h2>
      <Row xs={2} md={3} xl={4} className="g-4">
        {products.map((product) => (
          <Col key={product.id}>
            <ProductCard product={product} available={availability.available?.get(product.id)} />
          </Col>
        ))}
      </Row>
    </section>
  );
}
