import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useSearchParams } from 'react-router-dom';
import { Container } from 'react-bootstrap';

import { NOT_FOUND, toProblem } from '../../shared/api/http';
import { BACKOFFICE_STOCK_PATH } from '../../shared/routes';
import { ErrorState } from '../../shared/ui/ErrorState';
import { Icon } from '../../shared/ui/Icon';
import { useProductBySku } from './api/queries';
import { EmploymentBoundary } from './components/EmploymentBoundary';
import { NewStockLineForm } from './components/NewStockLineForm';
import { SkuFinder } from './components/SkuFinder';
import { readSku } from './stockSchema';

/**
 * Adding a line of stock (RF-09) at `/backoffice/stock/new`, in two steps on one screen: resolve the
 * SKU, then say how many units. A line is created **by SKU** and never by internal identifier
 * (ADR-0015), which is why resolving comes first and is not a detail of the form.
 *
 * `?sku=` prefills the field and resolves on arrival — it is what every row of the "not stocked" tab
 * links to, so a SKU that is already on screen is not retyped. What is typed here afterwards does
 * **not** go back into the URL: that would be one history entry per keystroke, and there is nothing
 * to link to halfway through a form.
 */
export default function NewStockLinePage() {
  const { t } = useTranslation('backoffice');
  const [searchParams] = useSearchParams();

  // Seeded from the URL and owned by the screen from then on. It is the SKU being resolved, not the
  // one being typed: the field keeps its own draft until it is submitted.
  const [sku, setSku] = useState(() => readSku(searchParams.get('sku')));

  const productQuery = useProductBySku(sku === '' ? undefined : sku);

  /**
   * Everything this screen reads off the query, read **here** and not down in the JSX.
   *
   * It is not a style choice. TanStack Query watches which fields of the result a render touches and
   * only re-renders for the ones it saw (tracked properties). The body below is a render prop that
   * `EmploymentBoundary` does not call while the employment is still loading — so reading `data`
   * only in there means the very first render touches `error` and nothing else, and the product
   * arriving is a change to a field nobody asked to hear about. The screen then waits forever with
   * the answer already in the cache.
   */
  const product = productQuery.data;
  const isSearching = productQuery.isFetching;
  const problem = productQuery.error === null ? undefined : toProblem(productQuery.error);
  const notFound = problem?.status === NOT_FOUND;

  return (
    <Container className="py-5">
      <EmploymentBoundary>
        {(employment) => (
          <div className="col-lg-8 col-xl-6">
            <Link to={BACKOFFICE_STOCK_PATH} className="d-inline-flex align-items-center gap-1 mb-3">
              <Icon name="arrow_back" size="sm" />
              {t('newLine.back')}
            </Link>

            <h1 className="h2 mb-2">{t('newLine.title')}</h1>
            <p className="text-body-secondary mb-4">
              {t('newLine.lead', { store: employment.storeName })}
            </p>

            {product === undefined ? (
              <SkuFinder
                defaultSku={sku}
                isSearching={isSearching}
                notFound={notFound}
                onSearch={setSku}
              />
            ) : (
              <NewStockLineForm
                employment={employment}
                product={product}
                onChangeSku={() => setSku('')}
              />
            )}

            {/* A SKU that resolves to nothing is the field's business, and `SkuFinder` says so there.
                Anything else is a failed read, and that is this screen's to report. */}
            {problem !== undefined && !notFound && <ErrorState error={productQuery.error} />}
          </div>
        )}
      </EmploymentBoundary>
    </Container>
  );
}
