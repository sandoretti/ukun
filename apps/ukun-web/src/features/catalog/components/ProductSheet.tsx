import { useTranslation } from 'react-i18next';
import { Col, Row } from 'react-bootstrap';
import { Link } from 'react-router-dom';

import type { OnlineAvailability } from '../../../shared/api/availability';
import { formatMoney } from '../../../shared/format/money';
import { useLocale } from '../../../shared/hooks/useLocale';
import { Icon } from '../../../shared/ui/Icon';
import type { ProductDetail } from '../api/productKeys';
import { catalogByTypePath } from '../routes';
import { ProductPhoto } from './ProductPhoto';

type ProductSheetProps = {
  product: ProductDetail;
  /** Read in parallel by the screen, not here: the sheet only exists once the product has arrived. */
  availability: OnlineAvailability;
};

/**
 * Everything RF-01 asks for about one product, laid out as `docs/07-frontend-ux-ui.md` §"Anatomía de
 * una pantalla de ficha" describes: the photo whole on one side, and on the other the type as the way
 * out sideways, the name, the reference, the price, the stock answer and the description.
 *
 * It receives the availability instead of reading it so that the two requests actually overlap: asked
 * for here, the stock read would not start until the product had already come back.
 */
export function ProductSheet({ product, availability }: ProductSheetProps) {
  const { t } = useTranslation('catalog');
  const locale = useLocale();

  const store = availability.store;
  const available = availability.available?.get(product.id);

  return (
    <Row className="g-4 g-lg-5">
      <Col lg={6}>
        <ProductPhoto imageUrl={product.imageUrl} name={product.name} />
      </Col>

      <Col lg={6}>
        {/* The type stops being a dead line and becomes the way sideways: the listing already reads
            `typeId` from the URL, so this is its own catalogue, already filtered. */}
        <Link
          to={catalogByTypePath(product.typeId)}
          className="btn btn-light btn-sm rounded-pill border d-inline-flex align-items-center gap-1 mb-3"
          aria-label={t('detail.seeAllOfType', { type: product.typeName })}
        >
          {product.typeName}
          <Icon name="arrow_forward" size="sm" />
        </Link>

        <h1 className="h2 mb-1">{product.name}</h1>
        {/* The business identifier (ADR-0015) is what a customer quotes when asking about the
            product, not the headline of the screen: it belongs here, small and grey. */}
        <p className="small text-body-secondary mb-3">{t('detail.reference', { sku: product.sku })}</p>

        <p className="fs-2 fw-semibold text-tabular-nums mb-3">{formatMoney(product.price, locale)}</p>

        {/* The stock read lands after the sheet is already painted, so what shows up here is
            announced instead of appearing in silence. */}
        <div aria-live="polite" className="mb-4">
          {availability.failed ? (
            <p className="small text-body-secondary mb-0">{t('detail.availabilityFailed')}</p>
          ) : (
            available !== undefined &&
            store !== undefined && (
              <p
                className={`availability-block${available ? '' : ' availability-block--out'} d-flex align-items-center gap-2 mb-0`}
              >
                <Icon name={available ? 'check_circle' : 'warning'} />
                {available
                  ? t('detail.availableAt', { store: store.name })
                  : t('detail.unavailableAt', { store: store.name })}
              </p>
            )
          )}
        </div>

        <h2 className="h5">{t('detail.description')}</h2>
        {product.description === null ? (
          <p className="text-body-secondary mb-0">{t('detail.noDescription')}</p>
        ) : (
          <p className="product-description mb-0">{product.description}</p>
        )}
      </Col>
    </Row>
  );
}
