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
import { LoadingState } from '../../shared/ui/LoadingState';
import { useProduct } from './api/queries';
import { ProductPhoto } from './components/ProductPhoto';

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
  const available = productId === undefined ? undefined : availabilityQuery.data?.get(productId);
  const product = productQuery.data;

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

    return (
      <Row className="g-4 g-lg-5">
        <Col lg={6}>
          <ProductPhoto imageUrl={productQuery.data.imageUrl} name={productQuery.data.name} />
        </Col>

        <Col lg={6}>
          <p className="overline mb-1">{productQuery.data.typeName}</p>
          <h1 className="h2 mb-3">{productQuery.data.name}</h1>
          <p className="fs-3 fw-semibold text-tabular-nums mb-2">
            {formatMoney(productQuery.data.price, locale)}
          </p>

          {/* The second read lands after the sheet is already painted, so what shows up here is
              announced instead of appearing in silence. */}
          <div aria-live="polite" className="mb-4">
            {availabilityFailed ? (
              <p className="small text-body-secondary mb-0">{t('detail.availabilityFailed')}</p>
            ) : (
              available !== undefined && (
                <p className="small text-body-secondary d-flex align-items-center gap-2 mb-0">
                  <span
                    className={`status-dot status-dot--${available ? 'in' : 'out'}`}
                    aria-hidden="true"
                  />
                  {available ? t('availability.available') : t('availability.unavailable')}
                </p>
              )
            )}
          </div>

          {/* The business identifier of the product (ADR-0015). It is what the customer quotes when
              asking about it, which is why it is on the sheet and not on the card. */}
          <dl className="row mb-4">
            <dt className="col-4 col-sm-3 fw-normal text-body-secondary">{t('detail.sku')}</dt>
            <dd className="col-8 col-sm-9 mb-0">{productQuery.data.sku}</dd>
          </dl>

          <h2 className="h5">{t('detail.description')}</h2>
          {productQuery.data.description === null ? (
            <p className="text-body-secondary mb-0">{t('detail.noDescription')}</p>
          ) : (
            <p className="product-description mb-0">{productQuery.data.description}</p>
          )}
        </Col>
      </Row>
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
        {product !== undefined && <Breadcrumb.Item active>{product.name}</Breadcrumb.Item>}
      </Breadcrumb>

      {renderBody()}
    </Container>
  );
}
