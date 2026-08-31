import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Breadcrumb, Container } from 'react-bootstrap';
import { Link, useParams } from 'react-router-dom';

import { useFavoriteStoreAvailability, useOnlineAvailability } from '../../shared/api/availability';
import { ProblemDetailError, toProblem } from '../../shared/api/http';
import { ErrorState } from '../../shared/ui/ErrorState';
import { ResourceBoundary } from '../../shared/ui/ResourceBoundary';
import { useProduct } from './api/queries';
import { ProductSheet } from './components/ProductSheet';
import { RelatedProducts } from './components/RelatedProducts';
import { CATALOG_PATH } from '../../shared/routes';

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
 * The detail screen of one product: it resolves what to show and which of the four states the reader
 * gets; what the sheet itself looks like is `ProductSheet`'s business.
 *
 * Two reads, fired in parallel and composed on screen, because `catalog` cannot depend on `inventory`
 * (ADR-0017 §3): the product itself, and its availability in the online store. They fail
 * independently on purpose — a stock service that is down leaves a note under the price, not a screen
 * the reader cannot get past.
 */
export default function ProductDetailPage() {
  const { t } = useTranslation('catalog');
  const { id } = useParams();
  const productId = toProductId(id);

  const productQuery = useProduct(productId);
  // The availability endpoint takes a set; here the set is one product, and it comes back with an
  // entry for it whether or not there is a stock row (RF-01).
  const productIds = useMemo(() => (productId === undefined ? [] : [productId]), [productId]);
  const availability = useOnlineAvailability(productIds);
  // The second store of RF-01, and it answers nothing at all for a visitor with no session or no
  // favourite — which is most of them. It never delays or replaces the online answer.
  const favoriteAvailability = useFavoriteStoreAvailability(productIds);

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
    // A discontinued product (ADR-0013) is a 404 with its own message, never the generic "something
    // failed on our side": the reader has to be able to tell "this is gone" from "try again". It is
    // the one case `ResourceBoundary` cannot serve, because it is not a state of the read — it is a
    // dead end, and a dead end needs a way out of its own. An id that is not a positive integer
    // never existed, so it lands here too instead of being sent to the API to be rejected there.
    if (productId === undefined) {
      return deadEndWithExit(NOT_FOUND);
    }
    if (productQuery.isError) {
      const problem = toProblem(productQuery.error);
      if (problem.code === 'RESOURCE_NOT_FOUND') {
        return deadEndWithExit(problem);
      }
    }

    // Everything else is the ordinary chain of a screen that reads one resource, and it is written
    // once in the base component instead of again here — which is what it was doing.
    return (
      <ResourceBoundary query={productQuery} data={productQuery.data}>
        {(product) => (
          <>
            <ProductSheet
              product={product}
              availability={availability}
              favoriteAvailability={favoriteAvailability}
            />
            <RelatedProducts typeId={product.typeId} excludeId={product.id} />
          </>
        )}
      </ResourceBoundary>
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
