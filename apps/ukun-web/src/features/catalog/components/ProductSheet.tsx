import { useTranslation } from 'react-i18next';
import { Col, Row } from 'react-bootstrap';
import { Link } from 'react-router-dom';

import type { StoreAvailability } from '../../../shared/api/availability';
import { formatMoney } from '../../../shared/format/money';
import { useLocale } from '../../../shared/hooks/useLocale';
import { Icon } from '../../../shared/ui/Icon';
import type { ProductDetail } from '../api/productKeys';
import { catalogByTypePath } from '../routes';
import { AddToCartButton } from './AddToCartButton';
import { ProductPhoto } from './ProductPhoto';

type ProductSheetProps = {
  product: ProductDetail;
  /** Read in parallel by the screen, not here: the sheet only exists once the product has arrived. */
  availability: StoreAvailability;
  /**
   * The customer's favourite store, when there is a session with one (RF-01). Read in parallel too,
   * and it lands later than the online one — it takes a profile to know which store it is.
   */
  favoriteAvailability?: StoreAvailability;
};

/**
 * Everything RF-01 asks for about one product, laid out as `docs/07-frontend-ux-ui.md` §"Anatomía de
 * una pantalla de ficha" describes: the photo whole on one side, and on the other the type as the way
 * out sideways, the name, the reference, the price, the stock answer and the description.
 *
 * It receives the availability instead of reading it so that the two requests actually overlap: asked
 * for here, the stock read would not start until the product had already come back.
 */
/** A store that has answered about this product. Anything else has no line on the sheet. */
type StockLine = { storeName: string; available: boolean };

function stockLineOf(source: StoreAvailability | undefined, productId: number): StockLine | undefined {
  const available = source?.available?.get(productId);

  return source?.store === undefined || available === undefined
    ? undefined
    : { storeName: source.store.name, available };
}

export function ProductSheet({ product, availability, favoriteAvailability }: ProductSheetProps) {
  const { t } = useTranslation('catalog');
  const locale = useLocale();

  // The two stores RF-01 names, in the order it names them: the online one, which every reader
  // gets, and the customer's favourite underneath. A store whose read is in flight, missing or
  // failed has no line — the favourite arrives later than the online one (it takes a profile to
  // know which store it is), so this list grows on screen instead of appearing complete.
  const stockLines = [
    stockLineOf(availability, product.id),
    stockLineOf(favoriteAvailability, product.id),
  ].filter((line) => line !== undefined);

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

        {/* The stock reads land after the sheet is already painted, so what shows up here is
            announced instead of appearing in silence.

            The failure message is the online store's alone: that is the one being bought from
            (RF-04), and a favourite store that could not be resolved leaves no trace rather than a
            second apology under the first. */}
        <div aria-live="polite" className="mb-4 d-grid gap-1">
          {availability.failed ? (
            <p className="small text-body-secondary mb-0">{t('detail.availabilityFailed')}</p>
          ) : (
            stockLines.map(({ storeName, available }) => (
              <p
                key={storeName}
                className={`availability-block${available ? '' : ' availability-block--out'} d-flex align-items-center gap-2 mb-0`}
              >
                <Icon name={available ? 'check_circle' : 'warning'} />
                {available
                  ? t('detail.availableAt', { store: storeName })
                  : t('detail.unavailableAt', { store: storeName })}
              </p>
            ))
          )}
        </div>

        {/* Adding does not wait for the stock answer: availability informs, it never blocks (RF-03).
            The button belongs above the description because it is what the reader came to do. */}
        <AddToCartButton productId={product.id} />

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
