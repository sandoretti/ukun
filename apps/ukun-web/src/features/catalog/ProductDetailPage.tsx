import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Breadcrumb, Col, Container, Row } from 'react-bootstrap';
import { Link, useParams } from 'react-router-dom';

import { useAvailability } from '../../shared/api/availability';
import { ProblemDetailError, toProblem } from '../../shared/api/http';
import { useOnlineStore } from '../../shared/api/stores';
import { formatMoney } from '../../shared/format/money';
import { useLocale } from '../../shared/hooks/useLocale';
import { ErrorState } from '../../shared/ui/ErrorState';
import { Icon } from '../../shared/ui/Icon';
import { LoadingState } from '../../shared/ui/LoadingState';
import { useProduct } from './api/queries';
import { ProductPhoto } from './components/ProductPhoto';
import { RelatedProducts } from './components/RelatedProducts';

const CATALOG_PATH = '/products';

/**
 * An identifier that is not a positive integer never existed as a product, so it is answered like a
 * product that no longer exists instead of being sent to the API to be rejected there.
 */
const NOT_FOUND = new ProblemDetailError({ code: 'RESOURCE_NOT_FOUND', status: 404 }, 404);

function toProductId(param: string | undefined): number | undefined {
  const id = Number(param);
  return Number.isInteger(id) && id > 0 ? id : undefined;
}

/**
 * The detail sheet of one product (RF-01): name, price, description, photo, type and SKU — the SKU
 * belongs here and not on the card of the listing (`docs/07-frontend-ux-ui.md`).
 *
 * Two reads, fired in parallel and composed on screen, because `catalog` cannot depend on
 * `inventory` (ADR-0017 §3): the product itself, and its availability in the online store. They fail
 * independently on purpose — a stock service that is down leaves a note under the price, not a
 * screen the reader cannot get past.
 */
export default function ProductDetailPage() {
  const { t } = useTranslation('catalog');
  const locale = useLocale();
  const { id } = useParams();
  const productId = toProductId(id);

  const productQuery = useProduct(productId);
  const onlineStoreQuery = useOnlineStore();
  // The availability endpoint takes a set; here the set is one product, and it comes back with an
  // entry for it whether or not there is a stock row (RF-01).
  const productIds = useMemo(() => (productId === undefined ? [] : [productId]), [productId]);
  const availabilityQuery = useAvailability(onlineStoreQuery.data?.id, productIds);

  const availabilityFailed = onlineStoreQuery.isError || availabilityQuery.isError;
  // Both come from the same pair of reads, so by the time there is an answer there is also a name:
  // the availability of the online store cannot be known without having resolved which one it is.
  const storeName = onlineStoreQuery.data?.name;
  const available =
    productId === undefined || storeName === undefined
      ? undefined
      : availabilityQuery.data?.get(productId);

  /** A failure with no way out is not a comprehensible screen: the catalog is one click away. */
  const deadEndWithExit = (error: unknown) => (
    <>
      <ErrorState error={error} />
      <Link to={CATALOG_PATH} className="btn btn-primary">
        {t('detail.backToCatalog')}
      </Link>
    </>
  );

  const renderBody = () => {
    if (productId === undefined) {
      return deadEndWithExit(NOT_FOUND);
    }
    if (productQuery.isPending) {
      return <LoadingState />;
    }
    if (productQuery.isError) {
      const problem = toProblem(productQuery.error);
      // A discontinued product (ADR-0013) is a 404 with its own message, never the generic "something
      // failed on our side": the reader has to be able to tell "this is gone" from "try again".
      return problem.code === 'RESOURCE_NOT_FOUND' ? (
        deadEndWithExit(problem)
      ) : (
        <ErrorState error={problem} />
      );
    }

    const product = productQuery.data;

    return (
      <>
        <Row className="g-4 g-lg-5">
          <Col lg={6}>
            <ProductPhoto imageUrl={product.imageUrl} name={product.name} />
          </Col>

          <Col lg={6}>
            {/* The type stops being a dead line and becomes the way sideways: the listing already
                reads `typeId` from the URL, so this is its own catalogue, already filtered. */}
            <Link
              to={`/products?typeId=${product.typeId}`}
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

            <p className="fs-2 fw-semibold text-tabular-nums mb-3">
              {formatMoney(product.price, locale)}
            </p>

            {/* The second read lands after the sheet is already painted, so what shows up here is
                announced instead of appearing in silence. */}
            <div aria-live="polite" className="mb-4">
              {availabilityFailed ? (
                <p className="small text-body-secondary mb-0">{t('detail.availabilityFailed')}</p>
              ) : (
                available !== undefined && (
                  <p
                    className={`availability-block${available ? '' : ' availability-block--out'} d-flex align-items-center gap-2 mb-0`}
                  >
                    <Icon name={available ? 'check_circle' : 'warning'} />
                    {available
                      ? t('detail.availableAt', { store: storeName })
                      : t('detail.unavailableAt', { store: storeName })}
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

        <RelatedProducts typeId={product.typeId} excludeId={product.id} />
      </>
    );
  };

  return (
    <Container className="py-4">
      {/* Present in every state, error included: from a sheet that could not be loaded there has to
          be a way back that is not the browser's back button. Its last step only appears once the
          name is known. */}
      <Breadcrumb label={t('detail.breadcrumb')}>
        <Breadcrumb.Item linkAs={Link} linkProps={{ to: CATALOG_PATH }}>
          {t('title')}
        </Breadcrumb.Item>
        {productQuery.data !== undefined && (
          <Breadcrumb.Item active>{productQuery.data.name}</Breadcrumb.Item>
        )}
      </Breadcrumb>

      {renderBody()}
    </Container>
  );
}
